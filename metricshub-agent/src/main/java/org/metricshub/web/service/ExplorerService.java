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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AbstractBaseTelemetry;
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

	/**
	 * Minimum Jaro-Winkler similarity required for a result to be returned.
	 * Values in [0,1]. 0.85 is a reasonable balance to cut mid-quality matches
	 * like incidental substring overlaps while keeping relevant fuzzy hits.
	 */
	private static final double MIN_JW_SCORE = 0.85d;

	// No more virtual container node types: starting from resource-groups,
	// each node has a single typed child collection.

	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new ExplorerService instance.
	 *
	 * @param agentContextHolder holder providing access to the current
	 *                           {@link org.metricshub.agent.context.AgentContext}
	 */
	public ExplorerService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
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
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			agentAttributes.getOrDefault(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, "MetricsHub")
		);

		final AgentTelemetry root = AgentTelemetry.builder().name(agentName).build();

		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			telemetryManagers = Map.of();
		}

		// Resource Groups (excluding top-level group key)
		telemetryManagers
			.entrySet()
			.stream()
			.filter(entry -> !TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(entry.getKey()))
			.sorted(Map.Entry.comparingByKey())
			.forEach(entry -> root.getResourceGroups().add(buildResourceGroupNode(entry.getKey(), entry.getValue())));

		// Top-level Resources (directly under the agent)
		final Map<String, TelemetryManager> topLevelResources = telemetryManagers.getOrDefault(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			Map.of()
		);
		buildResources(root.getResources(), topLevelResources);
		return root;
	}

	/**
	 * Returns a full resource subtree including attributes and metrics.
	 * <p>
	 * When {@code groupedOnly} is true, only searches in resource groups (excluding
	 * top-level);
	 * when false, searches top-level first then groups. If a specific
	 * {@code groupKey} is
	 * provided it is used to narrow the search within that group.
	 * </p>
	 *
	 * @param resourceName resource key/name
	 * @param groupKey     optional group key to disambiguate (ignored for top-level
	 *                     only search)
	 * @param groupedOnly  restricts the search to grouped resources when true
	 * @return resource subtree with connectors, monitor types and instances
	 *         enriched with
	 *         attributes/metrics
	 */
	public ResourceTelemetry getResource(final String resourceName, final String groupKey, final boolean groupedOnly) {
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		final var agentContext = agentContextHolder.getAgentContext();
		Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		TelemetryManager tm = null;
		String resolvedGroup = null;

		// Search order: top-level then groups OR only groups depending on flag
		if (!groupedOnly) {
			final Map<String, TelemetryManager> topLevel = telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY);
			if (topLevel != null) {
				tm = topLevel.get(resourceName);
				if (tm != null) {
					resolvedGroup = TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
				}
			}
		}

		if (tm == null) {
			for (Entry<String, Map<String, TelemetryManager>> entry : telemetryManagers.entrySet()) {
				final String currentGroup = entry.getKey();
				if (TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(currentGroup)) {
					continue; // skip top-level when grouped search
				}
				if (groupKey != null && !groupKey.isBlank() && !groupKey.equals(currentGroup)) {
					continue; // disambiguated group mismatch
				}
				final Map<String, TelemetryManager> groupMap = entry.getValue();
				if (groupMap == null || groupMap.isEmpty()) {
					continue;
				}
				final TelemetryManager candidate = groupMap.get(resourceName);
				if (candidate != null) {
					tm = candidate;
					resolvedGroup = currentGroup;
					break;
				}
			}
		}

		if (tm == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}

		return buildFullResourceNode(resourceName, tm, resolvedGroup);
	}

	/**
	 * Returns a top-level resource (not belonging to any resource-group).
	 *
	 * @param resourceName the resource key/name
	 * @return the resolved top-level resource subtree
	 * @throws ResponseStatusException 404 when the resource cannot be found
	 */
	public ResourceTelemetry getTopLevelResource(final String resourceName) {
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		final Map<String, TelemetryManager> topLevel = telemetryManagers == null
			? null
			: telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY);
		final TelemetryManager tm = topLevel == null ? null : topLevel.get(resourceName);
		if (tm == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		return buildFullResourceNode(resourceName, tm, TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY);
	}

	/**
	 * Returns a resource that belongs to resource-groups (excludes top-level).
	 * If {@code groupKey} is provided, only that group is searched.
	 *
	 * @param resourceName the resource key/name
	 * @param groupKey     optional group key to restrict the search
	 * @return the resolved grouped resource subtree
	 * @throws ResponseStatusException 404 when the resource cannot be found
	 */
	public ResourceTelemetry getGroupedResource(final String resourceName, final String groupKey) {
		if (resourceName == null || resourceName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		final var agentContext = agentContextHolder.getAgentContext();
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null || telemetryManagers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
		}
		for (Entry<String, Map<String, TelemetryManager>> entry : telemetryManagers.entrySet()) {
			final String currentGroup = entry.getKey();
			if (TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(currentGroup)) {
				continue;
			}
			if (groupKey != null && !groupKey.isBlank() && !groupKey.equals(currentGroup)) {
				continue;
			}
			final Map<String, TelemetryManager> groupMap = entry.getValue();
			if (groupMap == null || groupMap.isEmpty()) {
				continue;
			}
			final TelemetryManager tm = groupMap.get(resourceName);
			if (tm != null) {
				return buildFullResourceNode(resourceName, tm, currentGroup);
			}
		}
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceName);
	}

	/**
	 * Builds a resource subtree copying attributes and metrics for the endpoint
	 * host,
	 * connectors, monitor types and monitor instances.
	 *
	 * @param resourceKey the resource identifier (as configured)
	 * @param tm          telemetry manager backing this resource
	 * @param groupKey    the group key this resource belongs to (or top-level key)
	 * @return a populated {@link ResourceTelemetry} subtree
	 */
	private static ResourceTelemetry buildFullResourceNode(
		final String resourceKey,
		final TelemetryManager tm,
		final String groupKey
	) {
		final ResourceTelemetry resourceNode = ResourceTelemetry.builder().name(resourceKey).build();
		// Group relationship is now only conveyed by structure; do not expose a
		// resource-group attribute

		// Copy endpoint host attributes & basic metrics snapshot
		final Monitor endpointHost = tm.getEndpointHostMonitor();
		if (endpointHost != null) {
			if (endpointHost.getAttributes() != null) {
				resourceNode.getAttributes().putAll(endpointHost.getAttributes());
			}
			if (endpointHost.getMetrics() != null) {
				endpointHost.getMetrics().forEach((k, v) -> resourceNode.getMetrics().put(k, v == null ? null : v.getValue()));
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
			.sorted((Monitor a, Monitor b) -> {
				final String an = a.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, a.getId());
				final String bn = b.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, b.getId());
				return an.compareToIgnoreCase(bn);
			})
			.forEach((Monitor connectorMonitor) -> {
				final String connectorId = connectorMonitor
					.getAttributes()
					.getOrDefault(MONITOR_ATTRIBUTE_ID, connectorMonitor.getId());
				final String connectorName = connectorMonitor.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, connectorId);

				final ConnectorTelemetry connectorNode = ConnectorTelemetry.builder().name(connectorName).build();
				// copy connector attributes
				if (connectorMonitor.getAttributes() != null) {
					connectorNode.getAttributes().putAll(connectorMonitor.getAttributes());
				}
				// copy connector metrics
				if (connectorMonitor.getMetrics() != null) {
					connectorMonitor
						.getMetrics()
						.forEach((k, v) -> connectorNode.getMetrics().put(k, v == null ? null : v.getValue()));
				}

				final Set<String> monitorTypes = connectorIdToMonitorTypes.getOrDefault(connectorId, Set.of());
				monitorTypes
					.stream()
					.sorted(String::compareToIgnoreCase)
					.forEach(type -> {
						// Create the monitor-type node
						final MonitorTypeTelemetry monitorTypeNode = MonitorTypeTelemetry.builder().name(type).build();

						// Expand with monitor instances for this connector and type
						final Map<String, Monitor> monitorsOfType = tm.findMonitorsByType(type);
						if (monitorsOfType != null && !monitorsOfType.isEmpty()) {
							monitorsOfType
								.values()
								.stream()
								.filter(Objects::nonNull)
								.filter(m ->
									connectorId.equals(
										m.getAttributes() == null ? null : m.getAttributes().get(MONITOR_ATTRIBUTE_CONNECTOR_ID)
									)
								)
								.sorted((m1, m2) -> {
									final String n1 = m1.getAttributes() == null ? null : m1.getAttributes().get(MONITOR_ATTRIBUTE_NAME);
									final String n2 = m2.getAttributes() == null ? null : m2.getAttributes().get(MONITOR_ATTRIBUTE_NAME);
									final String a1 = (n1 == null || n1.isBlank()) ? m1.getId() : n1;
									final String a2 = (n2 == null || n2.isBlank()) ? m2.getId() : n2;
									return a1.compareToIgnoreCase(a2);
								})
								.forEach(m -> {
									final String mNameAttr = m.getAttributes() == null
										? null
										: m.getAttributes().get(MONITOR_ATTRIBUTE_NAME);
									final String instanceName = (mNameAttr == null || mNameAttr.isBlank()) ? m.getId() : mNameAttr;
									final InstanceTelemetry instanceNode = InstanceTelemetry.builder().name(instanceName).build();
									if (m.getAttributes() != null) {
										instanceNode.getAttributes().putAll(m.getAttributes());
									}
									if (m.getMetrics() != null) {
										m.getMetrics().forEach((k, v) -> instanceNode.getMetrics().put(k, v == null ? null : v.getValue()));
									}
									monitorTypeNode.getInstances().add(instanceNode);
								});
						}

						connectorNode.getMonitors().add(monitorTypeNode);
					});

				connectors.add(connectorNode);
			});
		return connectors;
	}

	/**
	 * Performs a search across hierarchy elements (excluding virtual container
	 * nodes) using Jaro–Winkler similarity with Levenshtein distance as a secondary
	 * ranking key for tie-breaking.
	 *
	 * @param query raw query string
	 * @return ranked list of matches
	 */
	public List<SearchMatch> search(final String query) {
		final String q = query == null ? "" : query.trim();
		if (q.isEmpty()) {
			return List.of();
		}
		final AgentTelemetry hierarchy = getHierarchy();
		final List<SearchMatch> matches = new ArrayList<>();

		final Queue<TraversalNode> queue = new LinkedList<>();
		queue.add(new TraversalNode(hierarchy, hierarchy.getName()));
		while (!queue.isEmpty()) {
			final TraversalNode tn = queue.poll();
			final AbstractBaseTelemetry current = tn.node;
			final double jw = jaroWinkler(current.getName(), q);
			if (jw >= MIN_JW_SCORE) {
				matches.add(
					SearchMatch
						.builder()
						.name(current.getName())
						.type(current.getType())
						.path(tn.path)
						.jaroWinklerScore(jw)
						.build()
				);
			}

			// enqueue children based on type
			if (current instanceof AgentTelemetry at) {
				if (at.getResourceGroups() != null) {
					at.getResourceGroups().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
				if (at.getResources() != null) {
					at.getResources().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ResourceGroupTelemetry rgt) {
				if (rgt.getResources() != null) {
					rgt.getResources().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ResourceTelemetry rt) {
				if (rt.getConnectors() != null) {
					rt.getConnectors().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof ConnectorTelemetry ct) {
				if (ct.getMonitors() != null) {
					ct.getMonitors().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			} else if (current instanceof MonitorTypeTelemetry mtt) {
				if (mtt.getInstances() != null) {
					mtt.getInstances().forEach(child -> queue.add(new TraversalNode(child, tn.path + "/" + child.getName())));
				}
			}
		}

		return matches
			.stream()
			.sorted((a, b) -> {
				int cmp = Double.compare(b.getJaroWinklerScore(), a.getJaroWinklerScore());
				if (cmp == 0) {
					cmp = a.getPath().compareToIgnoreCase(b.getPath());
				}
				return cmp;
			})
			.limit(250) // safety cap
			.collect(Collectors.toList());
	}

	private static record TraversalNode(AbstractBaseTelemetry node, String path) {}

	// --- Similarity helpers ----------------------------------------------------

	private static double jaroWinkler(final String source, final String target) {
		if (source == null || target == null) {
			return 0d;
		}
		final String a = source.toLowerCase();
		final String b = target.toLowerCase();
		final int maxDist = Math.max(a.length(), b.length()) / 2 - 1;
		final boolean[] aMatches = new boolean[a.length()];
		final boolean[] bMatches = new boolean[b.length()];
		int matches = 0;
		for (int i = 0; i < a.length(); i++) {
			int start = Math.max(0, i - maxDist);
			int end = Math.min(b.length() - 1, i + maxDist);
			for (int j = start; j <= end; j++) {
				if (bMatches[j]) {
					continue;
				}
				if (a.charAt(i) != b.charAt(j)) {
					continue;
				}
				aMatches[i] = true;
				bMatches[j] = true;
				matches++;
				break;
			}
		}
		if (matches == 0) {
			return 0d;
		}
		int transpositions = 0;
		int k = 0;
		for (int i = 0; i < a.length(); i++) {
			if (!aMatches[i]) {
				continue;
			}
			while (!bMatches[k]) {
				k++;
			}
			if (a.charAt(i) != b.charAt(k)) {
				transpositions++;
			}
			k++;
		}
		final double m = matches;
		double jaro = (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
		int prefix = 0;
		for (int i = 0; i < Math.min(4, Math.min(a.length(), b.length())); i++) {
			if (a.charAt(i) == b.charAt(i)) {
				prefix++;
			} else {
				break;
			}
		}
		return jaro + prefix * 0.1 * (1 - jaro);
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
		final ResourceGroupTelemetry groupNode = ResourceGroupTelemetry.builder().name(groupName).build();
		// Do not expose technical resource-group attributes in the API output
		// Build resources under this group and expose the group key on each resource
		// node
		buildResources(groupNode.getResources(), groupTms, groupName);
		return groupNode;
	}

	/**
	 * Populates the provided resources collection with resource nodes built
	 * from the given map.
	 *
	 * @param resourcesTarget the target list to which resource children will be
	 *                        appended
	 * @param tms             a map of resource key to {@link TelemetryManager}
	 */
	private static void buildResources(
		final List<ResourceTelemetry> resourcesTarget,
		final Map<String, TelemetryManager> tms
	) {
		buildResources(resourcesTarget, tms, null);
	}

	/**
	 * Overload that also propagates a resource-group key onto each resource node
	 * when provided.
	 *
	 * @param resourcesTarget the target list to which resource children will be
	 *                        appended
	 * @param tms             a map of resource key to {@link TelemetryManager}
	 * @param groupKeyOrNull  optional group key to reflect structural grouping
	 */
	private static void buildResources(
		final List<ResourceTelemetry> resourcesTarget,
		final Map<String, TelemetryManager> tms,
		final String groupKeyOrNull
	) {
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
				resourcesTarget.add(buildResourceNode(resourceKey, tm, groupKeyOrNull));
			});
	}

	/**
	 * Builds a single resource node including its connectors subtree.
	 *
	 * @param resourceKey the resource identifier (as configured)
	 * @param tm          the {@link TelemetryManager} for this resource
	 * @return a {@link ResourceTelemetry} representing the resource subtree
	 */
	private static ResourceTelemetry buildResourceNode(
		final String resourceKey,
		final TelemetryManager tm,
		final String groupKeyOrNull
	) {
		final ResourceTelemetry resourceNode = ResourceTelemetry.builder().name(resourceKey).build();
		// Group relationship is only conveyed by structure; do not add group-related
		// attributes

		// Attach connectors (lightweight: monitor types only)
		resourceNode.getConnectors().addAll(buildConnectors(tm));

		return resourceNode;
	}

	/**
	 * Builds connector nodes and their monitor type lists.
	 * <p>
	 * Monitor types are grouped by {@code connector_id} and exclude {@code host}
	 * and {@code connector} monitor types.
	 * </p>
	 *
	 * @param tm the {@link TelemetryManager} providing monitors for this resource
	 * @return connectors with their monitor type lists (no instances)
	 */
	private static List<ConnectorTelemetry> buildConnectors(final TelemetryManager tm) {
		final Map<String, Monitor> connectorMonitors = tm.findMonitorsByType(CONNECTOR.getKey());
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			return List.of();
		}

		// Collect monitors by connector-id to later build monitor type lists per
		// connector
		final Map<String, Set<String>> connectorIdToMonitorTypes = groupMonitorTypesByConnectorId(tm, connectorMonitors);

		final List<ConnectorTelemetry> connectors = new ArrayList<>();

		connectorMonitors
			.values()
			.stream()
			.sorted((Monitor a, Monitor b) -> {
				final String an = a.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, a.getId());
				final String bn = b.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, b.getId());
				return an.compareToIgnoreCase(bn);
			})
			.forEach((Monitor connectorMonitor) ->
				connectors.add(buildConnectorNode(connectorIdToMonitorTypes, connectorMonitor))
			);

		return connectors;
	}

	/**
	 * Builds a single connector node including its monitor types list.
	 *
	 * @param connectorIdToMonitorTypes map of connector IDs to their monitor types
	 * @param connectorMonitor          the connector monitor to build the node for
	 * @return a connector node with its monitor type list
	 */
	private static ConnectorTelemetry buildConnectorNode(
		final Map<String, Set<String>> connectorIdToMonitorTypes,
		Monitor connectorMonitor
	) {
		final String connectorId = connectorMonitor
			.getAttributes()
			.getOrDefault(MONITOR_ATTRIBUTE_ID, connectorMonitor.getId());
		final String connectorName = connectorMonitor.getAttributes().getOrDefault(MONITOR_ATTRIBUTE_NAME, connectorId);

		final ConnectorTelemetry connectorNode = ConnectorTelemetry.builder().name(connectorName).build();
		final Set<String> monitorTypes = connectorIdToMonitorTypes.getOrDefault(connectorId, Set.of());
		monitorTypes
			.stream()
			.sorted(String::compareToIgnoreCase)
			.forEach(type -> connectorNode.getMonitors().add(MonitorTypeTelemetry.builder().name(type).build()));

		return connectorNode;
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
