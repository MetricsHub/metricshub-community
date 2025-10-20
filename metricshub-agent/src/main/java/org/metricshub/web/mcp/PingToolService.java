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
	 * Pings a host with a specified timeout to check if it is reachable.
	 *
	 * @param hostname the hostname to ping
	 * @param timeout  the timeout for the ping operation in seconds
	 * @return a ProtocolCheckResponse containing the hostname, response time, and whether it is reachable or not
	 */
	@Tool(
		description = "Ping a host to check if it is reachable. Returns a ProtocolCheckResponse with the hostname, duration of the ping in milliseconds, and whether it is reachable or not.",
		name = "PingHost"
	)
	public ProtocolCheckResponse pingHost(
		@ToolParam(description = "The hostname to ping") final String hostname,
		@ToolParam(description = "The timeout for the ping operation in seconds", required = false) final Long timeout
	) {
		return runPing(hostname, timeout != null ? timeout : DEFAULT_PING_TIMEOUT);
	}

	/**
	 * Pings a host with a specified timeout.
	 *
	 * @param hostname the hostname to ping
	 * @param timeout  the timeout for the ping operation in seconds
	 * @return a ProtocolCheckResponse containing the hostname, response time, and whether it is reachable or not
	 */
	private ProtocolCheckResponse runPing(final String hostname, final long timeout) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(PING_EXTENSION_TYPE)
			.map((IProtocolExtension extension) -> pingHostWithExtensionSafe(hostname, timeout, extension))
			.orElse(
				ProtocolCheckResponse.builder().hostname(hostname).errorMessage("The extension is not available").build()
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
