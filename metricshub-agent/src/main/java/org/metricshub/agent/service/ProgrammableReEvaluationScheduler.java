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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * When a task fires, the whole template is rendered again. If the fragment it produces actually
 * changed, a reload is triggered through the injected {@link ReloadTrigger}, which rebuilds the
 * running configuration and applies the differences.
 * </p>
 * <p>
 * Several templates can be due at the same time. Their renders run concurrently &mdash; a slow data
 * source in one template must not hold up another &mdash; but the compare-and-merge step that
 * follows is serialized by a single lock, so simultaneous firings are merged one at a time and none
 * clobbers another.
 * </p>
 * <p>
 * {@link #rediscoverNewRegistrations()} lets a {@code LOCAL_ONLY} reload (one that applies a new
 * template's content without rebuilding the {@link AgentContext}) pick up that template's schedule
 * without a full restart; see its javadoc for why this is needed.
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
	 * How often the scheduler re-checks its providers for re-evaluations it does not know yet.
	 * <p>
	 * A newly added template is loaded by whichever reload path noticed the file (the configuration
	 * file watcher, a UI save, a re-evaluation-driven reload), and those paths do not all go through
	 * this scheduler. Sweeping periodically makes the pickup independent of which one ran. The sweep
	 * only compares already-computed ids against {@link #scheduledIds} and performs no I/O.
	 * </p>
	 */
	private static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);

	private final AgentContextHolder agentContextHolder;
	private final TaskScheduler taskScheduler;
	private final ReloadTrigger reloadTrigger;

	/** Serializes the merge step so simultaneous firings do not race on the reload. */
	private final Object lock = new Object();

	/** The scheduled cron tasks, so they can be cancelled on {@link #stop()}. */
	private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();

	/** Last published fragment per re-evaluation id, so an unchanged re-evaluation skips the reload. */
	private final Map<String, JsonNode> lastFragments = new HashMap<>();

	/** Ids already scheduled, so {@link #rediscoverNewRegistrations()} does not re-register them. */
	private final Set<String> scheduledIds = new HashSet<>();

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
			final ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
				this::rediscoverNewRegistrations,
				DISCOVERY_INTERVAL
			);
			if (future != null) {
				scheduledFutures.add(future);
			}
		} catch (Exception e) {
			log.error("Failed to schedule the re-evaluation discovery sweep: {}", e.getMessage());
			log.debug("Discovery sweep scheduling error:", e);
		}
	}

	/**
	 * Re-runs discovery against the current context and schedules any re-evaluation not already
	 * known, leaving every already-scheduled cron task untouched.
	 * <p>
	 * A {@code LOCAL_ONLY} reload (for example a new template that only adds a resource or a resource
	 * group) applies its content to the running configuration but does not rebuild the
	 * {@link AgentContext}, so it never goes through {@link #stop()}/{@link #start()} again. Without
	 * this method, a schedule declared by such a template would only ever be picked up by a full
	 * restart. Safe to call repeatedly; a no-op once every current registration is already scheduled.
	 * </p>
	 */
	public void rediscoverNewRegistrations() {
		discoverAndSchedule();
	}

	/**
	 * Discovers every {@link ScheduledReEvaluation} currently exposed by the context's providers and
	 * schedules a cron task for each one not already tracked in {@link #scheduledIds}. Guarded by
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
			for (final IConfigurationProvider provider : providers) {
				for (final ScheduledReEvaluation reEvaluation : provider.getScheduledReEvaluations()) {
					if (!scheduledIds.add(reEvaluation.id())) {
						// Already scheduled from a previous discovery pass.
						continue;
					}
					// Seed the last-known fragment so a first firing with unchanged data does not reload.
					provider
						.currentFragment(reEvaluation.id())
						.ifPresent(fragment -> lastFragments.put(reEvaluation.id(), fragment));
					scheduleRegistration(provider, reEvaluation);
				}
			}
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
				scheduledFutures.add(future);
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
		final Optional<JsonNode> fragment = provider.reevaluate(reEvaluationId);
		if (fragment.isEmpty()) {
			log.warn("Re-evaluation of '{}' produced nothing; keeping the last good value.", reEvaluationId);
			return;
		}
		synchronized (lock) {
			if (fragment.get().equals(lastFragments.get(reEvaluationId))) {
				log.debug("Re-evaluation of '{}' left the configuration unchanged.", reEvaluationId);
				return;
			}
			lastFragments.put(reEvaluationId, fragment.get());
			log.info("Re-evaluation of '{}' changed the configuration; reloading.", reEvaluationId);
			try {
				reloadTrigger.triggerReload();
			} catch (Exception e) {
				log.error("Reload after re-evaluation of '{}' failed: {}", reEvaluationId, e.getMessage());
				log.debug("Reload error:", e);
			}
		}
	}

	/**
	 * Cancels every scheduled re-evaluation task. Called before the owning {@link AgentContext} is
	 * discarded (e.g. on restart).
	 */
	public void stop() {
		synchronized (lock) {
			scheduledFutures.forEach(future -> future.cancel(false));
			scheduledFutures.clear();
			scheduledIds.clear();
		}
	}
}
