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
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
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
 * clobbers another.
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
		/** Rebuilds the running configuration from the current templates and applies changes. */
		void triggerReload();
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

	/** Serializes the merge step so simultaneous firings do not race on the reload. */
	private final Object lock = new Object();

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
	 * Creates the scheduler.
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
		this.agentContextHolder = agentContextHolder;
		this.taskScheduler = taskScheduler;
		this.reloadTrigger = reloadTrigger;
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
						provider.currentFragment(id).ifPresent(fragment -> lastFragments.put(id, fragment));
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
		reevaluateNow(provider, reEvaluationId);
	}

	/**
	 * Re-evaluates one unit and, if its fragment changed, reloads the configuration.
	 * <p>
	 * This is the single entry point for both a cron firing and an on-demand re-evaluation, so the two
	 * share the same change detection, the same baseline and the same lock: a manual re-evaluation can
	 * never interleave with a scheduled one, and neither leaves the other's change detection stale.
	 * </p>
	 * <p>
	 * The re-evaluation itself runs <b>outside</b> {@link #lock}: it re-runs the data sources behind
	 * that unit, which can be slow, and holding the lock across it would stall every other firing.
	 * Only the compare-and-reload step, which must be atomic so simultaneous firings do not clobber one
	 * another, is guarded. The reload reuses every other unit's cached fragment, so re-evaluating one
	 * template does not re-run the sources of all the others.
	 * </p>
	 *
	 * @param provider       the owning configuration provider
	 * @param reEvaluationId the re-evaluation to refresh
	 * @return what the re-evaluation ended up doing
	 */
	public ReEvaluationOutcome reevaluateNow(final IConfigurationProvider provider, final String reEvaluationId) {
		final Optional<JsonNode> fragment = provider.reevaluate(reEvaluationId);
		if (fragment.isEmpty()) {
			log.warn("Re-evaluation of '{}' produced nothing; keeping the last good value.", reEvaluationId);
			return ReEvaluationOutcome.NOTHING_PRODUCED;
		}
		synchronized (lock) {
			if (fragment.get().equals(lastFragments.get(reEvaluationId))) {
				log.debug("Re-evaluation of '{}' left the configuration unchanged.", reEvaluationId);
				return ReEvaluationOutcome.UNCHANGED;
			}
			log.info("Re-evaluation of '{}' changed the configuration; reloading.", reEvaluationId);
			try {
				// Only this re-evaluation was re-run; the provider serves every other unit from cache, so
				// one template's cron does not re-run the data sources of all the others.
				provider.runReusingCachedFragments(reloadTrigger::triggerReload);
				// The baseline only moves once the change was applied. A failed reload leaves it behind,
				// so the next firing sees the same difference again and retries instead of going quiet
				// on a configuration that was never updated.
				lastFragments.put(reEvaluationId, fragment.get());
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

	/**
	 * Cancels every scheduled re-evaluation task. Called before the owning {@link AgentContext} is
	 * discarded (e.g. on restart).
	 */
	public void stop() {
		synchronized (lock) {
			if (sweepFuture != null) {
				sweepFuture.cancel(false);
				sweepFuture = null;
			}
			scheduledTasks.values().forEach(future -> future.cancel(false));
			scheduledTasks.clear();
			scheduledCrons.clear();
			lastFragments.clear();
		}
	}
}
