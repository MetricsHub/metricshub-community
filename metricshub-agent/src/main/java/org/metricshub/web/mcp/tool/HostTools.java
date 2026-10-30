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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.IMCPToolService;
import org.metricshub.web.mcp.MultiHostToolExecutor;
import org.metricshub.web.mcp.MultiHostToolResponse;
import org.metricshub.web.mcp.QueryResponse;
import org.springframework.ai.tool.ToolCallback;

/**
 * Turns an engine {@link ToolDefinition}, which knows nothing about hosts, into an MCP tool that runs on one or
 * several configured hosts: the tool gains a {@code hostname} list and a {@code poolSize}, and its body runs once
 * per host through {@link MultiHostToolExecutor}, each host answering in its own entry.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HostTools {

	/**
	 * The parameter carrying the hosts to run the tool on.
	 */
	public static final String HOSTNAME = "hostname";

	/**
	 * The parameter bounding the concurrency over the hosts.
	 */
	public static final String POOL_SIZE = "poolSize";

	/**
	 * The JVM flag that lets destructive tools, those that change the state of a host, be advertised.
	 */
	public static final String DESTRUCTIVE_TOOLS_FLAG = "metricshub.mcp.tool.destructive.enabled";

	static final int DEFAULT_POOL_SIZE = 60;

	/**
	 * The longest name a function may have for the OpenAI API, which the chat sends every tool to.
	 */
	static final int MAX_NAME_LENGTH = 64;

	/**
	 * The parameter names the agent adds to every tool, which a tool may not declare itself.
	 */
	static final List<String> RESERVED_PARAMETERS = List.of(HOSTNAME, POOL_SIZE);

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	/**
	 * @return whether destructive tools may be advertised, i.e. {@value #DESTRUCTIVE_TOOLS_FLAG} is set
	 */
	public static boolean isDestructiveEnabled() {
		return Boolean.getBoolean(DESTRUCTIVE_TOOLS_FLAG);
	}

	/**
	 * Builds the advertised name of a tool from its owner and its own name, keeping to the characters MCP
	 * accepts in a tool name.
	 *
	 * @param owner the extension identifier or the connector identifier
	 * @param name  the tool name within its owner
	 * @return {@code owner_name}, any other character replaced by an underscore
	 */
	public static String toolName(final String owner, final String name) {
		return (owner + "_" + name).replaceAll("[^A-Za-z0-9_-]", "_");
	}

	/**
	 * Builds the MCP tool.
	 *
	 * @param name       the advertised tool name
	 * @param definition what the tool takes and does
	 * @param perHost    the body, run once per host with the resolved arguments; it returns the host's result or
	 *                   throws, in which case the host answers with an error
	 * @return the callback
	 */
	public static ToolCallback perHost(
		final String name,
		final ToolDefinition definition,
		final BiFunction<String, Map<String, String>, Object> perHost
	) {
		if (name.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException(
				"Tool name %s is longer than the %d characters a function name may have".formatted(name, MAX_NAME_LENGTH)
			);
		}
		for (final String reserved : RESERVED_PARAMETERS) {
			if (definition.parameters().containsKey(reserved)) {
				throw new IllegalArgumentException(
					"Tool %s declares the parameter %s, which the agent reserves for the host selection".formatted(name, reserved)
				);
			}
		}
		final org.springframework.ai.tool.definition.ToolDefinition springDefinition =
			org.springframework.ai.tool.definition.ToolDefinition.builder()
				.name(name)
				.description(description(definition))
				.inputSchema(inputSchema(definition).toString())
				.build();
		return new DynamicToolCallback(
			springDefinition,
			arguments -> call(definition, perHost, arguments),
			definition.destructive()
		);
	}

	static String description(final ToolDefinition definition) {
		final String description =
			definition.description() == null || definition.description().isBlank()
				? definition.name()
				: definition.description();
		return definition.destructive() ? "DESTRUCTIVE: " + description : description;
	}

	/**
	 * @param definition the tool
	 * @return the JSON schema of the arguments: the hosts, the tool's own parameters and the pool size
	 */
	static ObjectNode inputSchema(final ToolDefinition definition) {
		final ObjectNode properties = NODES.objectNode();
		final ArrayNode required = NODES.arrayNode();

		properties
			.putObject(HOSTNAME)
			.put("type", "array")
			.put("description", "The hostname(s) to run the tool on, as configured in MetricsHub.")
			.putObject("items")
			.put("type", "string");
		required.add(HOSTNAME);

		definition
			.parameters()
			.forEach((parameterName, parameter) -> {
				final ObjectNode property = properties.putObject(parameterName).put("type", parameter.type());
				final StringBuilder description = new StringBuilder();
				if (parameter.description() != null) {
					description.append(parameter.description());
				}
				if (parameter.defaultValue() != null) {
					description.append(description.isEmpty() ? "" : " ").append("Default: ").append(parameter.defaultValue());
				}
				if (!description.isEmpty()) {
					property.put("description", description.toString());
				}
				if (parameter.required() && parameter.defaultValue() == null) {
					required.add(parameterName);
				}
			});

		properties
			.putObject(POOL_SIZE)
			.put("type", "integer")
			.put("description", "Optional number of hosts run concurrently. Defaults to " + DEFAULT_POOL_SIZE + ".");

		final ObjectNode schema = NODES.objectNode().put("type", "object");
		schema.set("properties", properties);
		schema.set("required", required);
		return schema;
	}

	private static MultiHostToolResponse<Object> call(
		final ToolDefinition definition,
		final BiFunction<String, Map<String, String>, Object> perHost,
		final JsonNode arguments
	) {
		final List<String> hostnames = hostnames(arguments.get(HOSTNAME));
		if (hostnames.isEmpty()) {
			return MultiHostToolResponse.buildError("The hostname parameter is required.");
		}

		final Map<String, String> resolvedArguments;
		try {
			resolvedArguments = definition.resolveArguments(declaredArguments(definition, arguments));
		} catch (IllegalArgumentException e) {
			return MultiHostToolResponse.buildError(e.getMessage());
		}

		final JsonNode poolSizeNode = arguments.get(POOL_SIZE);
		final int poolSize = NumberHelper.getPositiveOrDefault(
			poolSizeNode == null || !poolSizeNode.canConvertToInt() ? null : poolSizeNode.intValue(),
			DEFAULT_POOL_SIZE
		).intValue();

		return MultiHostToolExecutor.executeForHosts(
			hostnames,
			() ->
				HostToolResponse.builder()
					.response(QueryResponse.builder().error(IMCPToolService.NULL_HOSTNAME_ERROR).build())
					.build(),
			hostname ->
				HostToolResponse.builder().hostname(hostname).response(runSafe(perHost, hostname, resolvedArguments)).build(),
			poolSize
		);
	}

	private static Object runSafe(
		final BiFunction<String, Map<String, String>, Object> perHost,
		final String hostname,
		final Map<String, String> arguments
	) {
		try {
			return perHost.apply(hostname, arguments);
		} catch (Exception e) {
			return QueryResponse.builder()
				.error("An error has occurred when running the tool: %s".formatted(messageOf(e)))
				.build();
		}
	}

	private static String messageOf(final Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
	}

	/**
	 * @param node the {@code hostname} argument: an array of names, or a single name
	 * @return the hostnames, a {@code null} entry kept so that it is reported as such
	 */
	private static List<String> hostnames(final JsonNode node) {
		final List<String> hostnames = new ArrayList<>();
		if (node == null || node.isNull()) {
			return hostnames;
		}
		if (node.isArray()) {
			node.forEach(entry -> hostnames.add(entry.isNull() ? null : entry.asText()));
		} else {
			hostnames.add(node.asText());
		}
		return hostnames;
	}

	/**
	 * @param definition the tool
	 * @param arguments  the arguments of the invocation
	 * @return the values of the declared parameters, as text, in declaration order
	 */
	private static Map<String, String> declaredArguments(final ToolDefinition definition, final JsonNode arguments) {
		final Map<String, String> values = new LinkedHashMap<>();
		for (final Map.Entry<String, ToolParameter> parameter : definition.parameters().entrySet()) {
			final JsonNode value = arguments.get(parameter.getKey());
			if (value != null && !value.isNull()) {
				values.put(parameter.getKey(), value.isValueNode() ? value.asText() : value.toString());
			}
		}
		return values;
	}
}
