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
import com.fasterxml.jackson.databind.node.LongNode;
import java.util.List;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Ping tool for checking the reachability of a host.
 */
@Service
public class PingToolService implements IMCPToolService {

	/**
	 * Default timeout for ping operations in seconds.
	 */
	private static final long DEFAULT_PING_TIMEOUT = 4L;

	/**
	 * Default thread pool size for ping operations.
	 */
	private static final int DEFAULT_PING_POOL_SIZE = 60;

	/**
	 * The type of the ping extension.
	 */
	private static final String PING_EXTENSION_TYPE = "ping";

	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for PingToolService.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public PingToolService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Pings one or more hosts with a specified timeout to check if they are reachable.
	 *
	 * @param hostname the hostnames to ping
	 * @param timeout  the timeout for the ping operation in seconds
	 * @param poolSize      optional pool size for concurrent ICMP queries; defaults to {@value #DEFAULT_PING_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a list of ProtocolCheckResponse results for each hostname
	 */
	@Tool(
		description = """
		Ping one or more hosts to check if they are reachable.
		Returns a list of ProtocolCheckResponse entries with the hostname,
		duration of the ping in milliseconds, and whether it is reachable or not.
		""",
		name = "PingHost"
	)
	public MultiHostToolResponse<ProtocolCheckResponse> pingHost(
		@ToolParam(description = "The hostname(s) to ping, provided as a list of strings") final List<String> hostname,
		@ToolParam(description = "The timeout for the ping operation in seconds", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent ping execution. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		final long resolvedTimeout = timeout != null ? timeout : DEFAULT_PING_TIMEOUT;
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_PING_POOL_SIZE);

		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(PING_EXTENSION_TYPE)
			.map((IProtocolExtension extension) ->
				executeForHosts(
					hostname,
					this::buildNullHostnameResponse,
					host ->
						HostToolResponse
							.<ProtocolCheckResponse>builder()
							.hostname(host)
							.response(pingHostWithExtensionSafe(host, resolvedTimeout, extension))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError("The ping extension is not available"));
	}

	/**
	 * Builds a response entry for a null hostname value.
	 *
	 * @return a {@link HostToolResponse} containing an error message indicating the hostname is missing
	 */
	private HostToolResponse<ProtocolCheckResponse> buildNullHostnameResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() ->
			ProtocolCheckResponse.builder().errorMessage(NULL_HOSTNAME_ERROR).build()
		);
	}

	/**
	 * Pings a host using the specified protocol extension.
	 *
	 * @param hostname  the hostname to ping
	 * @param timeout   the timeout for the ping operation in seconds
	 * @param extension the protocol extension to use for pinging
	 * @return a ProtocolCheckResponse containing the hostname, response time, and whether it is reachable or not
	 */
	private static ProtocolCheckResponse pingHostWithExtensionSafe(
		final String hostname,
		final long timeout,
		final IProtocolExtension extension
	) {
		try {
			// Create and fill in a configuration ObjectNode
			final var configurationNode = JsonNodeFactory.instance.objectNode();
			configurationNode.set("timeout", new LongNode(timeout));

			// Build an IConfiguration from the configuration ObjectNode
			final IConfiguration configuration = extension.buildConfiguration(PING_EXTENSION_TYPE, configurationNode, null);
			configuration.setHostname(hostname);

			configuration.validateConfiguration(hostname);

			final long startTime = System.currentTimeMillis();
			final String result = extension.executeQuery(configuration, null);
			final long duration = (System.currentTimeMillis() - startTime);

			return ProtocolCheckResponse
				.builder()
				.hostname(hostname)
				.responseTime(duration)
				.isReachable("true".equalsIgnoreCase(result))
				.build();
		} catch (Exception e) {
			// Error
			return ProtocolCheckResponse
				.builder()
				.hostname(hostname)
				.errorMessage("Error detected during host check: " + e.getMessage())
				.build();
		}
	}
}
