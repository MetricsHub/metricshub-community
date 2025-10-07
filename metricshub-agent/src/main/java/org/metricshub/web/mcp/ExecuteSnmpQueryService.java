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
import java.util.Set;
import java.util.function.Predicate;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that executes SNMP queries (GET/GETNEXT/WALK/TABLE) using the agent's SNMP extension.
 * Builds the appropriate JSON payload and returns a {@link QueryResponse} with either the result
 * or an error message when no configuration is found or execution fails.
 */
public class ExecuteSnmpQueryService {

	/**
	 * The set of SNMP accepted queries.
	 */
	private static final Set<String> SNMP_METHODS = Set.of("get", "getnext", "walk", "table");

	/**
	 * Default timeout of the query execution in seconds
	 */
	private static final Long DEFAULT_TIMEOUT = 10L;
	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteSnmpQueryService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteSnmpQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Executes an SNMP query on the given host.
	 *
	 * @param hostname  target host name
	 * @param queryType SNMP queries to execute: get, getNext, walk, or table
	 * @param oid       SNMP OID used in the request
	 * @param columns   comma-separated column indexes; required when {@code queryType} is {@code "table"}, ignored otherwise
	 * @param timeout   the timeout of the query execution in seconds
	 * @return a {@link QueryResponse} containing the result or an error message
	 */
	@Tool(
		name = "ExecuteSnmpQuery",
		description = """
		Executes an SNMP query (Get, GetNext, Walk, or Table) on a given hostname using the specified OID.
		For 'table' queries, comma-separated column indexes needs to be provided.
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute SNMP Query on.", required = true) final String hostname,
		@ToolParam(
			description = "The SNMP query to execute: Get, GetNext, Walk or Table.",
			required = true
		) final String queryType,
		@ToolParam(description = "The SNMP OID to use in the request.", required = true) final String oid,
		@ToolParam(description = "The columns to select on the SNMP Table query.", required = false) final String columns,
		@ToolParam(description = "The timeout for the query in seconds.", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("snmp")
			.map((IProtocolExtension extension) ->
				executeQueryWithExtension(extension, hostname.trim(), queryType.trim(), oid.trim(), columns, timeout)
			)
			.orElse(QueryResponse.builder().isError("SNMP Extension is not available").build());
	}

	/**
	 * Builds the SNMP query configuration and executes it with the provided extension.
	 *
	 * @param extension the SNMP protocol extension
	 * @param hostname  the target host
	 * @param queryType the SNMP query (get, getNext, walk, table)
	 * @param oid       the target OID
	 * @param columns   comma-separated list of integer column indexes; required when {@code queryType} is {@code "table"},
	 *                  ignored for all other actions
	 * @param timeout   the timeout of the query execution in seconds
	 * @return the {@link QueryResponse} with the execution result or an error
	 */
	private QueryResponse executeQueryWithExtension(
		final IProtocolExtension extension,
		final String hostname,
		final String queryType,
		final String oid,
		final String columns,
		final Long timeout
	) {
		// Make sure the selected SNMP method exists.
		if (!SNMP_METHODS.contains(queryType.toLowerCase())) {
			return QueryResponse
				.builder()
				.isError("Unknown SNMP query. Only Get, GetNext, Walk and Table are allowed.")
				.build();
		}

		// Create an ObjectNode containing all the SNMP query details.
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("action", new TextNode(queryType));
		queryNode.set("oid", new TextNode(oid));

		// add the columns if an SNMP table query is invoked.
		if (queryType.equalsIgnoreCase("table") && columns != null) {
			final ArrayNode columnsNode = JsonNodeFactory.instance.arrayNode();

			try {
				// Split the comma-separated columns string and add each column into the columns node.
				Arrays
					.stream(columns.split(","))
					.map(String::trim)
					.filter(Predicate.not(String::isEmpty))
					.mapToInt(Integer::parseInt)
					.forEach(columnsNode::add);

				// Add the columns node into the query node.
				queryNode.set("columns", columnsNode);
			} catch (Exception e) {
				return QueryResponse
					.builder()
					.isError("Exception occurred when parsing columns: %s".formatted(e.getMessage()))
					.build();
			}
		}

		// Execute the query using the SNMP Extension.
		return MCPConfigHelper
			.resolveAllHostConfigurationsFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map(configuration -> {
				try {
					configuration.setTimeout(timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT);
					final String result = extension.executeQuery(configuration, queryNode);
					return QueryResponse.builder().response(result).build();
				} catch (Exception e) {
					return QueryResponse
						.builder()
						.isError("failed to execute SNMP %s query on %s.".formatted(queryType, hostname))
						.build();
				}
			})
			.orElseGet(() ->
				QueryResponse.builder().isError("No SNMP configuration found for %s.".formatted(hostname)).build()
			);
	}
}
