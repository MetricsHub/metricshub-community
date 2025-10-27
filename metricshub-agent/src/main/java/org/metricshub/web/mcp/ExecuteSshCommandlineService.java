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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that runs a shell command over SSH on one or more hosts and return the
 * output/status as a {@link MultiHostToolResponse}.
 *
 */
public class ExecuteSshCommandlineService implements IMCPToolService {

	/**
	 * Default pool size for SSH queries.
	 */
	private static final int DEFAULT_SSH_POOL_SIZE = 60;

	/**
	 * Default timeout in seconds used when executing the SSH commandline.
	 */
	private static final long DEFAULT_COMMANDLINE_TIMEOUT = 10L;

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteSshCommandlineService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteSshCommandlineService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Run a Shell commandline through SSH on the resolved hosts.
	 *
	 * @param hostnames    target hosts to query.
	 * @param commandline  the Shell commandline to execute.
	 * @param timeout      timeout in seconds; defaults to {@value #DEFAULT_COMMANDLINE_TIMEOUT} if null/≤0.
	 * @param poolSize     the pool size for concurrent executions.
	 * @return a {@code MultiHostToolResponse} containing a response for each host.
	 */
	@Tool(
		name = "ExecuteSshCommandline",
		description = """
		Execute a shell command over SSH on one or more hosts and return the command output (and status).
		Supports an optional timeout (default 10s) and a pool size to control parallel execution.
		"""
	)
	public MultiHostToolResponse<QueryResponse> executeQuery(
		@ToolParam(description = "The hostname(s) to execute commandline on.", required = true) final List<
			String
		> hostnames,
		@ToolParam(description = "The commandline to execute.", required = true) final String commandline,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent SSH queries. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		// If SSH isn't enabled for the MCP tools, return an error message.
		if (!isSshEnabledForMCP()) {
			return MultiHostToolResponse.buildError("The SSH connections are disabled for MCP.");
		}

		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_SSH_POOL_SIZE);

		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("ssh")
			.map((IProtocolExtension extension) ->
				executeForHosts(
					hostnames,
					this::buildNullHostnameResponse,
					host ->
						HostToolResponse
							.<QueryResponse>builder()
							.hostname(host)
							.response(executeSshCommandlineWithExtensionSafe(extension, host, commandline, timeout))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError("No Extension found for SSH."));
	}

	/**
	 * Resolve a valid SSH configuration for the host and execute the command line via the SSH extension.
	 *
	 * @param extension   SSH protocol extension to use
	 * @param hostname    target host
	 * @param commandline shell command to execute
	 * @param timeout     optional timeout in seconds (defaults applied if null/≤0)
	 * @return {@link QueryResponse} containing the extension response, or an error if no valid config is found
	 */
	private QueryResponse executeSshCommandlineWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String commandline,
		final Long timeout
	) {
		// Try to retrieve an SSH configuration for the host
		return MCPConfigHelper
			.resolveAllHostConfigurationCopiesFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map((IConfiguration configurationCopy) ->
				executeQuerySafe(extension, configurationCopy, hostname, commandline, timeout)
			)
			.orElseGet(() ->
				QueryResponse.builder().error("No valid configuration found for SSH on %s.".formatted(hostname)).build()
			);
	}

	/**
	 * Execute the SSH command with a validated configuration. Returns an error if SSH is disabled.
	 * Otherwise, creates a query JsonNode and tries to execute the commandline on the host
	 * with the SSH configuration. Returns the extension result, or an error on exception.
	 *
	 * @param extension         SSH protocol extension
	 * @param configurationCopy validated host configuration (mutated with hostname/timeout)
	 * @param hostname          target host
	 * @param commandline       shell command to execute
	 * @param timeout           optional timeout in seconds (defaults applied if null/≤0)
	 * @return {@link QueryResponse} with the command output, or an error message
	 */
	private static QueryResponse executeQuerySafe(
		final IProtocolExtension extension,
		final IConfiguration configurationCopy,
		final String hostname,
		final String commandline,
		final Long timeout
	) {
		// add hostname and timeout to the valid configuration
		configurationCopy.setHostname(hostname);
		configurationCopy.setTimeout(NumberHelper.getPositiveOrDefault(timeout, DEFAULT_COMMANDLINE_TIMEOUT).longValue());

		// Create a json node and populate it with the commandline
		final var queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("commandLine", new TextNode(commandline));

		try {
			return QueryResponse.builder().response(extension.executeQuery(configurationCopy, queryNode)).build();
		} catch (Exception e) {
			return QueryResponse
				.builder()
				.error("An error has occurred when executing the commandline: %s".formatted(e.getMessage()))
				.build();
		}
	}

	/**
	 * Builds a {@link HostToolResponse} describing the error when the hostname
	 * parameter is omitted.
	 *
	 * @return a host-level response with an error payload explaining the missing hostname
	 */
	private HostToolResponse<QueryResponse> buildNullHostnameResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() ->
			QueryResponse.builder().error(NULL_HOSTNAME_ERROR).build()
		);
	}

	/**
	 * Checks JVM flag {@code metricshub.tools.ssh.enabled}.
	 * @return {@code true} if SSH tool is enabled; otherwise {@code false}.
	 */
	static boolean isSshEnabledForMCP() {
		return Boolean.getBoolean("metricshub.tools.ssh.enabled");
	}
}
