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
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.METRICSHUB_RESOURCE_GROUP_KEY_FORMAT;
import static org.metricshub.agent.service.scheduling.ResourceScheduling.METRICSHUB_RESOURCE_KEY_FORMAT;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Handles the reload process of the running agent by comparing the current and reloaded configurations.
 * <p>
 * Applies changes dynamically when possible (e.g., resource additions/removals/updates) and performs a full restart
 * if global settings have changed.
 * </p>
 */
@Data
@Slf4j
@Builder(setterPrefix = "with")
public class ReloadService {

	/**
	 * The running Agent context
	 */
	private AgentContext runningAgentContext;

	/**
	 * The Agent Context that have been created after that the configuration file(s) have been modified.
	 */
	private AgentContext reloadedAgentContext;

	/**
	 * Reloads the running agent context by comparing it with the reloaded configuration.
	 * <p>
	 * Applies resource-level changes dynamically, and triggers a full restart if global configuration changes are detected.
	 * </p>
	 */
	public void reload() {
		log.info("Reloading the Agent Context...");

		// No changes have been made to Agent Context
		if (runningAgentContext.getAgentConfig().equals(reloadedAgentContext.getAgentConfig())) {
			return;
		}

		// Compare global configurations
		if (globalConfigurationHasChanged(runningAgentContext.getAgentConfig(), reloadedAgentContext.getAgentConfig())) {
			log.info("Global configuration has changed. Restarting MetricsHub Agent...");
			runningAgentContext.getTaskSchedulingService().stop();
			runningAgentContext = reloadedAgentContext;
			runningAgentContext.getTaskSchedulingService().start();
			log.info("MetricsHub Agent restarted with updated global configuration.");
			return;
		}

		// Compare top level resources
		log.info("Comparing and applying changes to top-level resources...");
		compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			runningAgentContext.getAgentConfig().getResources(),
			reloadedAgentContext.getAgentConfig().getResources()
		);

		// Compare Resource Groups
		log.info("Comparing and applying changes to resource groups...");
		compareResourceGroups(
			runningAgentContext.getAgentConfig().getResourceGroups(),
			reloadedAgentContext.getAgentConfig().getResourceGroups()
		);

