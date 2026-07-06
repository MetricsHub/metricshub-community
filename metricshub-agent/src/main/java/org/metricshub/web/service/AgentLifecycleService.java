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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.RestartStatus;
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
 * discarded and its pre-built {@link AgentContext} — if any — is closed to avoid leaking
 * scheduler threads / gRPC channels).
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
	 * {@link RestartStatus} published for the run serving this request, so a client that fired
	 * the request can correlate the polled status with its own request instead of mistaking a
	 * previous restart's terminal status for its own (see {@link RestartStatus#getRequestId()}).
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
			log.info("Coalesced a pending restart with a newer request; dropping the previous pending one.");
			tryReleasePreBuiltContext(toDiscard.supplier());
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
			// If we built a reloaded context but the restart failed, release its heavy
			// state so we don't leak it.
			if (reloadedContext != null) {
				try {
					reloadedContext.close();
				} catch (Exception closeException) {
					log.debug("Failed to close the partially-initialized reloaded context.", closeException);
				}
			}
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
	 * Best-effort disposal of a discarded pending supplier's context. If the supplier was a
	 * simple {@code () -> preBuiltContext} closure (as produced by the DirectoryWatcher path),
	 * invoking it returns the already-built context which we then close to release its
	 * scheduler threads and gRPC channel. For a lazy supplier that would build a fresh
	 * context on invocation, this still safely releases whatever gets built. Any exception is
	 * caught and logged at debug level.
	 */
	private void tryReleasePreBuiltContext(final Supplier<AgentContext> supplier) {
		try {
			final AgentContext orphan = supplier.get();
			if (orphan != null) {
				orphan.close();
			}
		} catch (Exception e) {
			log.debug("Failed to release the discarded pending AgentContext: {}", e.getMessage());
		}
	}

	/**
	 * Shuts down the background restart executor when the service is destroyed.
	 */
	@PreDestroy
	void shutdown() {
		restartExecutor.shutdownNow();
	}
}
