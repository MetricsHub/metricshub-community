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
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.ConfigurationReloadService;
import org.metricshub.agent.service.ProgrammableReEvaluationScheduler;
import org.metricshub.agent.service.ProgrammableReEvaluationScheduler.ReEvaluationOutcome;
import org.metricshub.agent.service.ReloadService;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IConfigurationProvider;
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
	 * Re-evaluates a single template on demand and applies the resulting configuration changes.
	 * <p>
	 * This is the manual counterpart of a cron firing, and runs through the very same path: it works
	 * whether or not the template declares a {@code $schedule.cron(...)}, so a user can refresh a
	 * template's data whenever they want. Only the targeted template is re-run; the configuration of
	 * every other one is reused as it stands, and the reload is skipped altogether when the template
	 * produced the same result as before.
	 * </p>
	 *
	 * @param fileName name of the template file, relative to the configuration directory
	 * @return what the re-evaluation ended up doing
	 * @throws IllegalStateException when the agent context or the scheduler is unavailable
	 * @throws IllegalArgumentException when no provider handles the given file
	 */
	public ReEvaluationOutcome reevaluateTemplate(final String fileName) {
		final AgentContext currentContext = agentContextHolder.getAgentContext();
		if (currentContext == null || currentContext.getExtensionManager() == null) {
			throw new IllegalStateException("The agent context is not available yet.");
		}

		final String reEvaluationId = currentContext.getConfigDirectory().resolve(fileName).toAbsolutePath().toString();

		final IConfigurationProvider provider = currentContext
			.getExtensionManager()
			.getConfigurationProviderExtensions()
			.stream()
			.filter(candidate -> candidate.getFileExtensions().stream().anyMatch(fileName.toLowerCase(Locale.ROOT)::endsWith))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No configuration provider handles '" + fileName + "'."));

		final ProgrammableReEvaluationScheduler currentScheduler = scheduler;
		if (currentScheduler == null) {
			throw new IllegalStateException("The re-evaluation scheduler is not available yet.");
		}

		log.info("Manual re-evaluation of '{}' requested.", fileName);

		// Delegate to the scheduler rather than reloading here: a cron firing and a manual request then
		// share the same change detection, the same baseline and the same lock, so the two can never
		// interleave and neither leaves the other's change detection stale.
		return currentScheduler.reevaluateNow(provider, reEvaluationId);
	}

	/**
	 * Reloads the whole configuration on demand, re-running <b>every</b> configuration source rather
	 * than a single template, and applies the differences.
	 *
	 * @throws IllegalStateException when the agent context is unavailable
	 */
	public void reloadConfiguration() {
		if (agentContextHolder.getAgentContext() == null) {
			throw new IllegalStateException("The agent context is not available yet.");
		}
		log.info("Manual configuration reload requested.");
		reload();
	}

	/**
	 * Rebuilds the configuration and applies the differences. Invoked by the scheduler after a
	 * re-evaluation changed a template's output, and by the manual entry points above.
	 */
	void reload() {
		final AgentContext currentContext = agentContextHolder.getAgentContext();
		if (currentContext == null) {
			return;
		}
		// Captured before the reload so the lazily built restart context does not read a context that
		// is being replaced. The extension manager is loaded once at boot and carried across reloads,
		// so the reference stays the one a restart would find anyway.
		final String configDirectory = currentContext.getConfigDirectory().toString();
		final ExtensionManager extensionManager = currentContext.getExtensionManager();

		try {
			ConfigurationReloadService.builder()
				.withRunningAgentContext(currentContext)
				.withComparisonContextSupplier(() -> buildContext(configDirectory, extensionManager))
				.withRestartContextSupplier(() -> buildContext(configDirectory, extensionManager))
				.withRestartRequester(reloadedContextSupplier -> {
					agentLifecycleService.restartAsync(reloadedContextSupplier);
					return true;
				})
				// A resource-level reload does not rebuild the AgentContext, so nothing else will pick up
				// the schedule a newly added template declares.
				.withAfterLocalChanges(this::rediscoverSchedules)
				.build()
				.reload();
		} catch (Exception e) {
			log.error("Programmable re-evaluation reload failed: {}", e.getMessage());
			log.debug("Reload error:", e);
		}
	}

	/**
	 * Re-runs the scheduler's discovery, if it is up, after resource-level changes were applied.
	 */
	private void rediscoverSchedules() {
		final ProgrammableReEvaluationScheduler currentScheduler = scheduler;
		if (currentScheduler != null) {
			currentScheduler.rediscoverNewRegistrations();
		}
	}

	/**
	 * Builds the {@link AgentContext} served to a full restart. Invoked lazily, on the restart thread,
	 * and only when the queued restart actually runs. The checked exception the constructor declares
	 * is wrapped, since the lifecycle service consumes a plain supplier and records the failure.
	 *
	 * @param configDirectory  the configuration directory the context is built from
	 * @param extensionManager the extension manager carried into the new context
	 * @return the newly built context
	 */
	private static AgentContext buildContext(final String configDirectory, final ExtensionManager extensionManager) {
		try {
			return new AgentContext(configDirectory, extensionManager);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to build the reloaded AgentContext: " + e.getMessage(), e);
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
