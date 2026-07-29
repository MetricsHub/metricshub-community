package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.AgentContextReaderTracker;
import org.metricshub.web.dto.RestartStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing the lifecycle of the MetricsHub Agent.
 * <p>
 * Both restart triggers (UI-driven {@code POST /api/agent/restart} and file-watcher-driven
 * global configuration change) funnel through this service, so they share the same
 * background executor, status tracking and old-context disposal path.
 * </p>
 * <p>
 * Restarts are serialized with a <b>queue-and-run-after</b> policy: at most one restart runs
 * at a time and at most one further request is held pending. When a new request arrives while
 * a restart is already running, it is queued; if another one arrives while a request is
 * already queued, the queued one is <em>coalesced</em> (the newest wins, the older is
 * discarded without ever invoking its supplier — suppliers are lazy and only build their
 * {@link AgentContext} when the restart actually runs).
 * </p>
 * <p>
 * At the end of every successful restart the previous {@link AgentContext} is
 * {@link AgentContext#close() closed}, stopping its scheduler and OpenTelemetry Collector
 * process; the context itself is reclaimed by the garbage collector once no thread
 * references it anymore.
 * </p>
 */
@Service
@Slf4j
public class AgentLifecycleService {

	/**
	 * Outcome of a {@link AgentLifecycleService#restartAsync(Supplier)} call.
	 */
	public enum RestartRequestResult {
		/** The restart was submitted directly and will run immediately on the background thread. */
		SCHEDULED,
		/** A restart is already running; the new request has been queued to run after it. */
		QUEUED,
		/**
		 * A restart is already running and another one was already queued; the previous queued
		 * request has been discarded and replaced with this newer one (the freshest wins).
		 */
		COALESCED
	}

	/**
	 * Acknowledgement returned by {@link #restartAsync(Supplier)}: the enqueue outcome plus the
	 * identifier assigned to this restart request. The identifier is stamped into every
	 * {@link RestartStatus} published for the run serving this request (its {@code requestId}
	 * field), so a client that fired the request can correlate the polled status with its own
	 * request instead of mistaking a previous restart's terminal status for its own.
	 *
	 * @param result    the enqueue outcome
	 * @param requestId the monotonically increasing identifier assigned to this request
	 */
	public record RestartRequestAck(RestartRequestResult result, long requestId) {}

	/**
	 * A queued restart request: the identifier assigned when it was accepted plus the supplier
	 * that produces its reloaded context.
	 */
	private record PendingRestart(long requestId, Supplier<AgentContext> supplier) {}

	/**
	 * The single source of truth for the currently active {@link AgentContext}.
	 */
	private final AgentContextHolder agentContextHolder;

	/**
	 * Guard for {@link #restartInProgress} and {@link #pending} — protects the "am I busy? do
	 * I have a queued request?" state so scheduling, coalescing, and draining are race-free.
	 */
	private final Object queueLock = new Object();

	/**
	 * {@code true} while a restart is currently being executed on {@link #restartExecutor}.
	 * Guarded by {@link #queueLock}.
	 */
	private boolean restartInProgress;

	/**
	 * The single queued restart, if any. Guarded by {@link #queueLock}.
	 */
	private PendingRestart pending;

	/**
	 * Source of restart request identifiers. Incremented for every {@link #restartAsync(Supplier)}
	 * call, including coalesced ones.
	 */
	private final AtomicLong requestIdSequence = new AtomicLong();

	/**
	 * Snapshot of the current or last-known restart status, exposed via
	 * {@link #getRestartStatus()} so REST clients can poll it after firing an
	 * asynchronous restart. Kept as an {@link AtomicReference} so status reads never block
	 * on {@link #queueLock}.
	 */
	private final AtomicReference<RestartStatus> restartStatus = new AtomicReference<>(
		RestartStatus.builder().state(RestartStatus.State.IDLE).message("No restart requested yet.").build()
	);

	/**
	 * Dedicated single-thread executor used to run restart requests off the HTTP request
	 * thread (and off the DirectoryWatcher thread).
	 */
	private final ExecutorService restartExecutor = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "metricshub-agent-restart");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * Hooks invoked (in registration order) at the very beginning of every restart, on the
	 * background restart thread. Typically used by external modules (e.g. license enforcement)
	 * to pause their own schedulers before the {@link AgentContext} is torn down.
	 * <p>
	 * Exceptions thrown by a hook are caught and logged; they do not abort the restart.
	 * </p>
	 */
	private final List<Runnable> preRestartHooks = new CopyOnWriteArrayList<>();

	/**
	 * Hooks invoked (in registration order) at the end of every successful restart, on the
	 * background restart thread, after the {@link AgentContextHolder} has been updated and
	 * the previous context has been closed. Each hook receives the new active
	 * {@link AgentContext}.
	 * <p>
	 * Exceptions thrown by a hook are caught and logged; they do not fail the restart.
	 * </p>
	 */
	private final List<Consumer<AgentContext>> postRestartHooks = new CopyOnWriteArrayList<>();

	/**
	 * Predicate consulted during {@link #restart(AgentContext, AgentContext)} to decide
	 * whether the new context's OpenTelemetry Collector process should actually be launched.
	 * <p>
	 * Defaults to {@code true} (always launch), which matches the community-edition behavior.
	 * The enterprise agent replaces this with a check on the license state (see
	 * {@code startOpenTelemetryCollector} in {@code MetricsHubEnterpriseAgent}) so the
	 * collector is not launched when the license is invalid.
	 * </p>
	 * <p>
	 * When the predicate returns {@code false}, {@link #restart(AgentContext, AgentContext)}
	 * logs a warning and skips the launch — the reloaded context still becomes active in the
	 * {@link AgentContextHolder}, but its collector process stays offline until the next
	 * successful restart. Post-restart hooks still run and are given the new context; they
	 * can query the predicate themselves to synchronize their own state (for instance,
	 * clearing a "collector started" flag).
	 * </p>
	 */
	private volatile Predicate<AgentContext> collectorLaunchPredicate = _ -> true;

	/**
	 * Default grace delay, in milliseconds, between the moment an {@link ExtensionManager} is
	 * orphaned by an extension reload and the moment its isolated class loaders are closed.
	 * Sized to comfortably exceed the bounded durations of in-flight work that may still hold the
	 * replaced context (HTTP/MCP requests, collect jobs), so lazy class loading never fails
	 * mid-operation. Note that classes already loaded remain usable after the close; only
	 * not-yet-loaded classes/resources would be affected.
	 */
	private static final long DEFAULT_ORPHANED_LOADER_CLOSE_GRACE_MS = 5 * 60 * 1000L;

	/**
	 * Grace delay applied before closing an orphaned {@link ExtensionManager}. Package-private
	 * mutable so tests can shorten it.
	 */
	private volatile long orphanedLoaderCloseGraceMs = DEFAULT_ORPHANED_LOADER_CLOSE_GRACE_MS;

	/**
	 * Interval between quiescence rechecks once the grace delay has elapsed but the replaced
	 * context's scheduler still has tasks running (collection jobs have a user-configurable,
	 * unbounded timeout). Package-private mutable so tests can shorten it.
	 */
	private volatile long retirementRecheckMs = 30_000L;

	/**
	 * Hard cap on how long retirement waits for the replaced context's tasks to terminate after
	 * the grace delay. A task that ignores interrupts beyond this point would otherwise pin the
	 * loaders forever; the close proceeds with a warning (classes already loaded remain usable).
	 */
	private static final long RETIREMENT_MAX_WAIT_MS = 30 * 60 * 1000L;

	/**
	 * Dedicated scheduler that retires orphaned extension class loaders after the grace delay.
	 * Each orphaned manager gets its own full grace window, independent of how quickly further
	 * restarts follow (back-to-back restarts must not shorten an older generation's window).
	 */
	private final ScheduledExecutorService loaderRetirementExecutor = Executors.newSingleThreadScheduledExecutor(
		runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-extension-loader-retirement");
			thread.setDaemon(true);
			return thread;
		}
	);

	/**
	 * The orphaned managers whose close is scheduled but has not run yet. Tracked explicitly so
	 * {@link #shutdown()} can close them immediately at process exit (tasks drained from a
	 * scheduled executor's {@code shutdownNow()} refuse to run once the executor is stopped).
	 */
	private final Queue<ExtensionManager> pendingLoaderRetirements = new ConcurrentLinkedQueue<>();

	/**
	 * Tracks request-side readers of the active context (HTTP/MCP operations run on request
	 * threads, not on the context's task scheduler, and may invoke extension providers
	 * synchronously). Optional: injected by Spring when the web layer is active; {@code null} in
	 * plain unit-test or non-web setups, where only the scheduler quiescence check applies.
	 */
	private volatile AgentContextReaderTracker readerTracker;

	/**
	 * Injects the request-side reader tracker when the web layer is active.
	 *
	 * @param readerTracker the tracker bean
	 */
	@Autowired(required = false)
	void setReaderTracker(final AgentContextReaderTracker readerTracker) {
		this.readerTracker = readerTracker;
	}

	/**
	 * Constructor.
	 *
	 * @param agentContextHolder the shared {@link AgentContextHolder} injected by Spring
	 */
	public AgentLifecycleService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Registers a hook to be invoked at the beginning of every restart (before the old
	 * services are stopped). Hooks run on the background restart thread and any exception
	 * they throw is logged and swallowed so it does not abort the restart.
	 *
	 * @param hook the hook to add
	 */
	public void addPreRestartHook(final Runnable hook) {
		preRestartHooks.add(hook);
	}

	/**
	 * Registers a hook to be invoked at the end of every successful restart (after the
	 * {@link AgentContextHolder} has been swapped and the previous context has been closed).
	 * Hooks run on the background restart thread and any exception they throw is logged and
	 * swallowed.
	 *
	 * @param hook the hook to add; receives the new active {@link AgentContext}
	 */
	public void addPostRestartHook(final Consumer<AgentContext> hook) {
		postRestartHooks.add(hook);
	}

	/**
	 * Replaces the predicate that decides whether the OpenTelemetry Collector process should
	 * be launched during a restart. The default predicate always returns {@code true};
	 * downstream editions can install a stricter check (typically a license validity check).
	 *
	 * @param predicate the predicate to install; must not be {@code null}
	 * @throws NullPointerException if {@code predicate} is {@code null}
	 */
	public void setCollectorLaunchPredicate(final Predicate<AgentContext> predicate) {
		if (predicate == null) {
			throw new NullPointerException("predicate must not be null");
		}
		this.collectorLaunchPredicate = predicate;
	}

	/**
	 * @return the currently installed collector-launch predicate. Useful for post-restart
	 *         hooks that need to know whether the collector was actually launched.
	 */
	public Predicate<AgentContext> getCollectorLaunchPredicate() {
		return collectorLaunchPredicate;
	}

	/**
	 * Restarts the MetricsHub Agent synchronously.
	 * <p>
	 * Stops all services in the running context, starts them in the new context,
	 * publishes the new context to the {@link AgentContextHolder}, and finally closes
	 * the previous context to release its heavy internal state.
	 * </p>
	 * <p>
	 * Intended for direct/programmatic use; callers should generally prefer
	 * {@link #restartAsync(Supplier)} to avoid blocking their own thread.
	 * </p>
	 *
	 * @param runningContext  the current running agent context
	 * @param reloadedContext the new agent context to switch to
	 */
	public void restart(final AgentContext runningContext, final AgentContext reloadedContext) {
		log.info("Restart requested. Restarting MetricsHub Agent...");

		// Generation of the context being replaced: request-side readers that entered at or before
		// it must complete before its extension class loaders can be retired.
		final long outgoingGeneration = agentContextHolder.getGeneration();

		// Fire pre-restart hooks (e.g. license guard stop). Any failure is logged but does
		// not abort the restart.
		runHooks(preRestartHooks, "pre-restart");

		// Stop the current scheduler
		runningContext.getTaskSchedulingService().stop();

		// Stop the current OpenTelemetry Collector
		log.info("Restarting OpenTelemetry Collector...");
		runningContext.getOtelCollectorProcessService().stop();

		// Launch the new OpenTelemetry Collector from the reloaded context — but only if the
		// currently installed predicate allows it (enterprise editions gate this on license
		// validity). When the predicate refuses, the reloaded context still becomes active
		// (its scheduler starts, its telemetry managers are used) but the collector process
		// stays offline until the next successful restart.
		if (collectorLaunchPredicate.test(reloadedContext)) {
			reloadedContext.getOtelCollectorProcessService().launch();
		} else {
			log.warn(
				"OpenTelemetry Collector launch skipped by the configured predicate. " +
					"The reloaded MetricsHub Agent context is active but its collector process is offline."
			);
		}

		// Start the new scheduler
		reloadedContext.getTaskSchedulingService().start();

		// Publish the new context so every service reads it on the next call
		agentContextHolder.setAgentContext(reloadedContext);

		// Stop the previous context's services (idempotent). Its fields stay intact so any
		// thread that grabbed the old context just before the swap sees a stopped-but-valid
		// object; the whole graph becomes GC-eligible once those readers finish.
		runningContext.close();

		// Release the isolated extension class loaders orphaned by an extension reload, once the
		// replaced context's tasks have terminated and its request-side readers have completed
		// (plus a grace delay).
		releaseOrphanedExtensionLoaders(runningContext, reloadedContext.getExtensionManager(), outgoingGeneration);

		// Fire post-restart hooks (e.g. license guard restart with the new context).
		for (final Consumer<AgentContext> hook : postRestartHooks) {
			try {
				hook.accept(reloadedContext);
			} catch (Exception e) {
				log.warn("post-restart hook failed: {}", e.getMessage());
				log.debug("post-restart hook error", e);
			}
		}

		log.info(
			"MetricsHub Agent restarted successfully. Active context generation: {}",
			agentContextHolder.getGeneration()
		);
	}

	/**
	 * Schedules the close of the extension class loaders orphaned by this restart: after a grace
	 * delay, the retirement task waits for the replaced context's scheduler to actually terminate
	 * (collection jobs carry a user-configurable, unbounded timeout, so a fixed delay alone is not a
	 * bound), rechecking every {@link #retirementRecheckMs} up to {@link #RETIREMENT_MAX_WAIT_MS}.
	 * <p>
	 * A configuration-file reload reuses the {@link ExtensionManager} ({@code outgoing == incoming}),
	 * so it orphans nothing; only an explicit extension reload (the {@code /restart} endpoint) swaps
	 * in a different manager and thus orphans the outgoing one. Each orphaned manager gets its own
	 * grace window and quiescence wait on the retirement scheduler, so back-to-back restarts cannot
	 * shorten an older generation's window.
	 * </p>
	 *
	 * @param outgoingContext    the context being replaced (its scheduler is one quiescence signal)
	 * @param incoming           the extension manager of the newly active context
	 * @param outgoingGeneration the holder generation of the replaced context (request-side readers
	 *                           that entered at or before it are the other quiescence signal)
	 */
	private void releaseOrphanedExtensionLoaders(
		final AgentContext outgoingContext,
		final ExtensionManager incoming,
		final long outgoingGeneration
	) {
		final ExtensionManager outgoing = outgoingContext.getExtensionManager();
		if (outgoing == null || outgoing == incoming) {
			// A reused manager (configuration reload) orphans nothing.
			return;
		}
		log.debug(
			"Scheduling the close of the extension class loaders orphaned by an extension reload in {} ms.",
			orphanedLoaderCloseGraceMs
		);
		pendingLoaderRetirements.add(outgoing);
		final long deadlineMs = System.currentTimeMillis() + orphanedLoaderCloseGraceMs + RETIREMENT_MAX_WAIT_MS;
		loaderRetirementExecutor.schedule(
			() -> retireWhenQuiescent(outgoingContext, outgoing, outgoingGeneration, deadlineMs),
			orphanedLoaderCloseGraceMs,
			TimeUnit.MILLISECONDS
		);
	}

	/**
	 * Retires an orphaned manager once the replaced context is quiescent: closes it when the old
	 * scheduler has terminated, reschedules a recheck while its tasks are still running, and forces
	 * the close with a warning past the hard deadline (a task ignoring interrupts must not pin the
	 * loaders forever; classes already loaded remain usable after the close).
	 *
	 * @param outgoingContext the replaced context whose scheduler signals quiescence
	 * @param orphaned        the manager to retire
	 * @param deadlineMs      epoch milliseconds after which the close proceeds regardless
	 */
	private void retireWhenQuiescent(
		final AgentContext outgoingContext,
		final ExtensionManager orphaned,
		final long outgoingGeneration,
		final long deadlineMs
	) {
		if (!pendingLoaderRetirements.contains(orphaned)) {
			// Already flushed by shutdown().
			return;
		}
		final boolean quiescent = isSchedulerTerminated(outgoingContext) && !hasActiveReaders(outgoingGeneration);
		if (!quiescent && System.currentTimeMillis() < deadlineMs) {
			loaderRetirementExecutor.schedule(
				() -> retireWhenQuiescent(outgoingContext, orphaned, outgoingGeneration, deadlineMs),
				retirementRecheckMs,
				TimeUnit.MILLISECONDS
			);
			return;
		}
		if (!quiescent) {
			log.warn(
				"Closing the extension class loaders of a replaced context although some of its tasks or readers are still active."
			);
		}
		retireOrphanedManager(orphaned);
	}

	/**
	 * Tests whether request-side readers that entered at or before the given generation are still
	 * active. Without a tracker (non-web setups), there are no request-side readers to wait for.
	 *
	 * @param outgoingGeneration the generation of the replaced context
	 * @return {@code true} while at least one such reader has not completed
	 */
	private boolean hasActiveReaders(final long outgoingGeneration) {
		final AgentContextReaderTracker tracker = readerTracker;
		return tracker != null && tracker.hasReadersAtOrBefore(outgoingGeneration);
	}

	/**
	 * Tests whether the given context's task scheduler has fully terminated (no collection task is
	 * still running). A context without an initialized scheduler is considered terminated.
	 *
	 * @param context the replaced context
	 * @return {@code true} when no task of the context can still be executing
	 */
	private static boolean isSchedulerTerminated(final AgentContext context) {
		try {
			final var taskSchedulingService = context.getTaskSchedulingService();
			if (taskSchedulingService == null) {
				return true;
			}
			final var taskScheduler = taskSchedulingService.getTaskScheduler();
			if (taskScheduler == null) {
				return true;
			}
			return taskScheduler.getScheduledThreadPoolExecutor().isTerminated();
		} catch (Exception e) {
			// Scheduler never initialized (or already torn down): nothing can be running.
			return true;
		}
	}

	/**
	 * Closes an orphaned manager's extension class loaders if it is still pending (i.e. not already
	 * closed by {@link #shutdown()}).
	 *
	 * @param orphaned the manager to retire
	 */
	private void retireOrphanedManager(final ExtensionManager orphaned) {
		if (!pendingLoaderRetirements.remove(orphaned)) {
			return;
		}
		try {
			orphaned.close();
			log.debug("Closed the extension class loaders orphaned by an extension reload.");
		} catch (Exception e) {
			log.debug("Failed to close an orphaned ExtensionManager: {}", e.getMessage());
		}
	}

	/**
	 * Shortens the orphaned-loader close grace delay. Test-only.
	 *
	 * @param graceMs the new grace delay in milliseconds
	 */
	void setOrphanedLoaderCloseGraceMs(final long graceMs) {
		orphanedLoaderCloseGraceMs = graceMs;
	}

	/**
	 * Shortens the quiescence recheck interval. Test-only.
	 *
	 * @param recheckMs the new recheck interval in milliseconds
	 */
	void setRetirementRecheckMs(final long recheckMs) {
		retirementRecheckMs = recheckMs;
	}

	/**
	 * Runs every hook in the provided list, catching any exception and logging it so the
	 * restart flow continues.
	 */
	private static void runHooks(final List<Runnable> hooks, final String label) {
		for (final Runnable hook : hooks) {
			try {
				hook.run();
			} catch (Exception e) {
				log.warn("{} hook failed: {}", label, e.getMessage());
				log.debug("{} hook error", label, e);
			}
		}
	}

	/**
	 * Schedules an asynchronous restart of the MetricsHub Agent.
	 * <p>
	 * If nothing is running, the restart is submitted immediately and this method returns a
	 * {@link RestartRequestResult#SCHEDULED} acknowledgement. Otherwise it is queued behind
	 * the currently running restart ({@link RestartRequestResult#QUEUED}); if another one was
	 * already queued, the older queued request is discarded (and its pre-built context, if
	 * any, is closed) and this newer one replaces it
	 * ({@link RestartRequestResult#COALESCED}).
	 * </p>
	 * <p>
	 * Every request — including coalesced ones — is assigned a monotonically increasing
	 * identifier, returned in the {@link RestartRequestAck} and stamped into every
	 * {@link RestartStatus} published for the run serving that request. Clients polling
	 * {@link #getRestartStatus()} should only treat a terminal state as the outcome of their
	 * own request when {@code status.requestId >= ack.requestId}; a smaller id belongs to an
	 * earlier restart that finished before the queued one started.
	 * </p>
	 * <p>
	 * The reloaded {@link AgentContext} is produced lazily via the supplied factory so that
	 * heavy work (extension loading, configuration parsing, hostname resolution, connector
	 * store rebuild, ...) is done off the caller's thread. The running context is read from
	 * the {@link AgentContextHolder} at the moment the restart actually runs, so the freshest
	 * running context is always used as the "old" one.
	 * </p>
	 *
	 * @param reloadedContextSupplier a supplier that produces the new agent context
	 *                                (invoked on the background thread when the request runs)
	 * @return the outcome of the enqueue attempt together with the request identifier that
	 *         will be stamped into the {@link RestartStatus} of the run serving this request
	 */
	public RestartRequestAck restartAsync(final Supplier<AgentContext> reloadedContextSupplier) {
		PendingRestart toDiscard = null;
		boolean submitNow = false;
		final long requestId = requestIdSequence.incrementAndGet();
		final RestartRequestResult result;
		synchronized (queueLock) {
			if (!restartInProgress) {
				restartInProgress = true;
				submitNow = true;
				result = RestartRequestResult.SCHEDULED;
			} else if (pending == null) {
				pending = new PendingRestart(requestId, reloadedContextSupplier);
				result = RestartRequestResult.QUEUED;
			} else {
				toDiscard = pending;
				pending = new PendingRestart(requestId, reloadedContextSupplier);
				result = RestartRequestResult.COALESCED;
			}
		}

		if (toDiscard != null) {
			// The discarded supplier is intentionally NOT invoked: suppliers are lazy (they reload
			// the extensions and build a full AgentContext when the restart actually runs), so
			// invoking one here — on the coalescing caller's thread, potentially an HTTP request —
			// would build an expensive context only to throw it away.
			log.info("Coalesced a pending restart with a newer request; dropping the previous pending one.");
		} else if (!submitNow) {
			log.info("A MetricsHub Agent restart is running; queued the new request to run after it.");
		}

		if (submitNow) {
			submitRestart(reloadedContextSupplier, requestId);
		}
		return new RestartRequestAck(result, requestId);
	}

	/**
	 * Returns a snapshot of the current or last-known restart status.
	 *
	 * @return the current {@link RestartStatus}
	 */
	public RestartStatus getRestartStatus() {
		return restartStatus.get();
	}

	/**
	 * Submits a fresh restart run to the background executor and publishes the
	 * {@code IN_PROGRESS} status. Must only be called while {@link #restartInProgress} is
	 * already set to {@code true} (either by {@link #restartAsync(Supplier)} or by
	 * {@link #runRestart(Supplier, Instant)} handing off to the queued request).
	 */
	private void submitRestart(final Supplier<AgentContext> reloadedContextSupplier, final long requestId) {
		final Instant startedAt = Instant.now();
		publishStatus(RestartStatus.State.IN_PROGRESS, "Restart in progress...", startedAt, null, requestId);
		restartExecutor.submit(() -> runRestart(reloadedContextSupplier, startedAt, requestId));
	}

	/**
	 * Publishes a new {@link RestartStatus} snapshot, stamping the current context generation.
	 *
	 * @param state     the restart lifecycle state
	 * @param message   human-readable message associated with the state
	 * @param startedAt when the restart was started
	 * @param endedAt   when the restart ended, or {@code null} while it is still running
	 * @param requestId identifier of the restart request this status refers to
	 */
	private void publishStatus(
		final RestartStatus.State state,
		final String message,
		final Instant startedAt,
		final Instant endedAt,
		final long requestId
	) {
		restartStatus.set(
			RestartStatus.builder()
				.state(state)
				.message(message)
				.startedAt(startedAt)
				.endedAt(endedAt)
				.contextGeneration(agentContextHolder.getGeneration())
				.requestId(requestId)
				.build()
		);
	}

	/**
	 * Executes the restart on the background thread, updates the exposed status and — under
	 * the {@link #queueLock} — either releases the "busy" flag or drains and re-submits the
	 * queued request.
	 */
	private void runRestart(
		final Supplier<AgentContext> reloadedContextSupplier,
		final Instant startedAt,
		final long requestId
	) {
		AgentContext reloadedContext = null;
		try {
			final AgentContext runningContext = agentContextHolder.getAgentContext();
			reloadedContext = reloadedContextSupplier.get();
			restart(runningContext, reloadedContext);
			publishStatus(
				RestartStatus.State.SUCCEEDED,
				"MetricsHub Agent restarted successfully.",
				startedAt,
				Instant.now(),
				requestId
			);
		} catch (Exception e) {
			log.error("Failed to restart MetricsHub Agent.", e);
			// If we built a reloaded context but the restart failed, release its heavy state (and,
			// when it never became active, its freshly reloaded extension loaders) so we don't leak.
			releaseDiscardedContext(reloadedContext);
			publishStatus(
				RestartStatus.State.FAILED,
				"Failed to restart MetricsHub Agent: " + e.getMessage(),
				startedAt,
				Instant.now(),
				requestId
			);
		} finally {
			drainOrRelease();
		}
	}

	/**
	 * Under {@link #queueLock}, either hands off to the queued request (keeping the busy flag
	 * set) or clears the busy flag if nothing is queued.
	 */
	private void drainOrRelease() {
		final PendingRestart next;
		synchronized (queueLock) {
			next = pending;
			pending = null;
			if (next == null) {
				restartInProgress = false;
			}
			// If next != null, keep restartInProgress = true and hand off below.
		}
		if (next != null) {
			log.info("Running queued restart request now that the previous one has completed.");
			submitRestart(next.supplier(), next.requestId());
		}
	}

	/**
	 * Closes a discarded context and, when it carries its own freshly reloaded
	 * {@link ExtensionManager} that never became the active one, closes that manager's isolated
	 * extension class loaders too. Because the context was never published, nothing can be using it,
	 * so the loaders are released immediately. A context that reused the active manager (a
	 * configuration reload) leaves the manager untouched. Best-effort: exceptions are logged.
	 *
	 * @param context the discarded context; may be {@code null}.
	 */
	private void releaseDiscardedContext(final AgentContext context) {
		if (context == null) {
			return;
		}
		try {
			context.close();
		} catch (Exception e) {
			log.debug("Failed to close a discarded AgentContext: {}", e.getMessage());
		}
		final ExtensionManager manager = context.getExtensionManager();
		final AgentContext active = agentContextHolder.getAgentContext();
		final ExtensionManager activeManager = active == null ? null : active.getExtensionManager();
		if (manager != null && manager != activeManager) {
			manager.close();
		}
	}

	/**
	 * Shuts down the background restart executor and the loader-retirement scheduler when the
	 * service is destroyed. Pending retirement tasks are executed immediately: the process is
	 * exiting, so the grace delay no longer matters and closing releases the jar handles now.
	 */
	@PreDestroy
	void shutdown() {
		restartExecutor.shutdownNow();
		loaderRetirementExecutor.shutdownNow();
		ExtensionManager pending;
		while ((pending = pendingLoaderRetirements.poll()) != null) {
			try {
				pending.close();
			} catch (Exception e) {
				log.debug("Failed to close a pending orphaned ExtensionManager: {}", e.getMessage());
			}
		}
	}
}
