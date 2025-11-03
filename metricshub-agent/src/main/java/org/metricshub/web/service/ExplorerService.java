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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ExplorerNode;
import org.springframework.stereotype.Service;

/**
 * Service responsible for building a light-weight hierarchy representation of
 * the configured/active resources from the AgentContext's telemetry managers.
 * <p>
 * The resulting structure is designed to be simple and UI-agnostic
 * (children-only
 * nodes with a single display name), allowing the frontend to render it in any
 * tree component without tight coupling to backend types.
 * </p>
 */
@Service
public class ExplorerService {

	private final AgentContextHolder agentContextHolder;

	/**
	 * Creates a new service using the provided agent context holder.
	 *
	 * @param agentContextHolder holder that provides access to the current
	 *                           {@link AgentContext}
	 */
	public ExplorerService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Builds and returns the complete hierarchy structure as a tree of
	 * {@link org.metricshub.web.dto.ExplorerNode}.
	 * <p>
	 * The tree contains two top-level branches:
	 * <ul>
	 * <li><b>resource-groups</b>: groups and their resources</li>
	 * <li><b>resources</b>: top-level resources (not in any group)</li>
	 * </ul>
	 * Each resource node lists its monitor types as leaf nodes.
	 * </p>
	 *
	 * @return a list with two root nodes (resource-groups, resources). Returns an
	 *         empty list if the AgentContext or its telemetry managers are not
	 *         available.
	 */
	public List<ExplorerNode> getHierarchy() {
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getTelemetryManagers() == null) {
			return List.of();
		}

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();

		// Build group branch (excluding the virtual top-level group key)
		final List<ExplorerNode> groupChildren = telemetryManagers
			.entrySet()
			.stream()
			.filter(e -> !Objects.equals(e.getKey(), ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY))
			.sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
			.map(e -> buildResourceGroupNode(e.getKey(), e.getValue()))
			.collect(Collectors.toCollection(ArrayList::new));

		// Build top-level resources branch
		final Map<String, TelemetryManager> topLevelResources = telemetryManagers.getOrDefault(
			ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			Collections.emptyMap()
		);
		final List<ExplorerNode> topResourcesChildren = new ArrayList<>();
		topResourcesChildren.addAll(buildResourcesNodes(topLevelResources));

		final ExplorerNode resourceGroupsRoot = ExplorerNode
			.builder()
			.name("resource-groups")
			.children(groupChildren)
			.build();
		final ExplorerNode resourcesRoot = ExplorerNode.builder().name("resources").children(topResourcesChildren).build();

		return List.of(resourceGroupsRoot, resourcesRoot);
	}

	/**
	 * Builds and returns a flat list of all configured resources as
	 * {@link ExplorerNode}s, each with its monitor types as children (children-only
	 * structure).
	 * <p>
	 * Unlike {@link #getHierarchy()}, this method does not include the two
	 * top-level grouping nodes. It aggregates resources from all groups (including
	 * top-level/un-grouped) and sorts them by hostname.
	 * </p>
	 *
	 * @return list of resource nodes, or an empty list if no telemetry managers are
	 *         available
	 */
	public List<ExplorerNode> getResources() {
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getTelemetryManagers() == null) {
			return List.of();
		}

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();

		return telemetryManagers
			.values()
			.stream()
			.flatMap(groupMap -> groupMap.values().stream())
			.sorted(Comparator.comparing(tm -> safeHostname(tm), String.CASE_INSENSITIVE_ORDER))
			.map(this::buildResourceNode)
			.collect(Collectors.toList());
	}

	/**
	 * Creates a node for a specific resource group and populates it with the
	 * group's resources.
	 *
	 * @param groupName              the resource group name
	 * @param groupTelemetryManagers telemetry managers indexed by resource id for
	 *                               the group
	 * @return a node named after the group containing its resources
	 */
	private ExplorerNode buildResourceGroupNode(
		final String groupName,
		final Map<String, TelemetryManager> groupTelemetryManagers
	) {
		final List<ExplorerNode> resources = buildResourcesNodes(groupTelemetryManagers);
		return ExplorerNode.builder().name(groupName).children(resources).build();
	}

	/**
	 * Builds resource nodes (one per telemetry manager) sorted by hostname.
	 *
	 * @param tms map of resource id to {@link TelemetryManager}
	 * @return a list of resource nodes, or an empty list if input is null/empty
	 */
	private List<ExplorerNode> buildResourcesNodes(final Map<String, TelemetryManager> tms) {
		if (tms == null || tms.isEmpty()) {
			return List.of();
		}
		return tms
			.values()
			.stream()
			.sorted(Comparator.comparing(tm -> safeHostname(tm), String.CASE_INSENSITIVE_ORDER))
			.map(this::buildResourceNode)
			.collect(Collectors.toList());
	}

	/**
	 * Builds a single resource node with its monitor type leaf nodes.
	 *
	 * @param tm the telemetry manager associated with the resource
	 * @return the resource node
	 */
	private ExplorerNode buildResourceNode(final TelemetryManager tm) {
		final String resourceName = safeHostname(tm);

		// Child: monitors (by type). Keep it lightweight with children-only nodes.
		final List<ExplorerNode> monitorTypeNodes = tm.getMonitors() == null
			? List.of()
			: tm
				.getMonitors()
				.keySet()
				.stream()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.map(mt -> ExplorerNode.builder().name(mt).children(List.of()).build())
				.collect(Collectors.toList());

		return ExplorerNode.builder().name(resourceName).children(monitorTypeNodes).build();
	}

	/**
	 * Safely retrieves the resource hostname from a telemetry manager, falling back
	 * to "unknown" if not available.
	 *
	 * @param tm the telemetry manager
	 * @return the hostname or "unknown" when absent
	 */
	private static String safeHostname(final TelemetryManager tm) {
		try {
			final String hn = tm.getHostname();
			if (hn != null && !hn.isBlank()) {
				return hn;
			}
		} catch (Exception ignored) {}
		return "unknown";
	}
}
