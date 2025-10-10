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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that executes ad-hoc JMX requests through the MetricsHub agent.
 * It finds a valid JMX configuration for a host, applies runtime parameters,
 * builds the JMX query payload, and delegates execution to the JMX extension.
 */
public class ExecuteJmxQueryService {

	/**
	 * Default timeout in seconds used when executing the JMX request.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteJmxQueryService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteJmxQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes a JMX request on the given host.
	 * <p>
	 * Resolves a valid JMX configuration from the agent context, applies hostname
	 * and timeout, builds the request (object name, attributes, key properties),
	 * and returns the provider response or an error.
	 * </p>
	 *
	 * @param hostname      the target host
	 * @param objectName    MBean object name (pattern allowed)
	 * @param attributes    comma-separated attributes to read
	 * @param keyProperties comma-separated key properties to include
	 * @param timeout       timeout in seconds; defaults to 10 if {@code null} or ≤ 0
	 * @return {@link QueryResponse} with the provider payload on success, or an error message
	 */
	@Tool(
		name = "ExecuteJmxQuery",
		description = """
		Execute a JMX request on a host using the agent JMX extension.
		Resolve a valid configuration from context, set hostname and timeout (default 10s),
		build the request (objectName, attributes, keyProperties), execute, and return the result or an error.
		The query is valid only if at least one attribute or one key property is specified, even if those parameters are optional.
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute JMX query on.", required = true) final String hostname,
		@ToolParam(description = "The MBean object name pattern.", required = true) final String objectName,
		@ToolParam(
			description = "Comma-separated list of attributes to fetch from the MBean.",
			required = false
		) final String attributes,
		@ToolParam(
			description = "Comma-separated list of key properties to include in the result set.",
			required = false
		) final String keyProperties,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("jmx")
			.map((IProtocolExtension extension) ->
				executeJmxQueryWithExtensionSafe(extension, hostname, objectName, attributes, keyProperties, timeout)
			)
			.orElse(QueryResponse.builder().isError("No Extension found for JMX.").build());
	}

	/**
	 * Resolves a valid JMX configuration for {@code hostname}, applies runtime
	 * parameters, constructs the JMX query node (objectName, attributes,
	 * keyProperties), and executes it.
	 *
	 * @param extension     JMX protocol extension used to run the request
	 * @param hostname      target host
	 * @param objectName    MBean object name or pattern
	 * @param attributes    comma-separated attributes to read; may be {@code null}
	 * @param keyProperties comma-separated key properties; may be {@code null}
	 * @param timeout       timeout in seconds; defaults to
	 *                      {@value #DEFAULT_QUERY_TIMEOUT} when {@code null} or ≤ 0
	 * @return {@link QueryResponse} containing the provider result or an error
	 */
	QueryResponse executeJmxQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String objectName,
		final String attributes,
		final String keyProperties,
		final Long timeout
	) {
		// Try to retrieve a JMX configuration for the host
		final Optional<IConfiguration> maybeConfiguration = MCPConfigHelper
			.resolveAllHostConfigurationsFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst();

		// Early return if no JMX configuration is found for the given host.
		if (maybeConfiguration.isEmpty()) {
			return QueryResponse.builder().isError("No configuration found for JMX on %s.".formatted(hostname)).build();
		}

		// Retrieve the valid configuration from maybe configuration.
		final IConfiguration validConfiguration = maybeConfiguration.get();

		// add hostname and timeout to the valid configuration
		validConfiguration.setHostname(hostname);
		validConfiguration.setTimeout(timeout != null && timeout > 0 ? timeout : DEFAULT_QUERY_TIMEOUT);

		// Create an ObjectNode that will contain query attributes.
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("objectName", new TextNode(objectName));

		// Create an arrayNode that will contain MBean attributes.
		final ArrayNode attributesNode = JsonNodeFactory.instance.arrayNode();

		Stream.ofNullable(attributes)
			.flatMap(attr -> Arrays.stream(attr.split(",")))
			.map(String::trim)
			.filter(attr -> !attr.isBlank())
			.forEach(attributesNode::add);

		// Create an arrayNode that will contain MBean key properties
		final ArrayNode keyPropertiesNode = JsonNodeFactory.instance.arrayNode();

		Stream.ofNullable(keyProperties)
			.flatMap(properties -> Arrays.stream(properties.split(",")))
			.map(String::trim)
			.filter(property -> !property.isBlank())
			.forEach(keyPropertiesNode::add);

		if (!attributesNode.isEmpty()) {
			queryNode.set("attributes", attributesNode);
		}

		if (!keyPropertiesNode.isEmpty()) {
			queryNode.set("keyProperties", keyPropertiesNode);
		}

		// At least one attribute or one key property must be specified.
		if (!queryNode.has("keyProperties") && !queryNode.has("attributes")) {
			return QueryResponse
				.builder()
				.isError("At least one attribute or key property must be specified for JMX query.")
				.build();
		}

		try {
			return QueryResponse.builder().response(extension.executeQuery(validConfiguration, queryNode)).build();
		} catch (Exception e) {
			return QueryResponse
				.builder()
				.isError("An error has occurred when executing the JMX query: %s".formatted(e.getMessage()))
				.build();
		}
	}
}
