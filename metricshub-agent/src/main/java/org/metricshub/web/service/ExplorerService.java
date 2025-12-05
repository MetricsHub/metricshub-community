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

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AgentTelemetry;
import org.metricshub.web.dto.telemetry.ConnectorTelemetry;
import org.metricshub.web.dto.telemetry.InstanceTelemetry;
import org.metricshub.web.dto.telemetry.MonitorTypeTelemetry;
import org.metricshub.web.dto.telemetry.ResourceGroupTelemetry;
import org.metricshub.web.dto.telemetry.ResourceTelemetry;
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

	// No more virtual container node types: starting from resource-groups,
	// each node has a single typed child collection.

	private AgentContextHolder agentContextHolder;

	private SearchService searchService;

	/**
	 * Creates a new ExplorerService instance.
	 *
	 * @param agentContextHolder holder providing access to the current
	 *                           {@link org.metricshub.agent.context.AgentContext}
	 * @param searchService      service that performs fuzzy searches across the
	 */
	public ExplorerService(final AgentContextHolder agentContextHolder, final SearchService searchService) {
		this.agentContextHolder = agentContextHolder;
		this.searchService = searchService;
	}

	/**
	 * Builds the complete hierarchy tree under the current agent.
	 *
	 * @return root node describing the agent hierarchy
	 */
	public AgentTelemetry getHierarchy() {
		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, String> agentAttributes = agentContext.getAgentInfo().getAttributes();
		final String agentName = agentAttributes.getOrDefault(
			AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY,
			agentAttributes.getOrDefault(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY, "MetricsHub")
		);

		final AgentTelemetry root = AgentTelemetry.builder().name(agentName).attributes(agentAttributes).build();

		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			telemetryManagers = Map.of();
		}

		// Resource Groups (excluding top-level group key)
		var resourceGroups = telemetryManagers
			.entrySet()
			.stream()
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.map((Entry<String, Map<String, TelemetryManager>> entry) ->
				buildResourceGroupNode(entry.getKey(), entry.getValue())
			)
			.toList();

		root.getResourceGroups().addAll(resourceGroups);

		// Top-level Resources (directly under the agent)
		final Map<String, TelemetryManager> topLevelResources = telemetryManagers.getOrDefault(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			Map.of()
		);
		root.getResources().addAll(buildResources(topLevelResources));

		return root;
	}

	/**
	 * Builds a resource-group node along with its nested {@code resources}
	 * container.
	 *
	 * @param groupName the display name/key of the resource group
	 * @param groupTms  the map of resource keys to their {@link TelemetryManager}
	 *                  instances for this group
	 * @return a {@link ResourceGroupTelemetry} representing the resource-group
	 *         subtree
	 */
	private static ResourceGroupTelemetry buildResourceGroupNode(
		final String groupName,
		final Map<String, TelemetryManager> groupTms
	) {
		final Map<String, String> groupAttributes = new HashMap<>();
		final ResourceGroupTelemetry groupNode = ResourceGroupTelemetry
			.builder()
			.name(groupName)
			.attributes(groupAttributes)
			.build();

		groupNode.getResources().addAll(buildResources(groupTms));

		// Optionally, aggregate resource metrics (sum/avg/etc.)
		groupNode
			.getResources()
			.forEach(resource ->
				resource
					.getMetrics()
					.forEach((k, v) -> {
						// implement aggregation logic here
					})
			);

		return groupNode;
	}

	/**
	 * Overload that also propagates a resource-group key onto each resource node
	 * when provided.
	 *
	 * @param resourcesTarget the target list to which resource children will be
	 *                        appended
	 * @param tms             a map of resource key to {@link TelemetryManager}
	 * @return list of built {@link ResourceTelemetry} nodes
	 */
	private static List<ResourceTelemetry> buildResources(final Map<String, TelemetryManager> tms) {
		if (tms == null || tms.isEmpty()) {
			return new ArrayList<>();
		}
		return tms
			.entrySet()
			.stream()
			.sorted(Entry.comparingByKey())
			.map((Entry<String, TelemetryManager> entry) -> {
				final String resourceKey = entry.getKey();
				final TelemetryManager tm = entry.getValue();

				final Map<String, String> attributes = new HashMap<>();
				final Map<String, Object> metrics = new HashMap<>();

				if (tm != null) {
					final var endpointHost = tm.getEndpointHostMonitor();
					if (endpointHost != null) {
						if (endpointHost.getAttributes() != null) {
							attributes.putAll(endpointHost.getAttributes());
						}
						if (endpointHost.getMetrics() != null) {
							endpointHost.getMetrics().forEach((k, v) -> metrics.put(k, getMetricValue(v)));
						}
					}
				}

				return ResourceTelemetry.builder().name(resourceKey).attributes(attributes).metrics(metrics).build();
			})
			.toList();
	}

	/**
	 * Returns a full resource subtree including attributes and metrics.
	 *
	 * <p>
	 * When {@code groupedOnly} is {@code true}, only searches in resource groups
	 * (excluding top-level). When {@code false}, searches top-level first and then
	 * resource groups. If a specific {@code groupKey} is provided, it is used to
	 * narrow the grouped search.
	 * </p>
	 *
	 * @param resourceKey the resource key/name to resolve
	 * @param groupKey    optional group key used to disambiguate grouped searches
	 * @param groupedOnly if {@code true}, restricts the search to grouped
	 *                    resources;
	 *                    if {@code false}, top-level is searched first
	 * @return the fully built resource subtree with attributes and metrics
	 * @throws ResponseStatusException HTTP 404 when the resource cannot be found
	 */
	public ResourceTelemetry getResource(final String resourceKey, final String groupKey, final boolean groupedOnly) {
		if (!StringHelper.nonNullNonBlank(resourceKey)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceKey);
		}

		final var telemetryManagers = agentContextHolder.getAgentContext().getTelemetryManagers();

		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceKey);
		}

		// Try top-level (if allowed)
		Optional<TelemetryManager> topLevelResult = groupedOnly
			? Optional.empty()
			: Optional.ofNullable(telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY)).map(m -> m.get(resourceKey));

		if (topLevelResult.isPresent()) {
			return buildFullResourceNode(resourceKey, topLevelResult.get());
		}

		// 2. Search grouped resources
		TelemetryManager groupedResult = telemetryManagers
			.entrySet()
			.stream()
			// skip top-level groups
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			// filter by groupKey when provided
			.filter(entry -> !StringHelper.nonNullNonBlank(groupKey) || groupKey.equals(entry.getKey()))
			// look up the resource in that group
			.map((Entry<String, Map<String, TelemetryManager>> entry) -> {
				var groupMap = entry.getValue();
				return groupMap == null ? null : groupMap.get(resourceKey);
			})
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);

		if (groupedResult == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceKey);
		}

		return buildFullResourceNode(resourceKey, groupedResult);
	}

	/**
	 * Returns a top-level resource (not belonging to any resource-group).
	 *
	 * <p>
	 * This method retrieves the telemetry managers from the agent context and
	 * extracts the
	 * {@code TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY} group, which contains all
	 * top-level resources.
	 * It then attempts to locate the {@link TelemetryManager} associated with the
	 * given
	 * {@code resourceName}. If found, the full {@link ResourceTelemetry} node is
	 * built and returned.
	 * </p>
	 *
	 * <p>
	 * If the resource does not exist in the top-level group, a
	 * {@link org.springframework.web.server.ResponseStatusException} with HTTP 404
	 * is thrown.
	 * </p>
	 *
	 * @param resourceName the resource key/name to resolve; must not be
	 *                     {@code null}
	 * @return the resolved top-level resource subtree
	 * @throws ResponseStatusException with HTTP status 404 when the resource cannot
	 *                                 be found
	 */
	public ResourceTelemetry getTopLevelResource(@NonNull final String resourceName) {
		final var telemetryManagers = agentContextHolder.getAgentContext().getTelemetryManagers();

		final var topLevel = telemetryManagers == null ? null : telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY);

		return Optional
			.ofNullable(topLevel)
			.map(m -> m.get(resourceName))
			.map(tm -> buildFullResourceNode(resourceName, tm))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName));
	}

	/**
	 * Returns a resource that belongs to resource-groups (excluding the top-level
	 * virtual group).
	 * If {@code groupKey} is provided, only that group is searched.
	 *
	 * <p>
	 * This method looks through all telemetry manager groups and attempts to find
	 * the
	 * {@link TelemetryManager} associated with the given {@code resourceName}. If
	 * found, it builds and
	 * returns the full {@link ResourceTelemetry} tree for that resource.
	 * </p>
	 *
	 * <p>
	 * If the resource cannot be found in any applicable group, a
	 * {@link org.springframework.web.server.ResponseStatusException} with HTTP 404
	 * is thrown.
	 * </p>
	 *
	 * @param resourceName the resource key/name to resolve
	 * @param groupKey     optional group key to restrict the search; if blank or
	 *                     {@code null}, all groups are searched
	 * @return the resolved grouped resource subtree
	 * @throws ResponseStatusException with HTTP status 404 when the resource cannot
	 *                                 be found
	 */
	public ResourceTelemetry getGroupedResource(final String resourceName, final String groupKey) {
		if (!StringHelper.nonNullNonBlank(resourceName)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		final var telemetryManagers = agentContextHolder.getAgentContext().getTelemetryManagers();

		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		return telemetryManagers
			.entrySet()
			.stream()
			// Skip top-level virtual group
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			// Filter by groupKey if provided
			.filter(entry -> groupKey == null || groupKey.isBlank() || groupKey.equals(entry.getKey()))
			// Extract TelemetryManager matching resourceName
			.map((Entry<String, Map<String, TelemetryManager>> entry) -> {
				var groupMap = entry.getValue();
				return (groupMap == null || groupMap.isEmpty()) ? null : groupMap.get(resourceName);
			})
			// Keep only non-null TelemetryManagers
			.filter(Objects::nonNull)
			// Build and return the ResourceTelemetry
			.findFirst()
			.map(tm -> buildFullResourceNode(resourceName, tm))
			// Throw HTTP 404 if nothing found
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName));
	}

	/**
	 * Builds a resource subtree copying attributes and metrics for the endpoint
	 * host,
	 * connectors, monitor types and monitor instances.
	 *
	 * @param resourceKey the resource identifier (as configured)
	 * @param tm          telemetry manager backing this resource
	 * @return a populated {@link ResourceTelemetry} subtree
	 */
	private static ResourceTelemetry buildFullResourceNode(final String resourceKey, final TelemetryManager tm) {
		final ResourceTelemetry resourceNode = ResourceTelemetry.builder().name(resourceKey).build();
		// Group relationship is now only conveyed by structure; do not expose a
		// resource-group attribute

		// Copy endpoint host attributes & basic metrics snapshot
		final var endpointHost = tm.getEndpointHostMonitor();
		if (endpointHost != null) {
			if (endpointHost.getAttributes() != null) {
				resourceNode.getAttributes().putAll(endpointHost.getAttributes());
			}
			if (endpointHost.getMetrics() != null) {
				endpointHost.getMetrics().forEach((k, v) -> resourceNode.getMetrics().put(k, getMetricValue(v)));
			}
		}

		// Build connectors with monitors and instances directly under the resource
		resourceNode.getConnectors().addAll(buildConnectorsFull(tm));
		return resourceNode;
	}

	/**
	 * Builds connector nodes including their monitor types and expanded monitor
	 * instances
	 * for the given resource telemetry manager.
	 *
	 * @param tm the telemetry manager to inspect
	 * @return a list of connectors with nested monitor types and instances
	 */
	private static List<ConnectorTelemetry> buildConnectorsFull(final TelemetryManager tm) {
		final Map<String, Monitor> connectorMonitors = tm.findMonitorsByType(CONNECTOR.getKey());
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			return List.of();
		}

		final Map<String, Set<String>> connectorIdToMonitorTypes = groupMonitorTypesByConnectorId(tm, connectorMonitors);

		final List<ConnectorTelemetry> connectors = new ArrayList<>();

		connectorMonitors
			.values()
			.stream()
			.sorted(ExplorerService::compareMonitorsById)
			.forEach((Monitor connectorMonitor) ->
				appendConnectorNode(tm, connectorIdToMonitorTypes, connectors, connectorMonitor)
			);
		return connectors;
	}

	/**
	 * Appends a connector node to the given list, expanding its monitor types and
	 * instances.
	 *
	 * @param tm                        the telemetry manager to search for monitors
	 * @param connectorIdToMonitorTypes map of connector IDs to their associated
	 *                                  monitor types
	 * @param connectors                the list of connectors to which the new
	 *                                  connector node will be added
	 * @param connectorMonitor          the connector monitor instance
	 */
	private static void appendConnectorNode(
		final TelemetryManager tm,
		final Map<String, Set<String>> connectorIdToMonitorTypes,
		final List<ConnectorTelemetry> connectors,
		Monitor connectorMonitor
	) {
		final String connectorId = connectorMonitor.getAttributes().get(MONITOR_ATTRIBUTE_ID);

		final ConnectorTelemetry connectorNode = ConnectorTelemetry.builder().name(connectorId).build();

		// copy connector attributes
		if (connectorMonitor.getAttributes() != null) {
			connectorNode.getAttributes().putAll(connectorMonitor.getAttributes());
		}
		var metaMetrics = tm.getConnectorStore().getStore().get(connectorId).getMetrics();
		if (metaMetrics != null) {
			connectorNode.setMetaMetrics(metaMetrics);
		}
		// copy connector metrics
		if (connectorMonitor.getMetrics() != null) {
			connectorMonitor.getMetrics().forEach((k, v) -> connectorNode.getMetrics().put(k, getMetricValue(v)));
		}

		final Set<String> monitorTypes = connectorIdToMonitorTypes.getOrDefault(connectorId, Set.of());
		monitorTypes
			.stream()
			.sorted(String::compareToIgnoreCase)
			.forEach((String type) -> appendMonitorType(tm, connectorId, connectorNode, type));

		connectors.add(connectorNode);
	}

	/**
	 * Appends a monitor-type node under the given connector node, expanding all
	 *
	 * @param tm            the telemetry manager to search for monitors
	 * @param connectorId   the connector identifier
	 * @param connectorNode the connector telemetry node to which the monitor type
	 *                      will be added
	 * @param type          the monitor type name
	 */
	private static void appendMonitorType(
		final TelemetryManager tm,
		final String connectorId,
		final ConnectorTelemetry connectorNode,
		final String type
	) {
		// Create the monitor-type node
		final MonitorTypeTelemetry monitorTypeNode = MonitorTypeTelemetry.builder().name(type).build();

		// Expand with monitor instances for this connector and type
		final Map<String, Monitor> monitorsOfType = tm.findMonitorsByType(type);
		if (monitorsOfType != null && !monitorsOfType.isEmpty()) {
			monitorsOfType
				.values()
				.stream()
				.filter(Objects::nonNull)
				.filter(monitor -> connectorId.equals(monitor.getAttributes().get(MONITOR_ATTRIBUTE_CONNECTOR_ID)))
				.sorted(ExplorerService::compareMonitorsById)
				.forEach((Monitor monitor) -> appendInstances(monitorTypeNode, monitor));
		}

		connectorNode.getMonitors().add(monitorTypeNode);
	}

	/**
	 * Appends monitor instances to the given monitor-type node.
	 *
	 * @param monitorTypeNode the monitor-type telemetry node
	 * @param monitor         the monitor instance to append
	 */
	private static void appendInstances(final MonitorTypeTelemetry monitorTypeNode, Monitor monitor) {
		final InstanceTelemetry instanceNode = InstanceTelemetry.builder().name(monitor.getId()).build();
		if (monitor.getAttributes() != null) {
			instanceNode.getAttributes().putAll(monitor.getAttributes());
		}
		if (monitor.getMetrics() != null) {
			monitor.getMetrics().forEach((k, v) -> instanceNode.getMetrics().put(k, getMetricValue(v)));
		}
		monitorTypeNode.getInstances().add(instanceNode);
	}

	/**
	 * Comparator helper to sort monitors by their identifier.
	 *
	 * @param a The first monitor
	 * @param b The second monitor
	 * @return comparison result
	 */
	private static int compareMonitorsById(Monitor a, Monitor b) {
		return a.getId().compareToIgnoreCase(b.getId());
	}

	/**
	 * Extracts the raw value from a metric, or null if the metric is null.
	 *
	 * @param metric the metric instance
	 * @return the raw metric value or null
	 */
	private static Object getMetricValue(AbstractMetric metric) {
		return metric == null ? null : metric.getValue();
	}

	/**
	 * Performs a search across hierarchy elements (excluding virtual container
	 * nodes) using Jaro–Winkler
	 *
	 * @param query raw query string
	 * @return ranked list of matches
	 */
	public List<SearchMatch> search(final String query) {
		final String q = query == null ? "" : query.trim();
		if (q.isEmpty()) {
			return List.of();
		}
		return searchService.search(q, getHierarchy());
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
				.map(m -> m.getAttributes().get(MONITOR_ATTRIBUTE_ID))
				.collect(Collectors.toSet());
			connectorIds.forEach(id -> result.computeIfAbsent(id, k -> new HashSet<>()));
		}

		return result;
	}
}
