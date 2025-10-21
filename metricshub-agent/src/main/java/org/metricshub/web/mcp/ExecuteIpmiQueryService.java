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

import java.util.List;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that runs IPMI queries through the MetricsHub agent.
 * It looks up a valid IPMI configuration for a host, fills in the runtime
 * parameters (hostname and timeout), and delegates the call to the IPMI extension.
 */
@Service
public class ExecuteIpmiQueryService implements IMCPToolService {

	/**
	 * Default timeout in seconds used when executing the IPMI query.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * Default pool size for IPMI queries.
	 */
	private static final int DEFAULT_IPMI_POOL_SIZE = 60;

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
		Execute an IPMI query on the given host(s) using the agent’s IPMI extension.
		Resolves a valid configuration from context, applies hostname and timeout (default 10s),
		executes the query, and returns the provider result or an error.
		"""
	)
	public List<MultiHostToolResponse<QueryResponse>> executeQuery(
		@ToolParam(description = "The hostname(s) to execute IPMI query on.", required = true) final List<String> hostname,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent IPMI queries. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_IPMI_POOL_SIZE);
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("ipmi")
			.map((IProtocolExtension extension) ->
				executeForHosts(
					hostname,
					this::buildNullHostnameResponse,
					host ->
						MultiHostToolResponse
							.<QueryResponse>builder()
							.hostname(host)
							.response(executeIpmiQueryWithExtensionSafe(extension, host, timeout))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() ->
				executeForHosts(
					hostname,
					this::buildNullHostnameResponse,
					host ->
						MultiHostToolResponse
							.<QueryResponse>builder()
							.hostname(host)
							.response(QueryResponse.builder().isError("No Extension found for IPMI protocol.").build())
							.build(),
					resolvedPoolSize
				)
			);
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

	/**
	 * Builds a {@link MultiHostToolResponse} representing the error produced when
	 * a null hostname is encountered.
	 *
	 * @return a response wrapper containing an error payload for the missing host
	 *         name
	 */
	private MultiHostToolResponse<QueryResponse> buildNullHostnameResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() ->
			QueryResponse.builder().isError(NULL_HOSTNAME_ERROR).build()
		);
	}
}
