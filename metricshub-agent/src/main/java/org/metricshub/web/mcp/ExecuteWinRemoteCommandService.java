package org.metricshub.web.mcp;

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

/**
 * Service that executes Windows remote OS commands (CMD commands) through WMI or WinRM protocols.
 * The service resolves host configurations from the agent context, keeps only those that the
 * extension accepts, and then delegates execution to the extension.
 * Failures at any step are surfaced as an error in the returned {@link QueryResponse}.
 */
@Service
public class ExecuteWinRemoteCommandService implements IMCPToolService {

	/**
	 * Default pool size for Windows remote commands.
	 */
	private static final int DEFAULT_WINDOWS_POOL_SIZE = 60;

	/**
	 * Default timeout in seconds used when executing the Windows remote command.
	 */
	private static final long DEFAULT_COMMANDLINE_TIMEOUT = 10L;

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteWinRemoteCommandService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteWinRemoteCommandService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes a Windows remote OS command (CMD command) against the specified host(s) using the requested protocol extension (WMI or WinRM).
	 * The method looks up the extension by its type, prepares a valid configuration for the host, and
	 * returns the raw response produced by the extension. If the extension is not available or no valid
	 * configuration can be prepared, the response contains an error message instead of data.
	 *
	 * @param hostnames  the target host(s) on which to execute the command
	 * @param commandline the Windows OS command (CMD command) to execute
	 * @param protocol  the protocol identifier (WMI or WinRM)
	 * @param timeout   the timeout for the command execution in seconds (default: 10s)
	 * @param poolSize  optional pool size for concurrent Windows remote commands; defaults to {@value #DEFAULT_WINDOWS_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a {@link MultiHostToolResponse} containing the extension response or an error
	 */
	@Tool(
		name = "ExecuteWinRemoteCommand",
		description = """
		Execute a Windows OS command (CMD command) on one or more Windows hosts using WMI or WinRM protocol and return the command output.
		Supports an optional timeout (default 10s) and a pool size to control parallel execution.
		"""
	)
	public MultiHostToolResponse<QueryResponse> executeQuery(
		@ToolParam(description = "The hostname(s) to execute Windows remote command on.", required = true) final List<
			String
		> hostnames,
		@ToolParam(
			description = "The Windows OS command (CMD command) to execute.",
			required = true
		) final String commandline,
		@ToolParam(description = "The protocol to use (WMI or WinRM).", required = true) final String protocol,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent Windows remote commands. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		// If remote Windows isn't enabled for the MCP tools, return an error message.
		if (!isWinRemoteEnabledForMCP()) {
			return MultiHostToolResponse.buildError("The remote Windows connections are disabled for MCP.");
		}

		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_WINDOWS_POOL_SIZE);

		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(protocol)
			.map((IProtocolExtension extension) ->
				executeForHosts(
					hostnames,
					this::buildNullHostnameResponse,
					host ->
						HostToolResponse
							.<QueryResponse>builder()
							.hostname(host)
							.response(executeWindowsRemoteCommandWithExtensionSafe(extension, host, commandline, timeout))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError("No Extension found for remote Windows commands."));
	}

	private QueryResponse executeWindowsRemoteCommandWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String commandline,
		final Long timeout
	) {
		// Try to retrieve a Windows remote configuration for the host
		return MCPConfigHelper
			.resolveAllHostConfigurationCopiesFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map((IConfiguration configurationCopy) ->
				executeQuerySafe(extension, configurationCopy, hostname, commandline, timeout)
			)
			.orElseGet(() ->
				QueryResponse
					.builder()
					.error("No valid configuration found for remote Windows on %s.".formatted(hostname))
					.build()
			);
	}

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

		// Create a json node and populate it with the command and queryType (matching WinRemoteCli structure)
		final var queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(commandline));
		queryNode.set("queryType", new TextNode("winremote"));

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

	static boolean isWinRemoteEnabledForMCP() {
		return Boolean.getBoolean("metricshub.mcp.tool.win.remote.enabled");
	}
}
