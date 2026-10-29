package org.metricshub.web.mcp.tool;

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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.mcp.MCPConfigHelper;
import org.metricshub.web.mcp.QueryResponse;
import org.springframework.ai.tool.ToolCallback;

/**
 * The tools the protocol extensions declare through {@link IProtocolExtension#getTools()}, one MCP tool each,
 * named {@code <extension>_<tool>}. On each host the tool runs with the host's configuration for that extension,
 * through {@link IProtocolExtension#executeTool(String, IConfiguration, com.fasterxml.jackson.databind.JsonNode)}.
 * Every tool gains a {@code timeout} parameter, applied to the host's configuration before the extension runs.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtocolToolCallbacks {

	/**
	 * The parameter overriding the host's configured timeout, in seconds.
	 */
	public static final String TIMEOUT = "timeout";

	/**
	 * @param agentContextHolder the running agent context
	 * @return the tools of every loaded protocol extension, destructive ones only when allowed
	 */
	public static List<ToolCallback> from(final AgentContextHolder agentContextHolder) {
		final List<ToolCallback> callbacks = new ArrayList<>();
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getExtensionManager() == null) {
			return callbacks;
		}
		final boolean destructiveEnabled = HostTools.isDestructiveEnabled();
		for (final IProtocolExtension extension : agentContext.getExtensionManager().getProtocolExtensions()) {
			for (final ToolDefinition definition : extension.getTools()) {
				if (definition.destructive() && !destructiveEnabled) {
					continue;
				}
				try {
					callbacks.add(
						HostTools.perHost(
							HostTools.toolName(extension.getIdentifier(), definition.name()),
							withTimeout(definition),
							(hostname, arguments) -> execute(extension, definition.name(), hostname, arguments, agentContextHolder)
						)
					);
				} catch (IllegalArgumentException e) {
					// One bad declaration must not take the extension's other tools down
					log.warn(
						"Extension {}: tool {} is not exposed: {}",
						extension.getIdentifier(),
						definition.name(),
						e.getMessage()
					);
				}
			}
		}
		return callbacks;
	}

	/**
	 * @param definition a tool as the extension declares it
	 * @return the same tool with the {@code timeout} parameter the agent handles
	 */
	static ToolDefinition withTimeout(final ToolDefinition definition) {
		if (definition.parameters().containsKey(TIMEOUT)) {
			throw new IllegalArgumentException(
				"Tool %s declares the parameter %s, which the agent reserves for the host's timeout".formatted(
					definition.name(),
					TIMEOUT
				)
			);
		}
		final Map<String, ToolParameter> parameters = new LinkedHashMap<>(definition.parameters());
		parameters.put(
			TIMEOUT,
			new ToolParameter("integer", "Timeout in seconds. Defaults to the host's configured timeout.", false, null)
		);
		return new ToolDefinition(definition.name(), definition.description(), parameters, definition.destructive());
	}

	/**
	 * Runs a tool on one host with the first of the host's configurations the extension accepts.
	 *
	 * @param extension          the extension declaring the tool
	 * @param toolName           the tool name within the extension
	 * @param hostname           the host
	 * @param arguments          the resolved arguments, the {@code timeout} among them
	 * @param agentContextHolder the running agent context, where the host's configurations are
	 * @return the extension's result, or an error
	 */
	static QueryResponse execute(
		final IProtocolExtension extension,
		final String toolName,
		final String hostname,
		final Map<String, String> arguments,
		final AgentContextHolder agentContextHolder
	) {
		final Optional<IConfiguration> maybeConfiguration = MCPConfigHelper.resolveAllHostConfigurationCopiesFromContext(
			hostname,
			agentContextHolder
		)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst();
		if (maybeConfiguration.isEmpty()) {
			return QueryResponse.builder()
				.error("No valid %s configuration found for %s.".formatted(extension.getIdentifier(), hostname))
				.build();
		}
		final IConfiguration configuration = maybeConfiguration.get();
		configuration.setHostname(hostname);

		final Map<String, String> toolArguments = new LinkedHashMap<>(arguments);
		final String timeout = toolArguments.remove(TIMEOUT);
		if (timeout != null) {
			try {
				configuration.setTimeout(Long.parseLong(timeout.trim()));
			} catch (NumberFormatException e) {
				return QueryResponse.builder().error("The timeout must be a number of seconds: %s".formatted(timeout)).build();
			}
		}

		final ObjectNode argumentsNode = JsonNodeFactory.instance.objectNode();
		toolArguments.forEach(argumentsNode::put);

		try {
			return QueryResponse.builder().response(extension.executeTool(toolName, configuration, argumentsNode)).build();
		} catch (Exception e) {
			return QueryResponse.builder()
				.error("An error has occurred when running the tool: %s".formatted(e.getMessage()))
				.build();
		}
	}
}
