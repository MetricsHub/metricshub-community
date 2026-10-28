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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.configuration.IConfiguration;
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

	private static final String RAW_QUERY = "raw";

	private static final String CHASSIS_QUERY = "chassis";

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
	 * @param poolSize optional pool size for concurrent IPMI queries; defaults to {@value #DEFAULT_IPMI_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a {@link QueryResponse} containing the provider payload or an error message
	 */
	@Tool(
		name = "ExecuteIpmiQuery",
		description = """
		Execute an IPMI query on the given host(s) using the agent’s IPMI extension.
		Resolves a valid configuration from context, applies hostname and timeout (default 10s),
		executes the query, and returns the provider result or an error.
		Queries: 'sensors' (default) returns the chassis status, FRUs and sensors; 'sel' returns the System
		Event Log as CSV with a header line, the whole log or only the entries of the last 'since' duration;
		'raw' sends one raw command (netFn, command, data) and returns 'completionCode;data' in hex; raw
		commands can change the BMC state and are disabled by default (JVM flag metricshub.mcp.tool.ipmi.raw.enabled).
		'chassis' runs an action: 'status' (the power state), 'on', 'off', 'cycle', 'reset', 'soft', 'diag'
		(power control) or 'identify' (the chassis LED, for 'seconds'); the actions other than 'status' act on
		the server and are disabled by default (JVM flag metricshub.mcp.tool.ipmi.chassis.enabled).
		Reading a large SEL takes several seconds per hundred entries: raise the timeout accordingly.
		"""
	)
	public MultiHostToolResponse<QueryResponse> executeQuery(
		@ToolParam(description = "The hostname(s) to execute IPMI query on.", required = true) final List<String> hostname,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent IPMI queries. Defaults to 60.",
			required = false
		) final Integer poolSize,
		@ToolParam(
			description = "Optional query: 'sensors' (default), 'sel' (System Event Log), 'raw' (raw command) or 'chassis' (power state or action).",
			required = false
		) final String query,
		@ToolParam(
			description = "For 'chassis': 'status' (default), 'on', 'off', 'cycle', 'reset', 'soft', 'diag' or 'identify'.",
			required = false
		) final String action,
		@ToolParam(
			description = "For 'chassis' 'identify': how long the LED stays on in seconds (default 15), 0 to turn it off, or 'forever'.",
			required = false
		) final String seconds,
		@ToolParam(
			description = "For 'sel': only the entries of the last duration, e.g. 30m, 1h, 2d, or seconds. Default: the whole log.",
			required = false
		) final String since,
		@ToolParam(
			description = "For 'raw': the network function, decimal or 0x hex, e.g. 0x06.",
			required = false
		) final String netFn,
		@ToolParam(description = "For 'raw': the command byte, e.g. 0x01.", required = false) final String command,
		@ToolParam(
			description = "For 'raw': the request bytes separated with spaces, e.g. '0x00 0x01'.",
			required = false
		) final String data
	) {
		// A raw command can change the BMC state: it needs the explicit opt-in of the administrator
		if (RAW_QUERY.equalsIgnoreCase(query == null ? null : query.trim()) && !isIpmiRawEnabledForMCP()) {
			return MultiHostToolResponse.buildError("The raw IPMI commands are disabled for MCP.");
		}

		// A chassis action other than the status acts on the server: same opt-in
		final boolean chassisAction =
			CHASSIS_QUERY.equalsIgnoreCase(query == null ? null : query.trim()) &&
			action != null &&
			!action.isBlank() &&
			!"status".equalsIgnoreCase(action.trim());
		if (chassisAction && !isIpmiChassisEnabledForMCP()) {
			return MultiHostToolResponse.buildError("The IPMI chassis actions are disabled for MCP.");
		}

		final JsonNode queryNode = buildQueryNode(query, since, netFn, command, data, action, seconds);
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
						HostToolResponse.<QueryResponse>builder()
							.hostname(host)
							.response(executeIpmiQueryWithExtensionSafe(extension, host, timeout, queryNode))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError("The IPMI extension is not available"));
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
	/**
	 * Builds the query node of the IPMI extension: {@code null} for the default sensors query.
	 *
	 * @param query   The query type, or {@code null}
	 * @param since   The duration of the SEL query, or {@code null}
	 * @param netFn   The network function of a raw command, or {@code null}
	 * @param command The command byte of a raw command, or {@code null}
	 * @param data    The request bytes of a raw command, or {@code null}
	 * @param action  The chassis action, or {@code null}
	 * @param seconds The duration of the chassis identify, or {@code null}
	 * @return the query node, or {@code null}
	 */
	static JsonNode buildQueryNode(
		final String query,
		final String since,
		final String netFn,
		final String command,
		final String data,
		final String action,
		final String seconds
	) {
		if (query == null || query.isBlank()) {
			return null;
		}
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.put("query", query);
		if (since != null) {
			queryNode.put("since", since);
		}
		if (netFn != null) {
			queryNode.put("netFn", netFn);
		}
		if (command != null) {
			queryNode.put("command", command);
		}
		if (data != null) {
			queryNode.put("data", data);
		}
		if (action != null) {
			queryNode.put("action", action);
		}
		if (seconds != null) {
			queryNode.put("seconds", seconds);
		}
		return queryNode;
	}

	/**
	 * Checks the JVM flag {@code metricshub.mcp.tool.ipmi.raw.enabled}: raw IPMI commands can power a server off,
	 * clear its event log or change its settings, so they stay off unless the administrator turns them on.
	 *
	 * @return {@code true} if raw IPMI commands are enabled for MCP; otherwise {@code false}.
	 */
	static boolean isIpmiRawEnabledForMCP() {
		return Boolean.getBoolean("metricshub.mcp.tool.ipmi.raw.enabled");
	}

	/**
	 * Checks the JVM flag {@code metricshub.mcp.tool.ipmi.chassis.enabled}: the chassis actions power the server
	 * on, off or cycle it, so they stay off unless the administrator turns them on.
	 *
	 * @return {@code true} if the IPMI chassis actions are enabled for MCP; otherwise {@code false}.
	 */
	static boolean isIpmiChassisEnabledForMCP() {
		return Boolean.getBoolean("metricshub.mcp.tool.ipmi.chassis.enabled");
	}

	QueryResponse executeIpmiQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final Long timeout,
		final JsonNode queryNode
	) {
		return MCPConfigHelper.resolveAllHostConfigurationCopiesFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map((IConfiguration configuration) -> {
				configuration.setTimeout(NumberHelper.getPositiveOrDefault(timeout, DEFAULT_QUERY_TIMEOUT).longValue());
				try {
					return QueryResponse.builder().response(extension.executeQuery(configuration, queryNode)).build();
				} catch (Exception e) {
					return QueryResponse.builder()
						.error("An error has occurred when executing the query: %s".formatted(e.getMessage()))
						.build();
				}
			})
			.orElseGet(() ->
				QueryResponse.builder().error("No IPMI configuration found for %s.".formatted(hostname)).build()
			);
	}

	/**
	 * Builds a {@link HostToolResponse} representing the error produced when a
	 * null hostname is encountered.
	 *
	 * @return a host-level response containing an error payload for the missing hostname
	 */
	private HostToolResponse<QueryResponse> buildNullHostnameResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() ->
			QueryResponse.builder().error(NULL_HOSTNAME_ERROR).build()
		);
	}
}
