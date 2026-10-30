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

import com.fasterxml.jackson.databind.JsonNode;
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
	 * @param agentContext the agent context to build the tools from, the one every callback stays on
	 * @return the tools of every loaded protocol extension, destructive ones only when allowed
	 */
	public static List<ToolCallback> from(final AgentContext agentContext) {
		final List<ToolCallback> callbacks = new ArrayList<>();
		if (agentContext == null || agentContext.getExtensionManager() == null) {
			return callbacks;
		}
		for (final IProtocolExtension extension : agentContext.getExtensionManager().getProtocolExtensions()) {
			for (final ToolDefinition definition : extension.getTools()) {
				try {
					callbacks.add(
						HostTools.perHost(
							HostTools.toolName(extension.getIdentifier(), definition.name()),
							withTimeout(definition),
							(hostname, arguments) -> execute(extension, definition, hostname, arguments, agentContext)
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
			new ToolParameter(
				"integer",
				"Timeout in seconds, at least 1. Defaults to the host's configured timeout.",
				false,
				null
			)
		);
		return new ToolDefinition(definition.name(), definition.description(), parameters, definition.destructive());
	}

	/**
	 * Runs a tool on one host with the first of the host's configurations the extension accepts.
	 *
	 * @param extension    the extension declaring the tool
	 * @param definition   the tool as the extension declares it, whose parameter types the arguments take
	 * @param hostname     the host
	 * @param arguments    the resolved arguments, the {@code timeout} among them
	 * @param agentContext the agent context the tool was built from, where the host's configurations are
	 * @return the extension's result, or an error
	 */
	static QueryResponse execute(
		final IProtocolExtension extension,
		final ToolDefinition definition,
		final String hostname,
		final Map<String, String> arguments,
		final AgentContext agentContext
	) {
		final String toolName = definition.name();
		final Optional<IConfiguration> maybeConfiguration = MCPConfigHelper.resolveAllHostConfigurationCopies(
			hostname,
			agentContext
		)
			.stream()
			.filter(extension::isToolConfiguration)
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
			final long seconds;
			try {
				seconds = Long.parseLong(timeout.trim());
			} catch (NumberFormatException e) {
				return invalidTimeout(timeout);
			}
			if (seconds <= 0) {
				// Zero is "no timeout" to some clients and "invalid" to some extensions: neither is wanted here
				return invalidTimeout(timeout);
			}
			configuration.setTimeout(seconds);
		}

		// Typed as declared: an extension reading an integer or a boolean gets one, not its text
		final ObjectNode argumentsNode = JsonNodeFactory.instance.objectNode();
		toolArguments.forEach((name, value) ->
			argumentsNode.set(name, typedValue(definition.parameters().get(name), value))
		);
		return runSafe(extension, toolName, configuration, argumentsNode);
	}

	/**
	 * @param parameter the declared parameter, {@code null} when the argument is not declared
	 * @param value     the argument as text
	 * @return the argument as a JSON value of the declared type, as text when it does not parse as one
	 */
	static JsonNode typedValue(final ToolParameter parameter, final String value) {
		final JsonNodeFactory nodes = JsonNodeFactory.instance;
		final String type = parameter == null ? ToolParameter.STRING : parameter.type();
		try {
			return switch (type) {
				case "integer" -> nodes.numberNode(Long.parseLong(value.trim()));
				case "number" -> nodes.numberNode(Double.parseDouble(value.trim()));
				case "boolean" -> nodes.booleanNode(Boolean.parseBoolean(value.trim()));
				default -> nodes.textNode(value);
			};
		} catch (NumberFormatException e) {
			return nodes.textNode(value);
		}
	}

	private static QueryResponse invalidTimeout(final String timeout) {
		return QueryResponse.builder()
			.error("The timeout must be a positive number of seconds: %s".formatted(timeout))
			.build();
	}

	private static QueryResponse runSafe(
		final IProtocolExtension extension,
		final String toolName,
		final IConfiguration configuration,
		final ObjectNode argumentsNode
	) {
		try {
			return QueryResponse.builder().response(extension.executeTool(toolName, configuration, argumentsNode)).build();
		} catch (Exception e) {
			return QueryResponse.builder()
				.error("An error has occurred when running the tool: %s".formatted(e.getMessage()))
				.build();
		}
	}
}
