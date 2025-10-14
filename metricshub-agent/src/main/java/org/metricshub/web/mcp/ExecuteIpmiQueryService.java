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

import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that runs IPMI queries through the MetricsHub agent.
 * It looks up a valid IPMI configuration for a host, fills in the runtime
 * parameters (hostname and timeout), and delegates the call to the IPMI extension.
 */
public class ExecuteIpmiQueryService {

	/**
	 * Default timeout in seconds used when executing the IPMI query.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteIpmiQueryService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteIpmiQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes an IPMI query for the given host.
	 *
	 * @param hostname target host to query
	 * @param timeout  timeout in seconds; defaults to 10 when {@code null} or ≤ 0
	 * @return a {@link QueryResponse} containing the provider payload or an error message
	 */
	@Tool(
		name = "ExecuteIpmiQuery",
		description = """
		Execute an IPMI query on the given host using the agent’s IPMI extension.
		Resolves a valid configuration from context, applies hostname and timeout (default 10s),
		executes the query, and returns the provider result or an error.
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute IPMI query on.", required = true) final String hostname,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("ipmi")
			.map((IProtocolExtension extension) -> executeIpmiQueryWithExtensionSafe(extension, hostname, timeout))
			.orElse(QueryResponse.builder().isError("No Extension found for IPMI protocol.").build());
	}

	/**
	 * Finds a valid configuration for the host, applies runtime parameters, and
	 * invokes the provided IPMI extension.
	 *
	 * @param extension IPMI extension to use
	 * @param hostname  target host
	 * @param timeout   timeout in seconds; default is applied when null or ≤ 0
	 * @return query result or an error wrapped in {@link QueryResponse}
	 */
	QueryResponse executeIpmiQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final Long timeout
	) {
		return MCPConfigHelper
			.resolveAllHostConfigurationCopiesFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map(configuration -> {
				configuration.setTimeout(NumberHelper.getPositiveOrDefault(timeout, DEFAULT_QUERY_TIMEOUT).longValue());
				try {
					return QueryResponse.builder().response(extension.executeQuery(configuration, null)).build();
				} catch (Exception e) {
					return QueryResponse
						.builder()
						.isError("An error has occurred when executing the query: %s".formatted(e.getMessage()))
						.build();
				}
			})
			.orElseGet(() ->
				QueryResponse.builder().isError("No IPMI configuration found for %s.".formatted(hostname)).build()
			);
	}
}
