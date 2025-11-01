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

import java.util.List;
import org.metricshub.web.dto.ExplorerNode;
import org.metricshub.web.service.ExplorerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes endpoints related to the Explorer view.
 * <p>
 * Currently provides a single endpoint to retrieve the complete resource
 * hierarchy
 * observed by the agent, structured as a children-only tree suitable for
 * consumption by frontend components (e.g., React MUI TreeView).
 * </p>
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
	 * The response contains two top-level nodes: "resource-groups" and
	 * "resources". Each resource node lists its monitor types as leaf nodes.
	 * </p>
	 *
	 * @return a list containing two root {@link ExplorerNode} items representing
	 *         resource groups and top-level resources
	 */
	@GetMapping("/hierarchy")
	public List<ExplorerNode> hierarchy() {
		return explorerService.getHierarchy();
	}
}
