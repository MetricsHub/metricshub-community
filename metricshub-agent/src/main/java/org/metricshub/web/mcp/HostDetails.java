package org.metricshub.web.mcp;

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

import java.util.Set;
import lombok.Builder;
import lombok.Data;

/**
 * Encapsulates all the host details that need to be returned to the MCP server for a given host.
 * This regroups the list of configured protocols, the working connectors, the collectors that can
 * be applied for the given host protocols. And finally, an optional error message to display if any
 * error occurs.
 */
@Builder
@Data
public class HostDetails {

	/**
	 * List of configured protocols in the current host
	 */
	private Set<String> configuredProtocols;

	/**
	 * List of connectors that work in the current host.
	 */
	private Set<String> workingConnectors;

	/**
	 * List of the collectors that can be applied to the current host.
	 */
	private Set<String> collectors;

	/**
	 * The error message to return if any.
	 */
	private final String errorMessage;
}
