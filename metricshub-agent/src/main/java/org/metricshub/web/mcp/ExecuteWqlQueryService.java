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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Optional;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that executes a WQL query through the given {@link IProtocolExtension}.
 * The service resolves host configurations from the agent context, keeps only those that the
 * extension accepts, injects the requested namespace, and then delegates execution to the extension.
 * Failures at any step are surfaced as an error in the returned {@link QueryResponse}.
 */
public class ExecuteWqlQueryService {

	/**
	 * Default timeout in seconds used when executing the query through the protocol extension.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * Default WMI Namespace
	 */
	private static final String DEFAULT_NAMESPACE = "root\\cimv2";

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteWqlService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteWqlQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes a WQL query against the specified host using the requested protocol extension. The method
	 * looks up the extension by its type, prepares a valid configuration for the host and namespace, and
	 * returns the raw response produced by the extension. If the extension is not available or no valid
	 * configuration can be prepared, the response contains an error message instead of data.
	 *
	 * @param hostname  the target host on which to execute the query
	 * @param protocol  the protocol identifier (for example, {@code "WMI"} or {@code "WinRm"})
	 * @param query     the WQL query string to execute
	 * @param namespace the WMI namespace to use
	 * @param timeout   the timeout for the query execution in seconds (default: 10s)
	 * @return a {@link QueryResponse} containing the extension response or an error
	 */
	@Tool(
		name = "ExecuteWqlQuery",
		description = """
		Executes a WQL query on a given Windows host using the specified protocol (WMI or WinRM) and namespace.
		Returns the result produced by the Windows provider, or an error if the query cannot be executed.
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute WQL query on.", required = true) final String hostname,
		@ToolParam(
			description = "The protocol to use to execute the WQL query (WMI or WinRm).",
			required = true
		) final String protocol,
		@ToolParam(description = "The WQL query to execute.", required = true) final String query,
		@ToolParam(description = "The namespace to use.", required = false) final String namespace,
		@ToolParam(description = "The timeout for the query in seconds.", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(protocol)
			.map((IProtocolExtension extension) ->
				executeQueryWithExtensionSafe(extension, hostname, query, namespace, timeout)
			)
			.orElse(QueryResponse.builder().isError("No Extension found for %s protocol.".formatted(protocol)).build());
	}

	/**
	 * Prepares a configuration accepted by the provided extension and performs the query execution.
	 * The method chooses the first configuration that the extension validates, enriches it with the
	 * requested namespace, applies runtime fields such as hostname and timeout, builds the query node,
	 * and finally delegates execution to the extension. Any exception raised by the extension is caught
	 * and translated into an error response.
	 *
	 * @param extension the protocol extension that will validate the configuration and execute the query
	 * @param hostname  the target host
	 * @param query     the WQL query string
	 * @param namespace the namespace to inject into the configuration
	 * @param timeout   the timeout for the query execution in seconds
	 * @return a {@link QueryResponse} containing either the extension result or an error
	 */
	private QueryResponse executeQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String query,
		final String namespace,
		final Long timeout
	) {
		final Optional<IConfiguration> maybeConfiguration = MCPConfigHelper
			.resolveAllHostConfigurationsFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.map(configCandidate -> this.buildConfigurationWithNamespace(extension, configCandidate, namespace))
			.flatMap(Optional::stream)
			.findFirst();

		if (maybeConfiguration.isEmpty()) {
			return QueryResponse.builder().isError("No configuration found.").build();
		}

		// Retrieve the valid configuration from maybe configuration.
		final IConfiguration validConfiguration = maybeConfiguration.get();

		// add hostname and timeout to the valid configuration
		validConfiguration.setHostname(hostname);
		validConfiguration.setTimeout(timeout != null && timeout > 0 ? timeout : DEFAULT_QUERY_TIMEOUT);

		// Create a json node and populate it with the query
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(query));

		try {
			return QueryResponse.builder().response(extension.executeQuery(validConfiguration, queryNode)).build();
		} catch (Exception e) {
			return QueryResponse
				.builder()
				.isError("An error has occurred when executing the query: %s".formatted(e.getMessage()))
				.build();
		}
	}

	/**
	 * Builds a configuration derived from the supplied candidate by injecting the provided namespace and
	 * delegating to {@link IProtocolExtension#buildConfiguration(String, ObjectNode, Object)}. When the
	 * extension rejects the candidate or any error occurs during the build process, the method returns
	 * an empty {@link Optional}.
	 *
	 * @param extension     the protocol extension responsible for building the final configuration
	 * @param configuration the base configuration candidate
	 * @param namespace     the namespace to set on the configuration, for example {@code root\\cimv2}
	 * @return an optional containing the built configuration, or empty if the build fails
	 */
	Optional<IConfiguration> buildConfigurationWithNamespace(
		final IProtocolExtension extension,
		final IConfiguration configuration,
		final String namespace
	) {
		final ObjectMapper mapper = new ObjectMapper();

		// extract an ObjectNode from the IConfiguration
		final ObjectNode configurationNode = mapper.valueToTree(configuration);

		// Inject the namespace into the configuration ObjectNode
		configurationNode.set(
			"namespace",
			new TextNode(namespace != null && !namespace.isBlank() ? namespace : DEFAULT_NAMESPACE)
		);

		try {
			// Try to build an IConfiguration from the modified ObjectNode.
			return Optional.of(extension.buildConfiguration(extension.getIdentifier(), configurationNode, null));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
