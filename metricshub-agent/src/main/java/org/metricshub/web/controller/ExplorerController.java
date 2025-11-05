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

import org.metricshub.web.dto.AgentTelemetry;
import org.metricshub.web.service.ExplorerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API to expose the agent hierarchy and its resource-groups and monitored
 * resources.
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
	 * children are two nodes: "resource-groups" and "resources", each containing
	 * their respective connector and monitor children.
	 * </p>
	 *
	 * @return the agent hierarchy
	 */
	@GetMapping("/hierarchy")
	public AgentTelemetry getHierarchy() {
		return explorerService.getHierarchy();
	}

	/**
	 * Retrieves all configured resources as a children-only tree under a single
	 * top-level "resources" container node.
	 *
	 * @return the resources container with all resources as children
	 */
	@GetMapping("/resources")
	public AgentTelemetry getResources() {
		return explorerService.getResources();
	}

	/**
	 * Retrieves details for a single resource by name.
	 *
	 * @param resourceName the resource key/name
	 * @return a resource node, including its connectors and monitor types
	 */
	@GetMapping("/resources/{resourceName}")
	public AgentTelemetry getResource(@PathVariable("resourceName") final String resourceName) {
		return explorerService.getResource(resourceName);
	}

	/**
	 * Retrieves the list of connectors for a single resource as a children-only
	 * tree under a top-level "connectors" container node.
	 *
	 * @param resourceName the resource key/name
	 * @return the connectors container node for the resource
	 */
	@GetMapping("/resources/{resourceName}/connectors")
	public AgentTelemetry getResourceConnectors(@PathVariable("resourceName") final String resourceName) {
		return explorerService.getResourceConnectors(resourceName);
	}

	/**
	 * Retrieves details for a single connector under a given resource.
	 *
	 * @param resourceName  the resource key/name
	 * @param connectorName the connector id or display name
	 * @return a connector node, including its monitors container
	 */
	@GetMapping("/resources/{resourceName}/connectors/{connectorName}")
	public AgentTelemetry getResourceConnector(
		@PathVariable("resourceName") final String resourceName,
		@PathVariable("connectorName") final String connectorName
	) {
		return explorerService.getResourceConnector(resourceName, connectorName);
	}
}
