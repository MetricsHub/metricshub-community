package org.metricshub.web.controller;

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

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AgentTelemetry;
import org.metricshub.web.dto.telemetry.ResourceTelemetry;
import org.metricshub.web.service.ExplorerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API to expose the agent hierarchy and its resource-groups and monitored
 * resources.
 * Key endpoints:
 * <ul>
 * <li>GET {@code /api/hierarchy} – full agent hierarchy</li>
 * <li>GET {@code /api/resources/{resourceName}} – top-level resource
 * subtree</li>
 * <li>GET {@code /api/resource-groups/{groupName}/resources/{resourceName}} –
 * grouped resource subtree</li>
 * <li>GET {@code /api/search?q=...} – fuzzy search across hierarchy
 * elements</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExplorerController {

	private final ExplorerService explorerService;

	/**
	 * Creates a new controller backed by the given service.
	 *
	 * @param explorerService service that assembles the hierarchy from the current
	 *                        AgentContext
	 */
	public ExplorerController(final ExplorerService explorerService) {
		this.explorerService = explorerService;
	}

	/**
	 * Retrieves the complete resource hierarchy as a children-only tree.
	 * <p>
	 * The response contains one top level element which is the Agent itself. Its
	 * children are two typed collections: {@code resource-groups} and
	 * {@code resources},
	 * each containing their respective connector and monitor children.
	 * </p>
	 *
	 * @return the agent hierarchy root node
	 */
	@GetMapping("/hierarchy")
	public AgentTelemetry getHierarchy() {
		return explorerService.getHierarchy();
	}

	/**
	 * Retrieves a complete subtree for a top-level resource by name, including
	 * attributes, metrics and children (connectors, monitor types and instances).
	 *
	 * @param resourceName the resource key/name as configured at the top level
	 * @return the full resource subtree
	 */
	@GetMapping("/resources/{resourceName}")
	public ResourceTelemetry getTopLevelResource(@NotBlank @PathVariable("resourceName") final String resourceName) {
		return explorerService.getTopLevelResource(resourceName);
	}

	/**
	 * Retrieves a complete subtree for a resource that belongs to a specific
	 * resource group, using an unambiguous path that includes the group name.
	 *
	 * @param groupName    the resource-group key/name
	 * @param resourceName the resource key/name within the specified group
	 * @return the full resource subtree
	 */
	@GetMapping("/resource-groups/{groupName}/resources/{resourceName}")
	public ResourceTelemetry getGroupedResourceByPath(
		@NotBlank @PathVariable("groupName") final String groupName,
		@NotBlank @PathVariable("resourceName") final String resourceName
	) {
		return explorerService.getGroupedResource(resourceName, groupName);
	}

	/**
	 * Searches across resource-groups, resources, connectors, monitor types and
	 * monitor instances using Jaro–Winkler similarity.
	 *
	 * @param q free-text query string; fuzzy matched against node names
	 * @return a ranked list of matches with their hierarchy paths
	 */
	@GetMapping("/search")
	public List<SearchMatch> search(@RequestParam("q") final String q) {
		return explorerService.search(q);
	}
}
