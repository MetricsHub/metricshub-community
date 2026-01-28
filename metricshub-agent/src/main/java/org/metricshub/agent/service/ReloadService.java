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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.IntSupplier;
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
import org.metricshub.web.MetricsHubAgentServer;

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

	private static final int UNLIMITED_RESOURCE_QUOTA = -1;

	/**
	 * The running Agent context
	 */
	private AgentContext runningAgentContext;

	/**
	 * The Agent Context that have been created after that the configuration file(s) have been modified.
	 */
	private AgentContext reloadedAgentContext;

	/**
	 * Callback to execute before restarting the agent
	 */
	@Builder.Default
	private Runnable beforeRestartCallback = () -> {};

	/**
	 * Callback to execute after global restarting of the agent
	 */
	@Builder.Default
	private Runnable afterGlobalRestartCallback = () -> {};

	/**
	 * Callback to execute before scheduling resources
	 */
	@Builder.Default
	private Runnable beforeResourcesUpdateCallback = () -> {};

	/**
	 * Int Supplier to store and retrieve the maximum number of resources to monitor
	 */
	@Builder.Default
	private IntSupplier resourceQuotaSupplier = () -> UNLIMITED_RESOURCE_QUOTA;

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

		// Execute the callback
		beforeRestartCallback.run();

		// Compare global configurations
		if (globalConfigurationHasChanged(runningAgentContext.getAgentConfig(), reloadedAgentContext.getAgentConfig())) {
			log.info("Global configuration has changed. Restarting MetricsHub Agent...");

			runningAgentContext.getTaskSchedulingService().stop();

			// ServiceLoader responsibility is to manage the scheduler life cycle
			// OTEL Collector process is not managed by the current ServiceLoader
			// And should have its own life cycle hot reload management.
			// Accordingly, we need to transfer the running OTEL Collector process to the reloaded context
			reloadedAgentContext.setOtelCollectorProcessService(runningAgentContext.getOtelCollectorProcessService());

			// Point the running context to the reloaded one
			runningAgentContext = reloadedAgentContext;
			runningAgentContext.getTaskSchedulingService().start();

			// Update the MetricsHub Agent Server with the new context
			MetricsHubAgentServer.updateAgentContext(runningAgentContext);

			// Execute the Global restart callback
			afterGlobalRestartCallback.run();

			log.info("MetricsHub Agent restarted with updated global configuration.");
			return;
		}

		// Execute the restart callback
		beforeResourcesUpdateCallback.run();

		// Create a map that will be used to schedule new resources
		final Map<String, Map<String, ResourceConfig>> resourcesToSchedule = new HashMap<>();

		// Compare top level resources
		log.info("Comparing and applying changes to top-level resources...");

		resourcesToSchedule.put(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			compareResources(
				TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
				runningAgentContext.getAgentConfig().getResources(),
				reloadedAgentContext.getAgentConfig().getResources()
			)
		);

		// Compare Resource Groups
		log.info("Comparing and applying changes to resource groups...");
		resourcesToSchedule.putAll(
			compareResourceGroups(
				runningAgentContext.getAgentConfig().getResourceGroups(),
				reloadedAgentContext.getAgentConfig().getResourceGroups()
			)
		);

		// Schedule the additional resources
		scheduleResources(resourcesToSchedule, resourceQuotaSupplier.getAsInt());
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

	/**
	 * Compares the current set of running resources with a newly loaded set of resources for a given resource group,
	 * and determines the changes that need to be applied. This includes detecting resources that were added, updated,
	 * or removed, and updating the scheduling service accordingly.
	 * <p>
	 * - Resources present in {@code runningResources} but missing in {@code newResources} are stopped and removed.<br>
	 * - Resources present in {@code newResources} but missing in {@code runningResources} are added and scheduled.<br>
	 * - Resources present in both but with differences are updated and rescheduled.
	 * </p>
	 *
	 * @param resourceGroupKey the unique key identifying the resource group (or the top-level group)
	 * @param runningResources the map of currently running resource configurations (mutable and updated by this method)
	 * @param newResources     the map of newly loaded resource configurations
	 * @return a map of resources that need to be (re)scheduled, where the key is the resource ID and the value is the updated {@link ResourceConfig}
	 */
	public Map<String, ResourceConfig> compareResources(
		final String resourceGroupKey,
		final Map<String, ResourceConfig> runningResources,
		final Map<String, ResourceConfig> newResources
	) {
		final Set<String> allResources = Stream
			.concat(runningResources.keySet().stream(), newResources.keySet().stream())
			.collect(Collectors.toSet());

		// Creating the map that will contain all the resources to schedule
		final Map<String, ResourceConfig> resourcesToSchedule = new HashMap<>();

		for (final String resourceKey : allResources) {
			if (!newResources.containsKey(resourceKey)) {
				// Resource removed
				log.info("Resource '{}' removed from group '{}'. Stopping and cleaning up...", resourceKey, resourceGroupKey);

				// Remove the resource configuration
				runningResources.remove(resourceKey);

				// Stop the resource schedule and delete the its Telemetry Manager
				removeResourceFromTaskSchedulingService(
					resourceGroupKey,
					resourceKey,
					METRICSHUB_RESOURCE_KEY_FORMAT.formatted(resourceGroupKey, resourceKey)
				);
			} else if (!runningResources.containsKey(resourceKey)) {
				// Resource added
				log.info("New resource '{}' detected in group '{}'. Scheduling...", resourceKey, resourceGroupKey);

				// Add the resource configuration
				runningResources.put(resourceKey, newResources.get(resourceKey));

				// Add the resource to the scheduling map
				resourcesToSchedule.put(resourceKey, newResources.get(resourceKey));
			} else {
				final ResourceConfig runningResource = runningResources.get(resourceKey);
				final ResourceConfig newResource = newResources.get(resourceKey);

				if (!Objects.equals(runningResource, newResource)) {
					// Resource modified
					log.info("Resource '{}' in group '{}' has changed. Updating...", resourceKey, resourceGroupKey);

					// Update the resource configuration
					runningResources.put(resourceKey, newResource);

					// Stop the resource schedule and delete its Telemetry Manager
					removeResourceFromTaskSchedulingService(
						resourceGroupKey,
						resourceKey,
						METRICSHUB_RESOURCE_KEY_FORMAT.formatted(resourceGroupKey, resourceKey)
					);

					// Add the resource to the scheduling map
					resourcesToSchedule.put(resourceKey, newResource);
				}
			}
		}
		return resourcesToSchedule;
	}

	/**
	 * Compares the current set of running resource groups with a newly loaded set of resource groups,
	 * and determines the changes that need to be applied. Each resource group is processed independently,
	 * and the necessary additions, removals, or updates are applied to the running context.
	 *
	 * @param runningResourceGroups the current resource groups in the running context (mutable and updated by this method)
	 * @param newResourceGroups     the newly loaded resource groups to compare against
	 * @return a map where each key is a resource group ID, and the value is a map of resources within that group
	 *         that need to be (re)scheduled
	 */
	public Map<String, Map<String, ResourceConfig>> compareResourceGroups(
		final Map<String, ResourceGroupConfig> runningResourceGroups,
		final Map<String, ResourceGroupConfig> newResourceGroups
	) {
		final Set<String> allResourceGroups = Stream
			.concat(runningResourceGroups.keySet().stream(), newResourceGroups.keySet().stream())
			.collect(Collectors.toSet());

		// Create a map of resources to schedule
		final Map<String, Map<String, ResourceConfig>> resourcesToSchedule = new HashMap<>();

		for (final String resourceGroupKey : allResourceGroups) {
			final ResourceGroupConfig runningGroup = runningResourceGroups.get(resourceGroupKey);
			final ResourceGroupConfig newGroup = newResourceGroups.get(resourceGroupKey);

			if (newGroup == null) {
				// Handle removed group
				handleRemovedGroup(resourceGroupKey, runningGroup);
			} else if (runningGroup == null) {
				// Handle added group
				// Add the resource group configuration to the running agent context
				handleAddedGroup(resourceGroupKey, newGroup);

				// Add all the resource group resources to the scheduling map
				resourcesToSchedule.put(resourceGroupKey, newGroup.getResources());
			} else if (resourceGroupGlobalConfigHasChanged(runningGroup, newGroup)) {
				// Handle global configuration changed
				// Update all the resource group configurations
				// Cancel then remove all the related schedules and telemetry managers
				handleModifiedGroup(resourceGroupKey, runningGroup, newGroup);

				// Add all the resource group resources to the scheduling map
				resourcesToSchedule.put(resourceGroupKey, newGroup.getResources());
			} else {
				// Compare resources when group exists in both and hasn't globally changed
				resourcesToSchedule.put(
					resourceGroupKey,
					compareResources(resourceGroupKey, runningGroup.getResources(), newGroup.getResources())
				);
			}
		}

		return resourcesToSchedule;
	}

	/**
	 * Handles the removal of a resource group from the running agent context.
	 * <p>
	 * This method performs the following actions:
	 * <ul>
	 *   <li>Removes the resource group configuration from the agent's configuration.</li>
	 *   <li>Cancels and removes the resource group-level schedule (if any).</li>
	 *   <li>Stops and removes all scheduled resources within the group and their associated telemetry managers.</li>
	 *   <li>Removes the telemetry manager entry for the resource group itself.</li>
	 * </ul>
	 * </p>
	 *
	 * @param resourceGroupKey    the unique identifier of the resource group to remove
	 * @param resourceGroupConfig the configuration of the resource group being removed
	 */
	private void handleRemovedGroup(String resourceGroupKey, ResourceGroupConfig resourceGroupConfig) {
		log.info("The Resource Group '{}' is removed from the configuration.", resourceGroupKey);

		// Remove the resource group configuration from the agent
		runningAgentContext.getAgentConfig().getResourceGroups().remove(resourceGroupKey);

		// cancel and remove the resource group schedule
		Optional
			.ofNullable(
				runningAgentContext
					.getTaskSchedulingService()
					.getSchedules()
					.remove(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted(resourceGroupKey))
			)
			.ifPresent(s -> s.cancel(true));

		// remove all the resource group resources from the task scheduling service
		resourceGroupConfig
			.getResources()
			.forEach((resourceKey, resourceConfig) ->
				removeResourceFromTaskSchedulingService(
					resourceGroupKey,
					resourceKey,
					METRICSHUB_RESOURCE_KEY_FORMAT.formatted(resourceGroupKey, resourceKey)
				)
			);

		// Remove the resource group telemetry managers
		runningAgentContext.getTaskSchedulingService().getTelemetryManagers().remove(resourceGroupKey);
	}

	/**
	 * Handles the addition of a new resource group to the running agent context.
	 * <p>
	 * This method simply updates the agent's configuration by adding the new resource group
	 * and its associated configuration.
	 * </p>
	 *
	 * @param resourceGroupKey   the unique identifier of the resource group to add
	 * @param resourceGroup the configuration of the new resource group
	 */
	private void handleAddedGroup(String resourceGroupKey, ResourceGroupConfig resourceGroup) {
		log.info("The Resource Group '{}' is added to the configuration.", resourceGroupKey);

		// Add the resource group configuration to the running agent config.
		runningAgentContext.getAgentConfig().getResourceGroups().put(resourceGroupKey, resourceGroup);
	}

	/**
	 * Handles global configuration changes for an existing resource group.
	 * <p>
	 * This method restarts the specified resource group by:
	 * <ul>
	 *   <li>Updating the agent's configuration with the new resource group configuration.</li>
	 *   <li>Removing associated telemetry managers.</li>
	 *   <li>Cancelling and removing the resource group-level schedule.</li>
	 *   <li>Removing all individual resource schedules and telemetry managers associated with the old group.</li>
	 * </ul>
	 * After this method, the updated group will be ready to be rescheduled.
	 * </p>
	 *
	 * @param resourceGroupKey the unique identifier of the resource group being updated
	 * @param oldGroup         the previous configuration of the resource group
	 * @param newGroup         the updated configuration of the resource group
	 */
	private void handleModifiedGroup(
		String resourceGroupKey,
		ResourceGroupConfig oldGroup,
		ResourceGroupConfig newGroup
	) {
		log.info("Resource Group '{}' has global configuration changes. Restarting group...", resourceGroupKey);

		// Update the resource group configuration in the agent context
		runningAgentContext.getAgentConfig().getResourceGroups().put(resourceGroupKey, newGroup);

		// Remove the resource group telemetry Managers
		runningAgentContext.getTelemetryManagers().remove(resourceGroupKey);

		// cancel and remove the resource group schedule
		final String resourceGroupSchedulingName = METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted(resourceGroupKey);
		Optional
			.ofNullable(runningAgentContext.getTaskSchedulingService().getSchedules().remove(resourceGroupSchedulingName))
			.ifPresent(s -> {
				s.cancel(true);
				log.info("Stopping and removing scheduled resource group '{}'.", resourceGroupSchedulingName);
			});

		// remove all the resource group resources from the task scheduling service
		oldGroup
			.getResources()
			.keySet()
			.forEach(resourceKey ->
				removeResourceFromTaskSchedulingService(
					resourceGroupKey,
					resourceKey,
					METRICSHUB_RESOURCE_KEY_FORMAT.formatted(resourceGroupKey, resourceKey)
				)
			);
	}

	/**
	 * Determines if the global configuration of a resource group has changed between
	 * the running and newly loaded configurations.
	 * <p>
	 * This method compares global-level settings of the resource group such as logging level,
	 * output directory, collection intervals, filters, attributes, and other metadata.
	 * Any difference in these fields indicates that the group requires a restart.
	 * </p>
	 *
	 * @param runningResourceGroupConfig the current (running) resource group configuration
	 * @param newResourceGroupConfig     the newly loaded resource group configuration
	 * @return {@code true} if any global settings differ, otherwise {@code false}
	 */
	public boolean resourceGroupGlobalConfigHasChanged(
		final ResourceGroupConfig runningResourceGroupConfig,
		final ResourceGroupConfig newResourceGroupConfig
	) {
		// CHECKSTYLE:OFF
		return (
			!Objects.equals(runningResourceGroupConfig.getLoggerLevel(), newResourceGroupConfig.getLoggerLevel()) ||
			!Objects.equals(runningResourceGroupConfig.getOutputDirectory(), newResourceGroupConfig.getOutputDirectory()) ||
			!Objects.equals(runningResourceGroupConfig.getCollectPeriod(), newResourceGroupConfig.getCollectPeriod()) ||
			!Objects.equals(runningResourceGroupConfig.getDiscoveryCycle(), newResourceGroupConfig.getDiscoveryCycle()) ||
			!Objects.equals(
				runningResourceGroupConfig.getAlertingSystemConfig(),
				newResourceGroupConfig.getAlertingSystemConfig()
			) ||
			!Objects.equals(runningResourceGroupConfig.getSequential(), newResourceGroupConfig.getSequential()) ||
			!Objects.equals(
				runningResourceGroupConfig.getEnableSelfMonitoring(),
				newResourceGroupConfig.getEnableSelfMonitoring()
			) ||
			!Objects.equals(
				runningResourceGroupConfig.getResolveHostnameToFqdn(),
				newResourceGroupConfig.getResolveHostnameToFqdn()
			) ||
			!Objects.equals(runningResourceGroupConfig.getMonitorFilters(), newResourceGroupConfig.getMonitorFilters()) ||
			!Objects.equals(runningResourceGroupConfig.getJobTimeout(), newResourceGroupConfig.getJobTimeout()) ||
			!Objects.equals(runningResourceGroupConfig.getAttributes(), newResourceGroupConfig.getAttributes()) ||
			!Objects.equals(runningResourceGroupConfig.getMetrics(), newResourceGroupConfig.getMetrics()) ||
			!Objects.equals(
				runningResourceGroupConfig.getStateSetCompression(),
				newResourceGroupConfig.getStateSetCompression()
			)
		);
		// CHECKSTYLE:ON
	}

	/**
	 * Adds or updates the {@link TelemetryManager} instance for a specific resource in the
	 * running agent context, using the telemetry manager from the reloaded agent context.
	 * <p>
	 * If the resource group does not exist in the current telemetry structure,
	 * it is initialized before adding the resource's telemetry manager.
	 * </p>
	 *
	 * @param resourceGroupKey the key identifying the resource group the resource belongs to
	 * @param resourceKey      the unique key of the resource whose telemetry manager is to be added or updated
	 */
	public void addOrUpdateTelemetryManagerInTaskSchedulingService(
		final String resourceGroupKey,
		final String resourceKey
	) {
		final Map<String, Map<String, TelemetryManager>> newTelemetryManagers = reloadedAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, Map<String, TelemetryManager>> oldTelemetryManagers = runningAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers();

		// Add the TelemetryManager to the resource
		oldTelemetryManagers
			.computeIfAbsent(resourceGroupKey, key -> new HashMap<>())
			.put(resourceKey, newTelemetryManagers.get(resourceGroupKey).get(resourceKey));
	}

	/**
	 * Stops and removes a scheduled resource along with its associated {@link TelemetryManager}.
	 * <p>
	 * This method cancels the resource's scheduled task (if present) and removes it from
	 * the schedules map. It also removes the resource's telemetry manager from the
	 * {@link TaskSchedulingService}. If the resource group has no remaining telemetry managers,
	 * the group itself is removed from the telemetry map.
	 * </p>
	 *
	 * @param resourceGroupKey       the key identifying the resource group the resource belongs to
	 * @param resourceKey            the unique identifier of the resource to remove
	 * @param resourceSchedulingName the full internal name used to schedule the resource
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

		// Remove Telemetry Manager from task scheduling service, if the map still exists
		runningAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers()
			.computeIfPresent(
				resourceGroupKey,
				(key, resourceMap) -> {
					resourceMap.remove(resourceKey);
					// Keep the group-level map unless empty
					return resourceMap.isEmpty() ? null : resourceMap;
				}
			);
	}

	/**
	 * Schedules the given resources for monitoring, respecting the provided quota.
	 * <p>
	 * Each resource is either scheduled or, if the maximum number of allowed resources
	 * (quota) is reached, removed from the configuration. Resource groups with at least
	 * one scheduled resource are also scheduled.
	 * </p>
	 *
	 * @param resourcesToSchedule a map of resource group keys and their resources to schedule
	 * @param quota the maximum number of resources allowed; use {@link #UNLIMITED_RESOURCE_QUOTA} for no limit
	 */
	public void scheduleResources(
		final Map<String, Map<String, ResourceConfig>> resourcesToSchedule,
		final Integer quota
	) {
		// Calculate the number of Telemetry Managers in the task scheduling service.
		int currentQuota = runningAgentContext
			.getTaskSchedulingService()
			.getTelemetryManagers()
			.values()
			.stream()
			.mapToInt(Map::size)
			.sum();

		// Store resources to delete later
		final Map<String, Set<String>> resourcesToRemove = new HashMap<>();

		// The set of resource groups that needs to be scheduled
		final Set<String> scheduledResourceGroups = new HashSet<>();

		for (Map.Entry<String, Map<String, ResourceConfig>> groupEntry : resourcesToSchedule.entrySet()) {
			// Loop over each group and delegate scheduling
			currentQuota =
				processResourceGroup(
					groupEntry.getKey(),
					groupEntry.getValue(),
					quota,
					currentQuota,
					resourcesToRemove,
					scheduledResourceGroups
				);
		}

		// Schedule resource groups
		scheduledResourceGroups.forEach(resourceGroupKey ->
			runningAgentContext
				.getTaskSchedulingService()
				.scheduleResourceGroup(
					resourceGroupKey,
					runningAgentContext.getAgentConfig().getResourceGroups().get(resourceGroupKey)
				)
		);

		// Now remove only the unscheduled resources
		final AgentConfig agentConfig = runningAgentContext.getAgentConfig();
		for (Map.Entry<String, Set<String>> entry : resourcesToRemove.entrySet()) {
			final String groupKey = entry.getKey();
			final Set<String> resourceKeys = entry.getValue();

			if (groupKey.equals(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY)) {
				resourceKeys.forEach(agentConfig.getResources()::remove);
			} else {
				final Map<String, ResourceConfig> groupResources = agentConfig.getResourceGroups().get(groupKey).getResources();
				resourceKeys.forEach(groupResources::remove);
			}
		}
	}

	/**
	 * Processes a single resource group by scheduling its resources or marking them for removal if the quota is exceeded.
	 * <p>
	 * Each resource in the group is evaluated:
	 * <ul>
	 *     <li>If the resource quota has not been reached (or quota is unlimited), the resource is scheduled, and the group's
	 *     key is added to {@code scheduledResourceGroups}.</li>
	 *     <li>If the resource quota is exceeded, the resource is marked for removal by adding it to {@code resourcesToRemove}.</li>
	 * </ul>
	 * </p>
	 *
	 * @param resourceGroupKey        the key identifying the resource group being processed
	 * @param resourceGroup           the resources belonging to the group to process
	 * @param quota                   the maximum number of resources allowed to be scheduled (use {@code UNLIMITED_RESOURCE_QUOTA} for unlimited)
	 * @param currentQuota            the current number of resources already scheduled
	 * @param resourcesToRemove       a map of resources that could not be scheduled (will be updated by this method)
	 * @param scheduledResourceGroups a set of resource groups that contain at least one successfully scheduled resource (will be updated by this method)
	 * @return the updated count of scheduled resources after processing the group
	 */
	private int processResourceGroup(
		final String resourceGroupKey,
		final Map<String, ResourceConfig> resourceGroup,
		final Integer quota,
		int currentQuota,
		final Map<String, Set<String>> resourcesToRemove,
		final Set<String> scheduledResourceGroups
	) {
		for (Map.Entry<String, ResourceConfig> resourceEntry : resourceGroup.entrySet()) {
			final String resourceKey = resourceEntry.getKey();
			final ResourceConfig resource = resourceEntry.getValue();

			// Schedule resource if the quota isn't exceeded
			if (quota == UNLIMITED_RESOURCE_QUOTA || currentQuota < quota) {
				// Scheduling the resource as the quota is respected
				// Schedule the resource and add its telemetry manager
				scheduleResource(resourceGroupKey, resourceKey, resource);

				// increment the current quota
				currentQuota++;

				// Add the resource group key to the resource group map to schedule
				if (!resourceGroupKey.equals(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY)) {
					scheduledResourceGroups.add(resourceGroupKey);
				}
			} else {
				// Quota exceeded, deleting the resource configuration
				log.warn("Maximum number of resources '{}' reached. Deleting '{}' resource configuration", quota, resourceKey);

				// add the resource to the resourcesToRemove map.
				resourcesToRemove.computeIfAbsent(resourceGroupKey, k -> new HashSet<>()).add(resourceKey);
			}
		}

		return currentQuota;
	}

	/**
	 * Schedules a single resource for monitoring and ensures its {@link TelemetryManager} is updated.
	 *
	 * This method performs two main tasks:
	 * <ul>
	 *     <li>Updates or adds the resource's telemetry manager in the task scheduling service.</li>
	 *     <li>Schedules the resource for periodic monitoring based on its configuration.</li>
	 * </ul>
	 *
	 * @param resourceGroupKey the key of the resource group the resource belongs to
	 * @param resourceKey      the unique identifier of the resource to schedule
	 * @param resourceConfig   the {@link ResourceConfig} containing the configuration for the resource
	 */
	public void scheduleResource(
		final String resourceGroupKey,
		final String resourceKey,
		final ResourceConfig resourceConfig
	) {
		// Add the Resource Telemetry Manager
		addOrUpdateTelemetryManagerInTaskSchedulingService(resourceGroupKey, resourceKey);

		// Schedule the Resource
		runningAgentContext.getTaskSchedulingService().scheduleResource(resourceGroupKey, resourceKey, resourceConfig);
	}
}
