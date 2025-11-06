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

package org.metricshub.web.service;

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.AgentTelemetry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for building a light-weight hierarchy representation of
 * the configured/active resources from the AgentContext's telemetry managers.
 * This service also helps to browse the agent resource groups, resources,
 * connectors, and monitors in order
 * to report attributes and metrics for each level of the hierarchy.
 */
@Service
public class ExplorerService {

	/**
	 * Type for the "monitor" node.
	 */
	private static final String MONITOR_TYPE = "monitor";

	/**
	 * Type for the "agent" node.
	 */
	private static final String AGENT_TYPE = "agent";

	/**
	 * Type for the "resource" node.
	 */
	private static final String RESOURCE_TYPE = "resource";

	/**
	 * Key for the "connectors" container node.
	 */
	private static final String CONNECTORS_KEY = "connectors";

	/**
	 * Key for the "resource-group" container node.
	 */
	private static final String RESOURCE_GROUP_KEY = "resource-group";

	/**
	 * Type for the "connector" node.
	 */
	private static final String CONNECTOR_TYPE = "connector";

	/**
	 * Key for the "monitors" container node.
	 */
	private static final String MONITORS_KEY = "monitors";

	/**
	 * Key for the "resources" container node.
	 */
	private static final String RESOURCES_KEY = "resources";

	/**
	 * Key for the "resource-groups" container node.
	 */
	private static final String RESOURCE_GROUPS_KEY = "resource-groups";

	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new {@link ExplorerService} instance.
	 *
	 * @param agentContextHolder holder providing access to the current
	 *                           {@link org.metricshub.agent.context.AgentContext}
	 */
	public ExplorerService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Build the complete hierarchy tree under the current agent.
	 *
	 * @return root node describing the agent hierarchy
	 */
	public AgentTelemetry getHierarchy() {
		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, String> agentAttributes = agentContext.getAgentInfo().getAttributes();
		final String agentName = agentAttributes.getOrDefault(
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			agentAttributes.getOrDefault(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, "MetricsHub")
		);

		final AgentTelemetry root = AgentTelemetry.builder().name(agentName).type(AGENT_TYPE).build();

		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			telemetryManagers = Map.of();
		}

		// Resource Groups (excluding top-level group key)
		final AgentTelemetry resourceGroupsNode = AgentTelemetry
			.builder()
			.name(RESOURCE_GROUPS_KEY)
			.type(RESOURCE_GROUPS_KEY)
			.build();

