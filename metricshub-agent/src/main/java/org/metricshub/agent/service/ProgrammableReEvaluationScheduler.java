package org.metricshub.agent.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.ReloadService.ReloadResult;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IConfigurationProvider;
import org.metricshub.engine.extension.ScheduledReEvaluation;
import org.metricshub.web.AgentContextHolder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * Drives cron-based re-evaluation of programmable ({@code .vm}) configuration templates.
 * <p>
 * On {@link #start()} it walks every {@link IConfigurationProvider} of the current
 * {@link AgentContext}, reads the re-evaluations they declare (one per template that declared a
 * {@code $schedule.cron(...)}), and schedules a task per re-evaluation on the injected
 * {@link TaskScheduler}.
 * </p>
 * <p>
 * When a task fires, that template &mdash; and only that one &mdash; is rendered again. If the
 * fragment it produces actually changed, a reload is triggered through the injected
 * {@link ReloadTrigger}, which rebuilds the running configuration and applies the differences. The
 * rebuild runs inside {@link IConfigurationProvider#runReusingCachedFragments(Runnable)}, so every
 * other template is served from its cached fragment instead of having its data sources re-run.
 * </p>
 * <p>
 * Several templates can be due at the same time. Their renders run concurrently &mdash; a slow data
 * source in one template must not hold up another &mdash; but the compare-and-merge step that
 * follows is serialized by a single lock, so simultaneous firings are merged one at a time and none
 * clobbers another. One given template is additionally serialized with itself, so a cron firing and
 * an on-demand request for the same file are processed one after the other.
 * </p>
 * <p>
 * Discovery also runs periodically, so a template added while the agent runs starts firing without a
 * restart whichever reload path loaded it, and a template that was deleted has its schedule
 * cancelled instead of firing forever against a provider that no longer knows it.
 * </p>
 */
@Slf4j
public class ProgrammableReEvaluationScheduler {

	/**
	 * Callback that rebuilds the running configuration and applies the differences after a template
	 * re-evaluation changed its output. Supplied by the wiring layer.
	 */
	@FunctionalInterface
	public interface ReloadTrigger {
		/**
		 * Rebuilds the running configuration from the current templates and applies changes.
		 *
		 * @return what the reload concluded; {@link ReloadResult#GLOBAL_RESTART_REQUIRED} means a restart
		 *         was only requested, and runs later
		 */
		ReloadResult triggerReload();
	}

	/**
	 * What a re-evaluation ended up doing, so an on-demand caller can report it back to the user.
	 */
	public enum ReEvaluationOutcome {
		/** The unit produced no fragment; the last good configuration was kept. */
		NOTHING_PRODUCED,
		/** The unit produced the same fragment as before; no reload was needed. */
		UNCHANGED,
		/** The fragment changed and the configuration was reloaded. */
		RELOADED,
		/**
		 * The fragment changed and requires a restart of the agent, which was requested. The restart runs
		 * in the background and can still fail, so the change is not considered applied yet.
		 */
		RESTART_REQUESTED,
		/** The fragment changed but the reload failed; the change is retried on the next firing. */
		RELOAD_FAILED
	}

	/**
	 * How often the scheduler re-checks its providers for re-evaluations it does not know yet.
	 * <p>
	 * A newly added template is loaded by whichever reload path noticed the file (the configuration
	 * file watcher, a UI save, a re-evaluation-driven reload), and those paths do not all go through
	 * this scheduler. Sweeping periodically makes the pickup independent of which one ran. The sweep
	 * only compares already-computed ids and crons against {@link #scheduledCrons} and performs no I/O.
	 * </p>
	 */
	private static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);

	private final AgentContextHolder agentContextHolder;
	private final TaskScheduler taskScheduler;
	private final ReloadTrigger reloadTrigger;

	/**
	 * Serializes the merge step so simultaneous firings do not race on the reload. It is the lock
	 * every reload of the agent holds ({@link ConfigurationReloadService#RELOAD_LOCK}), not one of
	 * this scheduler's own: a cron-driven reload then also waits for a reload started by the file
	 * watcher or the reload endpoint, and the other way round.
	 */
	private final Object lock = ConfigurationReloadService.RELOAD_LOCK;

	/**
	 * One lock per re-evaluation id, so the same template is never re-evaluated twice at the same
	 * time. Two different templates hold two different locks and still run in parallel.
	 * <p>
	 * A cron firing and an on-demand request can target the same template at the same moment. Without
	 * this, both would render it, both would publish their result to the provider's cache, and the
	 * second one could record as the baseline a fragment that was never the one applied &mdash; a
	 * later re-evaluation producing that same fragment would then be skipped although the running
	 * configuration holds something else.
	 * </p>
	 */
	private final Map<String, Object> reEvaluationLocks = new ConcurrentHashMap<>();

	/** The scheduled cron tasks per re-evaluation id, so a single one can be cancelled. */
	private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

	/** The periodic discovery sweep, cancelled on {@link #stop()}. */
	private ScheduledFuture<?> sweepFuture;

	/** Last published fragment per re-evaluation id, so an unchanged re-evaluation skips the reload. */
	private final Map<String, JsonNode> lastFragments = new HashMap<>();

	/**
	 * Cron currently applied per re-evaluation id, so a rediscovery can tell an unchanged registration
	 * from one whose expression was edited. Wider than {@link #scheduledTasks}: a registration whose
	 * cron was rejected is remembered here but holds no task, so it is not retried until its
	 * expression changes.
	 */
	private final Map<String, String> scheduledCrons = new HashMap<>();

	/**
	 * Set by {@link #pause()} and {@link #stop()}, cleared by {@link #resume()}, always under
	 * {@link #lock}. A discovery pass or a re-evaluation can read the
	 * providers before it gets the lock, and then get it only after a restart stopped this scheduler.
	 * Checking this flag once it holds the lock stops it from scheduling tasks against the retired
	 * providers, or reloading from them: this instance is discarded after a restart, so nothing would
	 * ever cancel what it scheduled.
	 */
	private boolean stopped;

	/**
	 * Per re-evaluation id, the fragment whose change required a restart that was requested but is not
	 * known to be done. Guarded by {@link #lock}.
	 * <p>
	 * A restart runs later, on another thread, and can fail. So the baseline is not moved when one is
	 * requested: a failed restart must be retried by the next firing. This map stops those next
	 * firings from requesting the very same restart again while it is still running, which would
	 * restart the agent a second time for nothing.
	 * </p>
	 */
	private final Map<String, JsonNode> restartRequestedFragments = new HashMap<>();

	/** Tells whether a restart of the agent is still running or queued. */
	private final BooleanSupplier restartPending;

	/**
	 * Creates the scheduler, for a setup where no restart is ever pending.
	 *
	 * @param agentContextHolder holder of the active {@link AgentContext} (source of the providers)
	 * @param taskScheduler      the scheduler used to run cron tasks
	 * @param reloadTrigger      callback that rebuilds and applies the configuration after a change
	 */
	public ProgrammableReEvaluationScheduler(
		final AgentContextHolder agentContextHolder,
		final TaskScheduler taskScheduler,
		final ReloadTrigger reloadTrigger
	) {
		this(agentContextHolder, taskScheduler, reloadTrigger, () -> false);
	}

	/**
	 * Creates the scheduler.
	 *
	 * @param agentContextHolder holder of the active {@link AgentContext} (source of the providers)
	 * @param taskScheduler      the scheduler used to run cron tasks
	 * @param reloadTrigger      callback that rebuilds and applies the configuration after a change
	 * @param restartPending     tells whether a restart of the agent is still running or queued
	 */
	public ProgrammableReEvaluationScheduler(
		final AgentContextHolder agentContextHolder,
		final TaskScheduler taskScheduler,
		final ReloadTrigger reloadTrigger,
		final BooleanSupplier restartPending
	) {
		this.agentContextHolder = agentContextHolder;
		this.taskScheduler = taskScheduler;
		this.reloadTrigger = reloadTrigger;
		this.restartPending = restartPending;
	}

	/**
	 * Discovers the declared re-evaluations of the current context and schedules their cron tasks.
	 * Call {@link #stop()} before discarding a context.
	 */
	public void start() {
		discoverAndSchedule();
		scheduleDiscoverySweep();
	}

	/**
	 * Schedules the periodic discovery sweep described by {@link #DISCOVERY_INTERVAL}, so a template
	 * added while the agent runs starts firing without a restart, whichever reload path loaded it.
	 */
	private void scheduleDiscoverySweep() {
		try {
			sweepFuture = taskScheduler.scheduleAtFixedRate(this::rediscoverNewRegistrations, DISCOVERY_INTERVAL);
		} catch (Exception e) {
			log.error("Failed to schedule the re-evaluation discovery sweep: {}", e.getMessage());
			log.debug("Discovery sweep scheduling error:", e);
		}
	}

	/**
	 * Re-runs discovery against the current context: schedules any re-evaluation not already known,
	 * cancels the schedules that are no longer declared, and leaves the others untouched.
	 * <p>
	 * A reload that only applies resource-level changes does not rebuild the {@link AgentContext}, so
	 * it never goes through {@link #stop()}/{@link #start()} again, and the reload path that notices a
	 * new or deleted template is not always this scheduler's own. Without this method, a template
	 * added while the agent runs would never fire, and a deleted one would keep firing, until a full
	 * restart. Safe to call repeatedly; a no-op when nothing was added or removed.
	 * </p>
	 */
	public void rediscoverNewRegistrations() {
		discoverAndSchedule();
	}

	/**
	 * Discovers every {@link ScheduledReEvaluation} currently exposed by the context's providers and
	 * schedules a cron task for each one not already tracked in {@link #scheduledCrons}, reschedules
	 * one whose cron changed, and cancels those no longer declared. Guarded by
	 * {@link #lock} so it never races a concurrent re-evaluation's compare-and-merge step.
	 */
	private void discoverAndSchedule() {
		final AgentContext context = agentContextHolder.getAgentContext();
		if (context == null || context.getExtensionManager() == null) {
			return;
		}
		final ExtensionManager extensionManager = context.getExtensionManager();
		final List<IConfigurationProvider> providers = extensionManager.getConfigurationProviderExtensions();
		if (providers == null) {
			return;
		}
		synchronized (lock) {
			if (stopped) {
				log.debug("Skipping a re-evaluation discovery pass: the scheduler was stopped.");
				return;
			}
			final Set<String> currentIds = new HashSet<>();
			for (final IConfigurationProvider provider : providers) {
				for (final ScheduledReEvaluation reEvaluation : provider.getScheduledReEvaluations()) {
					final String id = reEvaluation.id();
					currentIds.add(id);
					final String appliedCron = scheduledCrons.get(id);
					if (reEvaluation.cron().equals(appliedCron)) {
						// Already handled by a previous discovery pass, on the very same cron.
						continue;
					}
					if (appliedCron == null) {
						// Seed the last-known fragment so a first firing with unchanged data does not reload.
						// Only when none is known: after resume() the baseline kept from before the pause must win,
						// since the provider's cache may hold a change that was never applied.
						provider.currentFragment(id).ifPresent(fragment -> lastFragments.putIfAbsent(id, fragment));
					} else {
						// The template edited its expression: drop the task still firing on the old one. The
						// last-known fragment is kept, since only the cadence changed, not the data.
						cancelTask(id);
						log.info(
							"Re-evaluation of '{}' changed its cron from '{}' to '{}'; it is rescheduled.",
							id,
							appliedCron,
							reEvaluation.cron()
						);
					}
					scheduledCrons.put(id, reEvaluation.cron());
					scheduleRegistration(provider, reEvaluation);
				}
			}
			cancelUndeclared(currentIds);
		}
	}

	/**
	 * Cancels every schedule whose re-evaluation is no longer declared, typically because its template
	 * was deleted. Without this, the cron task of a removed template would keep firing against a
	 * provider that no longer knows it, warning on every tick until the agent is restarted.
	 *
	 * @param currentIds the ids the providers declare right now
	 */
	private void cancelUndeclared(final Set<String> currentIds) {
		final Iterator<String> knownIds = scheduledCrons.keySet().iterator();
		while (knownIds.hasNext()) {
			final String id = knownIds.next();
			if (currentIds.contains(id)) {
				continue;
			}
			knownIds.remove();
			lastFragments.remove(id);
			restartRequestedFragments.remove(id);
			cancelTask(id);
			log.info("Re-evaluation of '{}' is no longer declared; its schedule is cancelled.", id);
		}
	}

	/**
	 * Cancels and forgets the cron task of a single re-evaluation, if it holds one.
	 *
	 * @param reEvaluationId the re-evaluation whose task must be cancelled
	 */
	private void cancelTask(final String reEvaluationId) {
		final ScheduledFuture<?> future = scheduledTasks.remove(reEvaluationId);
		if (future != null) {
			future.cancel(false);
		}
	}

	/**
	 * Schedules a single re-evaluation's cron task.
	 *
	 * @param provider     the owning configuration provider
	 * @param reEvaluation the scheduled re-evaluation (cron + id)
	 */
	private void scheduleRegistration(final IConfigurationProvider provider, final ScheduledReEvaluation reEvaluation) {
		try {
			final CronTrigger trigger = new CronTrigger(reEvaluation.cron());
			final ScheduledFuture<?> future = taskScheduler.schedule(
				() -> onReEvaluation(provider, reEvaluation.id()),
				trigger
			);
			if (future != null) {
				scheduledTasks.put(reEvaluation.id(), future);
			}
			log.info("Scheduled re-evaluation of '{}' with cron '{}'.", reEvaluation.id(), reEvaluation.cron());
		} catch (Exception e) {
			log.error("Invalid cron '{}' for re-evaluation '{}': {}", reEvaluation.cron(), reEvaluation.id(), e.getMessage());
		}
	}

	/**
	 * Re-evaluates one template and, if its fragment changed, triggers a reload.
	 * <p>
	 * The render itself runs <b>outside</b> {@link #lock}: it re-runs every data source the template
	 * reads, which can be slow, and holding the lock across it would stall every other template's
	 * firing behind it. Only the compare-and-merge step, which must be atomic so simultaneous
	 * firings do not clobber one another, is guarded.
	 * </p>
	 *
	 * @param provider       the owning configuration provider
	 * @param reEvaluationId the re-evaluation to refresh
	 */
	void onReEvaluation(final IConfigurationProvider provider, final String reEvaluationId) {
		// A cron firing is just a re-evaluation nobody asked for explicitly: it goes through the very
		// same path as the manual one, which already logs every outcome.
		try {
			reevaluateNow(provider, reEvaluationId);
		} catch (SchedulerStoppedException e) {
			// A firing that was already running when a restart stopped this scheduler: nothing to do.
			log.debug("Dropped the re-evaluation of '{}': the scheduler was stopped.", reEvaluationId);
		}
	}

	/**
	 * Thrown when a re-evaluation reaches a scheduler that a restart has stopped. The caller can retry
	 * once the restart is over.
	 */
	public static class SchedulerStoppedException extends IllegalStateException {

		private static final long serialVersionUID = 1L;

		/**
		 * Creates the exception.
		 */
		public SchedulerStoppedException() {
			super("The agent is restarting. Try again once the restart is over.");
		}
	}

	/**
	 * Re-evaluates one unit and, if its fragment changed, reloads the configuration.
	 * <p>
	 * This is the single entry point for both a cron firing and an on-demand re-evaluation, so the two
	 * share the same change detection, the same baseline and the same lock: a manual re-evaluation can
	 * never interleave with a scheduled one, and neither leaves the other's change detection stale.
	 * </p>
	 * <p>
	 * The whole sequence &mdash; render, cache publication, reload and baseline update &mdash; is held
	 * under a lock private to that re-evaluation ({@link #reEvaluationLocks}), so the same template is
	 * never processed twice at the same time and the baseline always records the fragment that was
	 * actually applied. Other templates use other locks and are not delayed by it.
	 * </p>
	 * <p>
	 * The re-evaluation itself runs <b>outside</b> {@link #lock}: it re-runs the data sources behind
	 * that unit, which can be slow, and holding the shared lock across it would stall every other
	 * firing. Only the compare-and-reload step, which must be atomic so simultaneous firings of
	 * <em>different</em> templates do not clobber one another, is guarded by it. The reload reuses
	 * every other unit's cached fragment, so re-evaluating one template does not re-run the sources of
	 * all the others.
	 * </p>
	 *
	 * @param provider       the owning configuration provider
	 * @param reEvaluationId the re-evaluation to refresh
	 * @return what the re-evaluation ended up doing
	 * @throws SchedulerStoppedException when a restart stopped this scheduler before the reload
	 */
	public ReEvaluationOutcome reevaluateNow(final IConfigurationProvider provider, final String reEvaluationId) {
		// Taken before the shared lock, and never in the other order: discovery and the merge step below
		// take the shared lock alone, so the two can never wait on each other.
		synchronized (reEvaluationLocks.computeIfAbsent(reEvaluationId, id -> new Object())) {
			seedBaseline(provider, reEvaluationId);

			final Optional<JsonNode> fragment = provider.reevaluate(reEvaluationId);
			if (fragment.isEmpty()) {
				log.warn("Re-evaluation of '{}' produced nothing; keeping the last good value.", reEvaluationId);
				return ReEvaluationOutcome.NOTHING_PRODUCED;
			}
			synchronized (lock) {
				// Checked under the lock: stop() may have run while this template was rendering.
				if (stopped) {
					throw new SchedulerStoppedException();
				}
				if (fragment.get().equals(lastFragments.get(reEvaluationId))) {
					log.debug("Re-evaluation of '{}' left the configuration unchanged.", reEvaluationId);
					return ReEvaluationOutcome.UNCHANGED;
				}
				if (fragment.get().equals(restartRequestedFragments.get(reEvaluationId)) && restartPending.getAsBoolean()) {
					log.debug(
						"Re-evaluation of '{}' produced the change a pending restart will apply; no new restart is requested.",
						reEvaluationId
					);
					return ReEvaluationOutcome.RESTART_REQUESTED;
				}
				log.info("Re-evaluation of '{}' changed the configuration; reloading.", reEvaluationId);
				try {
					// Only this re-evaluation was re-run; the provider serves every other unit from cache, so
					// one template's cron does not re-run the data sources of all the others.
					final ReloadResult[] result = new ReloadResult[1];
					provider.runReusingCachedFragments(() -> result[0] = reloadTrigger.triggerReload());
					if (result[0] == ReloadResult.GLOBAL_RESTART_REQUIRED) {
						// The restart was only requested. The baseline stays where it is, so that if the restart
						// fails the next firing sees the difference again and retries. A successful restart
						// replaces this scheduler, and the new one reads the new baseline.
						restartRequestedFragments.put(reEvaluationId, fragment.get());
						log.info(
							"Re-evaluation of '{}' requires a restart of the agent; the restart was requested.",
							reEvaluationId
						);
						return ReEvaluationOutcome.RESTART_REQUESTED;
					}
					// The baseline only moves once the change was applied. A failed reload leaves it behind,
					// so the next firing sees the same difference again and retries instead of going quiet
					// on a configuration that was never updated.
					lastFragments.put(reEvaluationId, fragment.get());
					restartRequestedFragments.remove(reEvaluationId);
					return ReEvaluationOutcome.RELOADED;
				} catch (Exception e) {
					log.error(
						"Reload after re-evaluation of '{}' failed: {}. The change is kept pending and retried on the next firing.",
						reEvaluationId,
						e.getMessage()
					);
					log.debug("Reload error:", e);
					return ReEvaluationOutcome.RELOAD_FAILED;
				}
			}
		}
	}

	/**
	 * Records what the running configuration already holds for a re-evaluation, when nothing is known
	 * about it yet.
	 * <p>
	 * Discovery seeds the baseline of every template that declares a schedule. A template that
	 * declares none is never seen by discovery, yet it can still be re-evaluated on demand: without
	 * this, its first request would compare against nothing, always conclude that the configuration
	 * changed, and rebuild it even when the template produced exactly what was already loaded.
	 * </p>
	 * <p>
	 * Called before the render, since the render replaces what the provider holds for that template.
	 * </p>
	 *
	 * @param provider       the owning configuration provider
	 * @param reEvaluationId the re-evaluation whose baseline must be known
	 */
	private void seedBaseline(final IConfigurationProvider provider, final String reEvaluationId) {
		synchronized (lock) {
			if (lastFragments.containsKey(reEvaluationId)) {
				return;
			}
		}
		// Read outside the lock: it is a lookup on the provider, and the merge step must stay free.
		final Optional<JsonNode> loaded = provider.currentFragment(reEvaluationId);
		if (loaded.isEmpty()) {
			return;
		}
		synchronized (lock) {
			lastFragments.putIfAbsent(reEvaluationId, loaded.get());
		}
	}

	/**
	 * Cancels every cron task and the discovery sweep, and rejects any re-evaluation until
	 * {@link #resume()}, while keeping what is known about each template: its baseline, and the
	 * restart its last change requested.
	 * <p>
	 * Used while a restart builds the new context, so no firing changes what that build reads. When
	 * the restart fails, {@link #resume()} picks up where this left off, and a change whose restart
	 * failed is still seen as a change and retried.
	 * </p>
	 */
	public void pause() {
		synchronized (lock) {
			stopped = true;
			if (sweepFuture != null) {
				sweepFuture.cancel(false);
				sweepFuture = null;
			}
			scheduledTasks.values().forEach(future -> future.cancel(false));
			scheduledTasks.clear();
			// Forgotten so that resume() schedules every template again.
			scheduledCrons.clear();
		}
	}

	/**
	 * Schedules the cron tasks and the discovery sweep again after {@link #pause()}, with the
	 * baselines kept from before the pause. Does nothing when the scheduler is not paused.
	 */
	public void resume() {
		synchronized (lock) {
			if (!stopped) {
				return;
			}
			stopped = false;
			discoverAndSchedule();
			scheduleDiscoverySweep();
		}
	}

	/**
	 * Cancels every scheduled re-evaluation task and forgets every template. Called before the owning
	 * {@link AgentContext} is discarded (e.g. on restart). A stopped scheduler is not meant to be
	 * resumed: a new one is created for the new context.
	 */
	public void stop() {
		synchronized (lock) {
			pause();
			lastFragments.clear();
			restartRequestedFragments.clear();
			// Nothing new can be admitted once the context is gone, so the per-template locks are
			// released with the rest of the state. They are kept while the scheduler runs, even for a
			// deleted template: dropping one that a re-evaluation still holds would let a concurrent one
			// take a fresh lock and run beside it.
			reEvaluationLocks.clear();
		}
	}
}
