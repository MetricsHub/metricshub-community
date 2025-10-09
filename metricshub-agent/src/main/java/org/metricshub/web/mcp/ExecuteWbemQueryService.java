package org.metricshub.web.mcp;

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

@Service
/**
 * Executes WBEM (CIM-XML) queries against a host using the WBEM protocol extension.
 * Selects a valid configuration from the agent context, injects namespace and optional vCenter,
 * applies runtime parameters, and delegates execution to the extension. Errors are returned
 * in the {@link QueryResponse}.
 */
public class ExecuteWbemQueryService {

	/**
	 * Default timeout in seconds used when executing the WBEM query.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * Default WBEM namespace used when none is supplied.
	 */
	private static final String DEFAULT_NAMESPACE = "root/cimv2";

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteWbemQueryService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteWbemQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes a WBEM (CIM-XML) query for the given host and namespace. Accepts a hostname and
	 * query string as required inputs. Uses {@code root/cimv2} as the default namespace when
	 * none is provided. Supports optional vCenter context and an optional timeout in seconds.
	 * Returns the result produced by the CIM provider, or an error if the query cannot be executed.
	 *
	 * @param hostname       target host
	 * @param query          WBEM (CIM-XML) query text
	 * @param namespace      CIM namespace; defaults to {@code root/cimv2} when null or blank
	 * @param vcenter        optional vCenter context
	 * @param timeout        optional timeout in seconds
	 * @return provider result or error wrapped in {@link QueryResponse}
	 */
	@Tool(
		name = "ExecuteWbemQuery",
		description = """
		Executes a WBEM (CIM-XML) query on a specified host and namespace.
		Accepts a hostname and query string as required inputs. Uses "root/cimv2" as the default
		namespace when none is provided. Supports optional vCenter context and timeout overrides.
		Returns the result produced by the CIM provider, or an error if the query cannot be executed.
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute Wbem query on.", required = true) final String hostname,
		@ToolParam(description = "The Wbem query to execute.", required = true) final String query,
		@ToolParam(description = "The namespace to use (default: root/cimv2).", required = false) final String namespace,
		@ToolParam(description = "Optional vCenter to use for session brokering.", required = false) final String vcenter,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("wbem")
			.map((IProtocolExtension extension) ->
				executeWbemQueryWithExtensionSafe(extension, hostname, query, namespace, vcenter, timeout)
			)
			.orElse(QueryResponse.builder().isError("No Extension found for Wbem protocol.").build());
	}

	/**
	 * Performs query execution with WBEM protocol extension. Builds a suitable configuration,
	 * applies hostname and timeout, constructs the query payload, and invokes the extension.
	 * Returns an error response when no configuration can be prepared or the extension fails.
	 *
	 * @param extension protocol extension used to validate and execute
	 * @param hostname    target host
	 * @param query       WBEM query text
	 * @param namespace   CIM namespace or null for default
	 * @param vCenter     optional vCenter context
	 * @param timeout     optional timeout in seconds
	 * @return provider result or error response
	 */
	QueryResponse executeWbemQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String query,
		final String namespace,
		final String vCenter,
		final Long timeout
	) {
		// Try to retrieve a Wbem configuration for the host
		final Optional<IConfiguration> maybeConfiguration = MCPConfigHelper
			.resolveAllHostConfigurationsFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.map(configCandidate ->
				this.buildConfigurationWithNamespaceAndVcenter(extension, configCandidate, namespace, vCenter)
			)
			.flatMap(Optional::stream)
			.findFirst();

		// Early return if no Wbem configuration is found for the given host.
		if (maybeConfiguration.isEmpty()) {
			return QueryResponse.builder().isError("No configuration found.").build();
		}

		// Retrieve the valid configuration from maybe configuration.
		final IConfiguration validConfiguration = maybeConfiguration.get();

		// add hostname and timeout to the valid configuration
		validConfiguration.setHostname(hostname);
		validConfiguration.setTimeout(timeout != null && timeout > 0 ? timeout : DEFAULT_QUERY_TIMEOUT);

		// Create a Json node and populate it with the query
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
	 * Builds a configuration derived from the supplied candidate by injecting the provided namespace and vcenter then
	 * delegating to {@link IProtocolExtension#buildConfiguration(String, ObjectNode, Object)}. When the
	 * extension rejects the candidate or any error occurs during the build process, the method returns
	 * an empty {@link Optional}.
	 *
	 * @param extension     the protocol extension responsible for building the final configuration
	 * @param configuration the base configuration candidate
	 * @param namespace     the namespace to set on the configuration, for example {@code root\\cimv2}
	 * @param vCenter       the optional vcenter context
	 * @return an optional containing the built configuration, or empty if the build fails
	 */
	Optional<IConfiguration> buildConfigurationWithNamespaceAndVcenter(
		final IProtocolExtension extension,
		final IConfiguration configuration,
		final String namespace,
		final String vCenter
	) {
		final ObjectMapper mapper = new ObjectMapper();

		// extract an ObjectNode from the IConfiguration
		final ObjectNode configurationNode = mapper.valueToTree(configuration);

		// Inject the namespace and vcenter into the configuration ObjectNode
		configurationNode.set(
			"namespace",
			new TextNode(namespace != null && !namespace.isBlank() ? namespace : DEFAULT_NAMESPACE)
		);

		// Inject vCenter if it isn't blank nor null
		if (vCenter != null && !vCenter.isBlank()) {
			configurationNode.set("vcenter", new TextNode(vCenter));
		}

		try {
			// Try to build an IConfiguration from the modified ObjectNode.
			return Optional.of(extension.buildConfiguration(extension.getIdentifier(), configurationNode, null));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