		telemetryManagers
			.entrySet()
			.stream()
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> resourceGroupsNode.getChildren().add(buildResourceGroupNode(entry.getKey(), entry.getValue())));

		// Top-level Resources
		final Map<String, TelemetryManager> topLevelResources = telemetryManagers.getOrDefault(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			Map.of()
		);
		final AgentTelemetry topLevelResourcesNode = AgentTelemetry
			.builder()
			.name(RESOURCES_KEY)
			.type(RESOURCES_KEY)
			.build();
		buildResources(topLevelResourcesNode, topLevelResources);

		root.getChildren().add(resourceGroupsNode);
		root.getChildren().add(topLevelResourcesNode);
		return root;
	}

	/**
	 * Build the details for a single resource by name, including its connectors and
	 * monitor types.
	 *
	 * @param resourceName the configured resource key/name to locate
	 * @return a resource node with connectors and monitor types as children
	 * @throws org.springframework.web.server.ResponseStatusException (404) when the
	 *                                                                resource is
	 *                                                                not found or
	 *                                                                the name is
	 *                                                                blank
	 */
	public AgentTelemetry getResource(final String resourceName) {
		final var agentContext = agentContextHolder.getAgentContext();
		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		for (Entry<String, Map<String, TelemetryManager>> entry : telemetryManagers.entrySet()) {
			final Map<String, TelemetryManager> groupTms = entry.getValue();
			if (groupTms == null || groupTms.isEmpty()) {
				continue;
			}
			final TelemetryManager tm = groupTms.get(resourceName);
			if (tm != null) {
				return buildResourceNode(resourceName, tm);
			}
		}

		// Not found
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
	}

	/**
	 * Build the connectors container for a single resource by name.
	 * Returns a "connectors" node whose children are the connectors under the
	 * resource, each including its monitor types.
	 *
	 * @param resourceName the configured resource key/name to locate
	 * @return a connectors container node for the resource; if the resource has no
	 *         connectors, the container will be empty
	 * @throws org.springframework.web.server.ResponseStatusException (404) when the
	 *                                                                resource is
	 *                                                                not found or
	 *                                                                the name is
	 *                                                                blank
	 */
	public AgentTelemetry getResourceConnectors(final String resourceName) {
		final AgentTelemetry connectorsNode = AgentTelemetry.builder().name(CONNECTORS_KEY).type(CONNECTORS_KEY).build();
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		final AgentTelemetry resourceNode = getResource(resourceName);
		return resourceNode
			.getChildren()
			.stream()
			.filter(child -> CONNECTORS_KEY.equals(child.getType()))
			.findFirst()
			.orElse(connectorsNode);
	}

	/**
	 * Build the details for a single connector under a given resource by name.
	 * Returns a connector node with its "monitors" child container listing monitor
	 * types.
	 *
	 * @param resourceName  the resource key/name
	 * @param connectorName the connector id or display name
	 * @return the connector node with details
	 * @throws org.springframework.web.server.ResponseStatusException (404) when the
	 *                                                                resource is
	 *                                                                not found, the
	 *                                                                connector is
	 *                                                                not
	 *                                                                found, or
	 *                                                                either name is
	 *                                                                blank
	 */
	public AgentTelemetry getResourceConnector(final String resourceName, final String connectorName) {
		if (resourceName == null || resourceName.isBlank() || connectorName == null || connectorName.isBlank()) {
			throw new ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"Connector not found: " + connectorName + " for resource: " + resourceName
			);
		}
		final AgentTelemetry resourceNode = getResource(resourceName);
		final AgentTelemetry connectorsNode = resourceNode
			.getChildren()
			.stream()
			.filter(child -> CONNECTORS_KEY.equals(child.getType()))
			.findFirst()
			.orElse(null);
		if (connectorsNode == null) {
			throw new ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"Connector not found: " + connectorName + " for resource: " + resourceName
			);
		}
		return connectorsNode
			.getChildren()
			.stream()
			.filter(conn -> connectorName.equals(conn.getName()))
			.findFirst()
			.orElseThrow(() ->
				new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Connector not found: " + connectorName + " for resource: " + resourceName
				)
			);
	}

	/**
	 * Build the monitors container for a given connector under a resource.
	 * Returns a "monitors" node whose children are the monitor types available
	 * under the specified connector. Children are type="monitor" and name is the
	 * monitor type.
	 *
	 * @param resourceName  the resource key/name
	 * @param connectorName the connector id or display name
	 * @return a monitors container node; if no monitors exist, the container will
	 *         be empty
	 * @throws org.springframework.web.server.ResponseStatusException (404) when the
	 *                                                                resource or
	 *                                                                connector is
	 *                                                                not found
	 */
	public AgentTelemetry getResourceConnectorMonitors(final String resourceName, final String connectorName) {
		// Reuse existing connector building logic and extract its monitors container
		final AgentTelemetry connectorNode = getResourceConnector(resourceName, connectorName);
		return connectorNode
			.getChildren()
			.stream()
			.filter(child -> MONITORS_KEY.equals(child.getType()))
			.findFirst()
			.orElse(AgentTelemetry.builder().name(MONITORS_KEY).type(MONITORS_KEY).build());
	}

	/**
	 * Build the list of monitor instances for a given type under a specific
	 * connector and resource.
	 * Response is a flat list of AgentTelemetry nodes (no children) each carrying
	 * attributes and metrics.
	 *
	 * @param resourceName  the resource key/name
	 * @param connectorName the connector id or display name
	 * @param monitorType   the monitor type key (e.g., "cpu", "disk")
	 * @return list of monitor DTOs with attributes and metrics
	 * @throws org.springframework.web.server.ResponseStatusException (404) when the
	 *                                                                resource or
	 *                                                                connector is
	 *                                                                not found
	 */
	public java.util.List<AgentTelemetry> getResourceConnectorMonitorsByType(
		final String resourceName,
		final String connectorName,
		final String monitorType
	) {
		// Locate TelemetryManager for the resource (inline to avoid extra helpers)
		final var agentContext = agentContextHolder.getAgentContext();
		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		TelemetryManager tm = null;
		for (Entry<String, Map<String, TelemetryManager>> entry : telemetryManagers.entrySet()) {
			final Map<String, TelemetryManager> groupTms = entry.getValue();
			if (groupTms == null || groupTms.isEmpty()) {
				continue;
			}
			tm = groupTms.get(resourceName);
			if (tm != null) {
				break;
			}
		}
		if (tm == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		// Resolve connector id from connector monitors (inline)
		if (connectorName == null || connectorName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + connectorName);
		}
		final Map<String, Monitor> connectorMonitors = tm.findMonitorsByType(CONNECTOR.getKey());
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + connectorName);
		}
		String connectorId = null;
		for (Monitor m : connectorMonitors.values()) {
			final String id = m.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_ID, m.getId());
			final String name = m.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, id);
			if (connectorName.equals(name) || connectorName.equals(id)) {
				connectorId = id;
				break;
			}
		}
		if (connectorId == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + connectorName);
		}
		final String resolvedConnectorId = connectorId;

		final Map<String, Monitor> monitorsByType = tm.findMonitorsByType(monitorType);
		final java.util.List<AgentTelemetry> result = new ArrayList<>();
		if (monitorsByType == null || monitorsByType.isEmpty()) {
			return result;
		}

		monitorsByType
			.values()
			.stream()
			.filter(Objects::nonNull)
			.filter(m -> resolvedConnectorId.equals(m.getAttributes().get(MONITOR_ATTRIBUTE_CONNECTOR_ID)))
			.sorted((a, b) -> {
				final String an = a.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, a.getId());
				final String bn = b.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, b.getId());
				return an.compareToIgnoreCase(bn);
			})
			.forEach(m -> {
				final String name = m.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, m.getId());
				final AgentTelemetry at = AgentTelemetry.builder().name(name).type(MONITOR_TYPE).build();
				// copy attributes
				if (m.getAttributes() != null) {
					at.getAttributes().putAll(m.getAttributes());
				}
				// copy metrics as plain values
				if (m.getMetrics() != null) {
					m.getMetrics().forEach((k, v) -> at.getMetrics().put(k, v == null ? null : v.getValue()));
				}
				result.add(at);
			});

		return result;
	}

	/**
	 * Build a flat resources container listing all configured resources across
	 * all resource-groups (including top-level), each with its connectors and
	 * monitor types as children following the existing structure.
	 *
	 * @return a "resources" container node with all resources as children
	 */
	public AgentTelemetry getResources() {
		final var agentContext = agentContextHolder.getAgentContext();
		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			telemetryManagers = Map.of();
		}

		final AgentTelemetry resourcesNode = AgentTelemetry.builder().name(RESOURCES_KEY).type(RESOURCES_KEY).build();

		// Iterate by resource-group key for deterministic ordering, then by resource
		// key
		telemetryManagers
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> buildResources(resourcesNode, entry.getValue()));

		return resourcesNode;
	}

	/**
	 * Build a flat resource-groups container listing all configured resource groups
	 * (excluding the top-level virtual group), each with its nested resources
	 * container as children.
	 *
	 * @return a "resource-groups" container node with all groups as children
	 */
	public AgentTelemetry getResourceGroups() {
		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();

		final AgentTelemetry resourceGroupsNode = AgentTelemetry
			.builder()
			.name(RESOURCE_GROUPS_KEY)
			.type(RESOURCE_GROUPS_KEY)
			.build();

		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			return resourceGroupsNode;
		}

		telemetryManagers
			.entrySet()
			.stream()
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> resourceGroupsNode.getChildren().add(buildResourceGroupNode(entry.getKey(), entry.getValue())));

		return resourceGroupsNode;
	}

	/**
	 * Build the details for a single resource-group by name, including its nested
	 * resources container.
	 *
	 * @param groupName the resource-group key/name to locate
	 * @return a resource-group node with a "resources" container as child
	 * @throws org.springframework.web.server.ResponseStatusException (400) when the
	 *                                                                name is blank
	 *                                                                or (404) when
	 *                                                                the group is
	 *                                                                not found
	 */
	public AgentTelemetry getResourceGroup(final String groupName) {
		if (groupName == null || groupName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource group not found: " + groupName);
		}

		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource group not found: " + groupName);
		}

		final Map<String, TelemetryManager> groupTms = telemetryManagers.get(groupName);
		if (groupTms == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource group not found: " + groupName);
		}

		return buildResourceGroupNode(groupName, groupTms);
	}

	/**
	 * Builds a resource-group node along with its nested {@code resources}
	 * container.
	 *
	 * @param groupName the display name/key of the resource group
	 * @param groupTms  the map of resource keys to their {@link TelemetryManager}
	 *                  instances for this group
	 * @return a {@link AgentTelemetry} representing the resource-group subtree
	 */
	private static AgentTelemetry buildResourceGroupNode(
		final String groupName,
		final Map<String, TelemetryManager> groupTms
	) {
		final AgentTelemetry groupNode = AgentTelemetry.builder().name(groupName).type(RESOURCE_GROUP_KEY).build();
		final AgentTelemetry resourcesContainer = AgentTelemetry.builder().name(RESOURCES_KEY).type(RESOURCES_KEY).build();
		buildResources(resourcesContainer, groupTms);
		groupNode.getChildren().add(resourcesContainer);
		return groupNode;
	}

	/**
	 * Populates the provided {@code resources} container with resource nodes built
	 * from the given map.
	 *
	 * @param resourcesContainer the container DTO to which resource children will
	 *                           be appended
	 * @param tms                a map of resource key to {@link TelemetryManager}
	 */
	private static void buildResources(final AgentTelemetry resourcesContainer, final Map<String, TelemetryManager> tms) {
		if (tms == null || tms.isEmpty()) {
			return;
		}
		tms
			.entrySet()
			.stream()
			.sorted(Entry.comparingByKey())
			.forEach((Entry<String, TelemetryManager> entry) -> {
				final String resourceKey = entry.getKey();
				final TelemetryManager tm = entry.getValue();
				resourcesContainer.getChildren().add(buildResourceNode(resourceKey, tm));
			});
	}

	/**
	 * Builds a single resource node including its connectors subtree.
	 *
	 * @param resourceKey the resource identifier (as configured)
	 * @param tm          the {@link TelemetryManager} for this resource
	 * @return a {@link AgentTelemetry} representing the resource subtree
	 */
	private static AgentTelemetry buildResourceNode(final String resourceKey, final TelemetryManager tm) {
		final AgentTelemetry resourceNode = AgentTelemetry.builder().name(resourceKey).type(RESOURCE_TYPE).build();

		final AgentTelemetry connectorsContainer = AgentTelemetry
			.builder()
			.name(CONNECTORS_KEY)
			.type(CONNECTORS_KEY)
			.build();

		buildConnectors(connectorsContainer, tm);

		resourceNode.getChildren().add(connectorsContainer);

		return resourceNode;
	}

	/**
	 * Populates the provided {@code connectors} container with connector nodes and
	 * their monitor type lists.
	 * <p>
	 * Monitor types are grouped by {@code connector_id} and exclude {@code host}
	 * and
	 * {@code connector} monitor types.
	 * </p>
	 *
	 * @param connectorsContainer the container DTO to which connector children will
	 *                            be appended
	 * @param tm                  the {@link TelemetryManager} providing monitors
	 *                            for this resource
	 */
	private static void buildConnectors(final AgentTelemetry connectorsContainer, final TelemetryManager tm) {
		final Map<String, Monitor> connectorMonitors = tm.findMonitorsByType(CONNECTOR.getKey());
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			return;
		}

		// Collect monitors by connector-id to later build monitor type lists per
		// connector
		final Map<String, Set<String>> connectorIdToMonitorTypes = groupMonitorTypesByConnectorId(tm, connectorMonitors);

		connectorMonitors
			.values()
			.stream()
			.sorted((Monitor a, Monitor b) -> {
				final String an = a.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, a.getId());
				final String bn = b.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, b.getId());
				return an.compareToIgnoreCase(bn);
			})
			.forEach((Monitor connectorMonitor) ->
				buildConnectorNode(connectorsContainer, connectorIdToMonitorTypes, connectorMonitor)
			);
	}

	/**
	 * Builds a single connector node including its monitor types list.
	 *
	 * @param connectorsContainer       The "connectors" container node.
	 * @param connectorIdToMonitorTypes The map of connector IDs to their monitor
	 *                                  types.
	 * @param connectorMonitor          The connector monitor to build the node for.
	 */
	private static void buildConnectorNode(
		final AgentTelemetry connectorsContainer,
		final Map<String, Set<String>> connectorIdToMonitorTypes,
		Monitor connectorMonitor
	) {
		final String connectorId = connectorMonitor
			.getAttributes()
			.getOrDefault(MONITOR_ATTRIBUTE_ID, connectorMonitor.getId());
		final String connectorName = connectorMonitor.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, connectorId);

		final AgentTelemetry connectorNode = AgentTelemetry.builder().name(connectorName).type(CONNECTOR_TYPE).build();

		final AgentTelemetry monitorsContainer = AgentTelemetry.builder().name(MONITORS_KEY).type(MONITORS_KEY).build();
		final Set<String> monitorTypes = connectorIdToMonitorTypes.getOrDefault(connectorId, Set.of());
		monitorTypes
			.stream()
			.sorted(String::compareToIgnoreCase)
			.forEach(type ->
				monitorsContainer.getChildren().add(AgentTelemetry.builder().name(type).type(MONITOR_TYPE).build())
			);

		connectorNode.getChildren().add(monitorsContainer);
		connectorsContainer.getChildren().add(connectorNode);
	}

	/**
	 * Computes the set of monitor types per connector identifier present in the
	 * given {@link TelemetryManager}.
	 * <p>
	 * Excludes monitors of type {@code host} and {@code connector}. Ensures all
	 * connector monitors are represented even if they have no matching monitor
	 * types.
	 * </p>
	 *
	 * @param tm                the {@link TelemetryManager} to inspect
	 * @param connectorMonitors map of connector monitors to consider
	 * @return a map of {@code connector_id} to the set of monitor types
	 */
	private static Map<String, Set<String>> groupMonitorTypesByConnectorId(
		final TelemetryManager tm,
		final Map<String, Monitor> connectorMonitors
	) {
		final Map<String, Set<String>> result = new HashMap<>();

		if (tm.getMonitors() == null || tm.getMonitors().isEmpty()) {
			return result;
		}

		// Flatten all monitors and group types by connector-id, excluding host and
		// connector monitors
		tm
			.getMonitors()
			.values()
			.stream()
			.filter(Objects::nonNull)
			.flatMap(map -> map.values().stream())
			.filter(Objects::nonNull)
			.filter(m -> !HOST.getKey().equalsIgnoreCase(m.getType()))
			.filter(m -> !CONNECTOR.getKey().equalsIgnoreCase(m.getType()))
			.forEach((Monitor monitor) -> {
				final String connectorId = monitor.getAttributes().get(MONITOR_ATTRIBUTE_CONNECTOR_ID);
				if (connectorId == null || connectorId.isBlank()) {
					return;
				}
				result.computeIfAbsent(connectorId, k -> new HashSet<>()).add(monitor.getType());
			});

		// Ensure that each listed connector-id exists, even if no non-connector/host
		// monitor types were found
		if (connectorMonitors != null && !connectorMonitors.isEmpty()) {
			final Set<String> connectorIds = connectorMonitors
				.values()
				.stream()
				.map(m -> m.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_ID, m.getId()))
				.collect(Collectors.toSet());
			connectorIds.forEach(id -> result.computeIfAbsent(id, k -> new HashSet<>()));
		}

		return result;
	}
}
