package org.metricshub.agent.service;

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

import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.service.scheduling.ResourceGroupScheduling;
import org.metricshub.agent.service.scheduling.ResourceScheduling;
import org.metricshub.agent.service.scheduling.SelfScheduling;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Service for managing task scheduling related to MetricsHub agent.
 * It handles scheduling of self-recorders, resource group recorders, and resource recorders.
 */
@Data
@Builder(setterPrefix = "with")
@Slf4j
public class TaskSchedulingService {

	private Path configDirectory;
	private AgentConfig agentConfig;
	private AgentInfo agentInfo;
	private ThreadPoolTaskScheduler taskScheduler;
	private Map<String, ScheduledFuture<?>> schedules;
	private OtelCollectorProcessService otelCollectorProcessService;
	private Map<String, Map<String, TelemetryManager>> telemetryManagers;
	private MetricDefinitions hostMetricDefinitions;
	private ExtensionManager extensionManager;
	private MetricsExporter metricsExporter;

	/**
	 * Start scheduling
	 */
	public void start() {
		// Self recorder scheduling
		scheduleSelfRecorder();

		// Top level resources recorders scheduling
		scheduleTopLevelResourcesRecorders();

		// Resource Group recorders scheduling
		scheduleResourceGroupRecorders();
	}

	/**
	 * Initialize the {@link SelfScheduling} to schedule agent metrics recorder
	 * which triggers a periodic task to flush metrics
	 */
	void scheduleSelfRecorder() {
		final ExtensionManager safeExtensionManager = getExtensionManagerOrEmpty();
		SelfScheduling
			.builder()
			.withAgentConfig(agentConfig)
			.withAgentInfo(agentInfo)
			.withExtensionManager(safeExtensionManager)
			.withMetricsExporter(metricsExporter)
			.withSchedules(schedules)
			.withTaskScheduler(taskScheduler)
			.build()
			.schedule();
	}

	/**
	 * Initialize the {@link ResourceGroupScheduling} to schedule recorders for each resource group
	 */
	void scheduleResourceGroupRecorders() {
		agentConfig
			.getResourceGroups()
			.entrySet()
			.stream()
			.filter(entry -> Objects.nonNull(entry.getValue()))
			.forEach(entry -> {
				final ResourceGroupConfig resourceGroupConfig = entry.getValue();
				final String resourceGroupKey = entry.getKey();
				scheduleResourceGroup(resourceGroupKey, resourceGroupConfig);
				scheduleResourcesInResourceGroups(resourceGroupKey, resourceGroupConfig);
			});

		log.info("Resource Group Recorders scheduled.");
	}

	/**
	 * Schedule top level resources recorders
	 */
	void scheduleTopLevelResourcesRecorders() {
		agentConfig
			.getResources()
			.entrySet()
			.stream()
			.filter(entry -> Objects.nonNull(entry.getValue()))
			.forEach(entry -> scheduleResource(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, entry.getKey(), entry.getValue()));

		log.info("Top level resources Recorders scheduled.");
	}

	/**
	 * Initialize the {@link ResourceGroupScheduling} to schedule recorders for a resource group
	 *
	 * @param resourceGroupKey    unique key of the resource group configuration.
	 * @param resourceGroupConfig {@link ResourceGroupConfig} instance configured by the user.
	 */
	void scheduleResourceGroup(final String resourceGroupKey, final ResourceGroupConfig resourceGroupConfig) {
		final ExtensionManager safeExtensionManager = getExtensionManagerOrEmpty();
		ResourceGroupScheduling
			.builder()
			.withAgentConfig(agentConfig)
			.withExtensionManager(safeExtensionManager)
			.withMetricsExporter(metricsExporter)
			.withSchedules(schedules)
			.withTaskScheduler(taskScheduler)
			.withResourceGroupKey(resourceGroupKey)
			.withResourceGroupConfig(resourceGroupConfig)
			.withAgentInfo(agentInfo)
			.build()
			.schedule();
	}

	/**
	 * Schedule each resource configured in the given {@link ResourceGroupConfig} instance
	 *
	 * @param resourceGroupKey    The key of the resource group defining the {@link ResourceGroupConfig} instance
	 * @param resourceGroupConfig {@link ResourceGroupConfig} instance defining resource configurations
	 */
	void scheduleResourcesInResourceGroups(final String resourceGroupKey, final ResourceGroupConfig resourceGroupConfig) {
		resourceGroupConfig
			.getResources()
			.entrySet()
			.stream()
			.filter(entry -> Objects.nonNull(entry.getValue()))
			.forEach(entry -> scheduleResource(resourceGroupKey, entry.getKey(), entry.getValue()));
	}

	/**
	 * Initialize the {@link ResourceScheduling} that schedules
	 * a monitoring task for the given resource configuration
	 *
	 * @param resourceGroupKey The key of the parent resource group configuration.
	 * @param resourceKey      The unique key of the resource configuration.
	 * @param resourceConfig   The {@link ResourceConfig} instance configured by the user.
	 */
	void scheduleResource(final String resourceGroupKey, final String resourceKey, final ResourceConfig resourceConfig) {
		final ExtensionManager safeExtensionManager = getExtensionManagerOrEmpty();
		// Get the TelemetryManager instance
		final Map<String, TelemetryManager> perGroupTelemetryManagers = telemetryManagers.get(resourceGroupKey);
		if (perGroupTelemetryManagers == null) {
			log.info("The resource group {} has been removed from the configuration.", resourceGroupKey);
			return;
		}

		// The resource TelemetryManager instance
		final TelemetryManager telemetryManager = perGroupTelemetryManagers.get(resourceKey);

		if (telemetryManager == null) {
			log.info("The resource {} has been removed from the configuration.", resourceKey);
			return;
		}

		// Schedule monitoring of the current resource configuration
		ResourceScheduling
			.builder()
			.withMetricsExporter(metricsExporter)
			.withSchedules(schedules)
			.withTaskScheduler(taskScheduler)
			.withResourceGroupKey(resourceGroupKey)
			.withResourceKey(resourceKey)
			.withResourceConfig(resourceConfig)
			.withTelemetryManager(telemetryManager)
			.withHostMetricDefinitions(hostMetricDefinitions)
			.withExtensionManager(safeExtensionManager)
			.build()
			.schedule();
	}

	/**
	 * Resolve the extension manager or provide an empty fallback.
	 *
	 * @return extension manager instance
	 */
	private ExtensionManager getExtensionManagerOrEmpty() {
		return extensionManager != null ? extensionManager : ExtensionManager.empty();
	}

	/**
	 * Create and initialize a scheduler instance
	 *
	 * @param jobPoolSize The size of the job pool
	 * @return new instance of {@link ThreadPoolTaskScheduler}
	 */
	public static ThreadPoolTaskScheduler newScheduler(final int jobPoolSize) {
		// Create the TaskScheduler
		final ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		// Set the maximum pool size.
		threadPoolTaskScheduler.setPoolSize(jobPoolSize);

		// Set the thread name prefix
		threadPoolTaskScheduler.setThreadNamePrefix("metricshub-task-");

		// Initialization
		threadPoolTaskScheduler.initialize();

		return threadPoolTaskScheduler;
	}

	/**
	 * Cancels all the {@link ScheduledFuture} instances and shuts down the task scheduler
	 */
	public void stop() {
		schedules.values().forEach(action -> action.cancel(true));
		metricsExporter.shutdown();
		taskScheduler.destroy();
	}
}
