package org.metricshub.web.service;

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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.ProgrammableReEvaluationScheduler;
import org.metricshub.agent.service.ReloadService;
import org.metricshub.agent.service.ReloadService.ReloadResult;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

/**
 * {@link StartupHook} that starts the {@link ProgrammableReEvaluationScheduler} when the agent has
 * started.
 * <p>
 * As a {@code StartupHook} it is run by {@link AgentStartupRunner} on application startup, so it
 * starts in <b>both</b> the community and enterprise agents (which boot the same Spring context)
 * without any edition-specific wiring.
 * </p>
 * <p>
 * The reload triggered after a template's output changed rebuilds the configuration, diffs it via
 * {@link ReloadService}, and either applies resource-level changes in place or delegates a global
 * restart to {@link AgentLifecycleService} — the same outcomes as a configuration-file edit. A
 * resource-level ({@code LOCAL_ONLY}) outcome also re-syncs the scheduler's own discovery
 * ({@link ProgrammableReEvaluationScheduler#rediscoverNewRegistrations()}), so a schedule declared
 * by a newly added template is picked up without waiting for a full restart.
 * </p>
 * <p>
 * Around a global restart the schedules are cancelled before the outgoing context is stopped and
 * re-created against the rebuilt one, so no firing runs against a context being torn down. The
 * restart itself is handed a lazy supplier rather than a pre-built context, so a request that gets
 * coalesced away leaks nothing.
 * </p>
 */
@Service
@Slf4j
public class ProgrammableReEvaluationLauncher implements StartupHook {

	/** Thread-pool size of the dedicated scheduler that fires re-evaluations. */
	private static final int POOL_SIZE = 2;

	private final AgentContextHolder agentContextHolder;
	private final AgentLifecycleService agentLifecycleService;

	private ThreadPoolTaskScheduler taskScheduler;
	private ProgrammableReEvaluationScheduler scheduler;

	/**
	 * Creates the launcher.
	 *
	 * @param agentContextHolder    holder of the active {@link AgentContext}
	 * @param agentLifecycleService lifecycle service used to apply global restarts and to re-sync the
	 *                              scheduler after a restart rebuilds the providers
	 */
	public ProgrammableReEvaluationLauncher(
		final AgentContextHolder agentContextHolder,
		final AgentLifecycleService agentLifecycleService
	) {
		this.agentContextHolder = agentContextHolder;
		this.agentLifecycleService = agentLifecycleService;
	}

	/**
	 * Starts the scheduler on agent startup, and registers a post-restart hook so the declared
	 * re-evaluations are re-discovered against each rebuilt context. Any failure is caught so it never
	 * prevents the agent from starting.
	 */
	@Override
	public synchronized void onStartup() {
		if (scheduler != null) {
			return;
		}
		try {
			taskScheduler = TaskSchedulingService.newScheduler(POOL_SIZE);
			scheduler = new ProgrammableReEvaluationScheduler(agentContextHolder, taskScheduler, this::reload);
			scheduler.start();

			// A restart stops the services of the outgoing context. Cancel the schedules first, so no
			// firing can re-evaluate against a context that is being torn down or trigger a reload
			// while the swap is in progress.
			agentLifecycleService.addPreRestartHook(this::quiesce);

			// After a full restart the extension manager (and its providers) is rebuilt, so re-discover
			// the declared re-evaluations against the new context.
			agentLifecycleService.addPostRestartHook(context -> resync());
		} catch (Exception e) {
			log.error("Failed to start the programmable re-evaluation scheduler: {}", e.getMessage());
			log.debug("Programmable re-evaluation scheduler startup error", e);
		}
	}

	/**
	 * Cancels every schedule before a global restart tears the current context down. The scheduler
	 * itself is kept, and {@link #resync()} re-creates the schedules once the new context is in place.
	 * Any failure is caught so it never aborts the restart.
	 */
	private synchronized void quiesce() {
		try {
			if (scheduler != null) {
				scheduler.stop();
			}
		} catch (Exception e) {
			log.error("Failed to quiesce the programmable re-evaluation scheduler: {}", e.getMessage());
			log.debug("Programmable re-evaluation scheduler quiesce error", e);
		}
	}

	/**
	 * Stops and re-starts the scheduler against the current context (used after a global restart).
	 */
	private synchronized void resync() {
		try {
			if (scheduler != null) {
				scheduler.stop();
			}
			scheduler = new ProgrammableReEvaluationScheduler(agentContextHolder, taskScheduler, this::reload);
			scheduler.start();
		} catch (Exception e) {
			log.error("Failed to re-sync the programmable re-evaluation scheduler: {}", e.getMessage());
			log.debug("Programmable re-evaluation scheduler re-sync error", e);
		}
	}

	/**
	 * Rebuilds the configuration and applies the differences. Invoked by the scheduler after a
	 * re-evaluation changed a template's output.
	 */
	void reload() {
		final AgentContext currentContext = agentContextHolder.getAgentContext();
		if (currentContext == null) {
			return;
		}
		AgentContext reloadedContext = null;
		try {
			reloadedContext = new AgentContext(
				currentContext.getConfigDirectory().toString(),
				currentContext.getExtensionManager()
			);

			final ReloadResult result = ReloadService.builder()
				.withRunningAgentContext(currentContext)
				.withReloadedAgentContext(reloadedContext)
				.build()
				.reload();

			switch (result) {
				case GLOBAL_RESTART_REQUIRED -> {
					// The comparison context is closed here rather than handed over: restartAsync coalesces
					// requests and drops a superseded one WITHOUT invoking its supplier, so a pre-built
					// context would be left started with nothing to close it. The supplier below builds the
					// context lazily, on the restart thread, and only if the request actually runs.
					final String configDirectory = currentContext.getConfigDirectory().toString();
					final ExtensionManager extensionManager = currentContext.getExtensionManager();
					reloadedContext.close();
					agentLifecycleService.restartAsync(() -> new AgentContext(configDirectory, extensionManager));
				}
				case LOCAL_ONLY -> {
					// The rebuilt context reused the running ExtensionManager, so its providers (e.g. a
					// newly added .vm file) are already up to date; pick up any schedule they declare
					// before discarding the throwaway context, since no full restart will follow this
					// outcome to do it for us.
					scheduler.rediscoverNewRegistrations();
					reloadedContext.close();
				}
				case NO_CHANGE -> reloadedContext.close();
				default -> {
					log.warn("Unknown reload result: {}", result);
					reloadedContext.close();
				}
			}
		} catch (Exception e) {
			log.error("Programmable re-evaluation reload failed: {}", e.getMessage());
			log.debug("Reload error:", e);
			if (reloadedContext != null) {
				try {
					reloadedContext.close();
				} catch (Exception closeException) {
					log.debug("Failed to close the partially-built reloaded context.", closeException);
				}
			}
		}
	}

	/**
	 * Stops the scheduler and shuts down its thread pool when the application shuts down.
	 */
	@PreDestroy
	public synchronized void stop() {
		if (scheduler != null) {
			scheduler.stop();
			scheduler = null;
		}
		if (taskScheduler != null) {
			taskScheduler.shutdown();
			taskScheduler = null;
		}
	}
}