		// Make sure the new Agent Context is stopped.
		reloadedAgentContext.getTaskSchedulingService().stop();
	}

	/**
	 * Compares two sets of resource definitions and applies changes (add, update, remove) to the scheduling service.
	 *
	 * @param resourceGroupKey the key representing the resource group (or top-level group)
	 * @param runningResources     the currently running resources
	 * @param newResources     the newly loaded resources
	 */
	public void compareResources(
		final String resourceGroupKey,
		final Map<String, ResourceConfig> runningResources,
		final Map<String, ResourceConfig> newResources
	) {
		final Set<String> allResources = Stream
			.concat(runningResources.keySet().stream(), newResources.keySet().stream())
			.collect(Collectors.toSet());

		for (final String resourceKey : allResources) {
			final ResourceConfig runningResource = runningResources.get(resourceKey);
			final ResourceConfig newResource = newResources.get(resourceKey);

			final String resourceSchedulingName = String.format(
				METRICSHUB_RESOURCE_KEY_FORMAT,
				resourceGroupKey,
				resourceKey
			);

			if (runningResource != null && newResource != null && !Objects.equals(runningResource, newResource)) {
				// The two resources are present but with different values
				// The running resource will be replaced by the new one
				log.info("Resource '{}' in group '{}' has changed. Updating...", resourceKey, resourceGroupKey);

				// Update the Resource Configuration in the Resource Group
				runningResources.put(resourceKey, newResource);

				// Update the Resource in the Task Scheduling Service
				updateResourceInTaskSchedulingService(resourceGroupKey, resourceKey, newResource, resourceSchedulingName);
			} else if (runningResource == null) {
				// The resource does not exist in the running agent
				// It needs to be added
				log.info("New resource '{}' detected in group '{}'. Scheduling...", resourceKey, resourceGroupKey);

				// Add the Resource Configuration in the Resource Group
				runningResources.put(resourceKey, newResource);

				// Add the resource in the Task Scheduling Service
				addResourceToTaskSchedulingService(resourceGroupKey, resourceKey, newResource);
			} else if (newResource == null) {
				// The resource does not exist in the new agent
				log.info("Resource '{}' removed from group '{}'. Stopping and cleaning up...", resourceKey, resourceGroupKey);

				// Remove the Resource Configuration from the Resource Group
				runningResources.remove(resourceKey);

				// Remote the Resource from the Task Scheduling Service
				removeResourceFromTaskSchedulingService(resourceGroupKey, resourceKey, resourceSchedulingName);

				runningAgentContext.getTaskSchedulingService().getTelemetryManagers().get(resourceGroupKey).remove(resourceKey);
			}
		}
	}

	/**
	 * Updates an existing scheduled resource by stopping it and rescheduling it with the new configuration.
	 *
	 * @param resourceGroupKey the key of the resource group the resource belongs to
	 * @param resourceKey      the unique identifier of the resource
	 * @param resourceConfig   the new configuration for the resource
	 * @param resourceName     the full internal name used in the scheduling service
	 */
	public void updateResourceInTaskSchedulingService(
		final String resourceGroupKey,
		final String resourceKey,
		final ResourceConfig resourceConfig,
		final String resourceName
	) {
		final TaskSchedulingService schedulingService = runningAgentContext.getTaskSchedulingService();
		// Get the resource schedule and stop it on the running agent.
		final ScheduledFuture<?> resourceSchedule = schedulingService.getSchedules().get(resourceName);

		if (resourceSchedule != null) {
			log.info("Stopping schedule for resource '{}' before updating...", resourceKey);
			resourceSchedule.cancel(true);
		} else {
			log.warn("Attempted to update resource '{}', but no active schedule was found.", resourceKey);
		}

		// Update the resource Telemetry manager
		updateTelemetryManagerInTaskSchedulingService(resourceGroupKey, resourceKey);

		// Schedule the resource again
		schedulingService.scheduleResource(resourceGroupKey, resourceKey, resourceConfig);
		log.info("Resource '{}' successfully updated and rescheduled.", resourceKey);
	}

	/**
	 * Stops and removes a scheduled resource and its telemetry manager.
	 *
	 * @param resourceGroupKey       group the resource belongs to
	 * @param resourceKey            unique resource identifier
	 * @param resourceSchedulingName full internal name used for scheduling
	 */
	public void removeResourceFromTaskSchedulingService(
		final String resourceGroupKey,
		final String resourceKey,
		final String resourceSchedulingName
	) {
		// Get the resource schedule and stop it on the running agent.
		final Map<String, ScheduledFuture<?>> schedules = runningAgentContext.getTaskSchedulingService().getSchedules();

		// retrieve the resource schedule
		final ScheduledFuture<?> resourceSchedule = schedules.get(resourceSchedulingName);

		if (resourceSchedule != null) {
			log.info("Stopping and removing scheduled resource '{}'.", resourceSchedulingName);
			resourceSchedule.cancel(true);
			// Remove the resource from the schedules map
			schedules.remove(resourceSchedulingName);
		} else {
			log.warn("Attempted to remove resource '{}', but it was not scheduled.", resourceSchedulingName);
		}

		// Remove the resource from the task scheduling service telemetry managers
		runningAgentContext.getTaskSchedulingService().getTelemetryManagers().get(resourceGroupKey).remove(resourceKey);
	}

	/**
	 * Adds and schedules a new resource in the agent context using the given configuration.
	 *
	 * @param resourceGroupKey the key of the resource group the resource belongs to
	 * @param resourceKey      the unique identifier of the new resource
	 * @param resourceConfig   the configuration to use when scheduling the resource
	 */
	public void addResourceToTaskSchedulingService(
		final String resourceGroupKey,
		final String resourceKey,
		final ResourceConfig resourceConfig
	) {
		// Add the resource Telemetry Manager to the task scheduling service
		updateTelemetryManagerInTaskSchedulingService(resourceGroupKey, resourceKey);

		// Schedule the new resource in the task scheduling service
		runningAgentContext.getTaskSchedulingService().scheduleResource(resourceGroupKey, resourceKey, resourceConfig);

		log.info("Scheduled new resource '{}' in group '{}'.", resourceKey, resourceGroupKey);
	}

	/**
	 * Updates the {@link TelemetryManager} instance for a specific resource in the running agent context,
	 * using the reloaded agent configuration as the source.
	 * <p>
	 * If the resource group does not exist in the current telemetry structure, it is initialized.
	 * </p>
	 *
	 * @param resourceGroupKey the key identifying the resource group the resource belongs to
	 * @param resourceKey      the unique key of the resource whose telemetry manager should be updated
	 */
	public void updateTelemetryManagerInTaskSchedulingService(final String resourceGroupKey, final String resourceKey) {
		final Map<String, Map<String, TelemetryManager>> newTelemetryManagers = reloadedAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, Map<String, TelemetryManager>> oldTelemetryManagers = runningAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers();

		// Add the TelemetryManager to the resource
		Map<String, TelemetryManager> oldResourceGroupTelemetryManagers = oldTelemetryManagers.get(resourceGroupKey);

		if (oldResourceGroupTelemetryManagers == null) {
			oldResourceGroupTelemetryManagers = new HashMap<>();
			oldTelemetryManagers.put(resourceGroupKey, oldResourceGroupTelemetryManagers);
		}

		oldResourceGroupTelemetryManagers.put(resourceKey, newTelemetryManagers.get(resourceGroupKey).get(resourceKey));
	}

	/**
	 * Compares and applies changes between the running and new sets of resource groups.
	 * <p>
	 * Each group is processed independently and delegated to {@code compareResources()}.
	 * </p>
	 *
	 * @param runningResourceGroups the current resource groups from the running context
	 * @param newResourceGroups the reloaded resource groups to compare against
	 */
	public void compareResourceGroups(
		final Map<String, ResourceGroupConfig> runningResourceGroups,
		final Map<String, ResourceGroupConfig> newResourceGroups
	) {
		final Set<String> allResourceGroups = Stream
			.concat(runningResourceGroups.keySet().stream(), newResourceGroups.keySet().stream())
			.collect(Collectors.toSet());

		for (final String resourceGroupKey : allResourceGroups) {
			final ResourceGroupConfig runningGroup = runningResourceGroups.get(resourceGroupKey);
			final ResourceGroupConfig newGroup = newResourceGroups.get(resourceGroupKey);

			if (runningGroup != null && newGroup != null) {
				// Compare the resources one by one
				compareResources(
					resourceGroupKey,
					runningGroup != null ? runningGroup.getResources() : new HashMap<>(),
					newGroup != null ? newGroup.getResources() : new HashMap<>()
				);
			} else if (runningGroup == null) {
				// A new Resource group has been added to the configuration
				log.info("The Resource Group {} is added to the configuration.", resourceGroupKey);

				// Add the new Resource Group to the running Agent
				runningAgentContext.getAgentConfig().getResourceGroups().put(resourceGroupKey, newGroup);

				final TaskSchedulingService runningTaskSchedulingService = runningAgentContext.getTaskSchedulingService();
				final TaskSchedulingService newTaskSchedulingService = reloadedAgentContext.getTaskSchedulingService();

				// Add the new Resources Telemetry Managers to the running Task Scheduling Service
				runningTaskSchedulingService
					.getTelemetryManagers()
					.put(resourceGroupKey, newTaskSchedulingService.getTelemetryManagers().get(resourceGroupKey));

				// Schedule the Resource Group
				runningTaskSchedulingService.scheduleResourceGroup(resourceGroupKey, newGroup);

				// Schedule all the resources
				runningTaskSchedulingService.scheduleResourcesInResourceGroups(resourceGroupKey, newGroup);
			} else if (newGroup == null) {
				// A Resource group has been removed from the configuration
				log.info("The Resource Group {} is removed from the configuration.", resourceGroupKey);

				// Remove the resource group from the running agent
				runningAgentContext.getAgentConfig().getResourceGroups().remove(resourceGroupKey);

				final Map<String, ScheduledFuture<?>> schedules = runningAgentContext.getTaskSchedulingService().getSchedules();

				final String resourceSchedulingName = METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted(resourceGroupKey);

				// Stop the scheduled Resource Group
				schedules.get(resourceSchedulingName).cancel(true);

				// Remove the resource from the scheduling map
				schedules.remove(resourceSchedulingName);

				// Stop the scheduled Resource Group Resources
				runningGroup
					.getResources()
					.forEach((String resourceKey, ResourceConfig resourceConfig) ->
						removeResourceFromTaskSchedulingService(
							resourceGroupKey,
							resourceKey,
							String.format(METRICSHUB_RESOURCE_KEY_FORMAT, resourceGroupKey, resourceKey)
						)
					);

				// Remove the Telemetry Managers
				runningAgentContext.getTaskSchedulingService().getTelemetryManagers().remove(resourceGroupKey);
			}
		}
	}

	/**
	 * Checks whether the global agent configuration has changed between the current and reloaded context.
	 * <p>
	 * If any of the high-level settings (like timeouts, directories, filters, etc.) differ, this method returns {@code true}.
	 * </p>
	 *
	 * @param runningConf current agent configuration
	 * @param newConf reloaded agent configuration
	 * @return {@code true} if global settings have changed, otherwise {@code false}
	 */
	public boolean globalConfigurationHasChanged(final AgentConfig runningConf, final AgentConfig newConf) {
		// CHECKSTYLE:OFF
		return (
			runningConf.getJobPoolSize() != newConf.getJobPoolSize() ||
			!Objects.equals(runningConf.getLoggerLevel(), newConf.getLoggerLevel()) ||
			!Objects.equals(runningConf.getOutputDirectory(), newConf.getOutputDirectory()) ||
			runningConf.getCollectPeriod() != newConf.getCollectPeriod() ||
			runningConf.getDiscoveryCycle() != newConf.getDiscoveryCycle() ||
			!Objects.equals(runningConf.getAlertingSystemConfig(), newConf.getAlertingSystemConfig()) ||
			runningConf.isSequential() != newConf.isSequential() ||
			runningConf.isEnableSelfMonitoring() != newConf.isEnableSelfMonitoring() ||
			runningConf.isResolveHostnameToFqdn() != newConf.isResolveHostnameToFqdn() ||
			!Objects.equals(runningConf.getMonitorFilters(), newConf.getMonitorFilters()) ||
			runningConf.getJobTimeout() != newConf.getJobTimeout() ||
			!Objects.equals(runningConf.getOtelCollector(), newConf.getOtelCollector()) ||
			!Objects.equals(runningConf.getOtelConfig(), newConf.getOtelConfig()) ||
			!Objects.equals(runningConf.getAttributes(), newConf.getAttributes()) ||
			!Objects.equals(runningConf.getMetrics(), newConf.getMetrics()) ||
			!Objects.equals(runningConf.getStateSetCompression(), newConf.getStateSetCompression()) ||
			!Objects.equals(runningConf.getPatchDirectory(), newConf.getPatchDirectory())
		);
		// CHECKSTYLE:ON
	}
}
