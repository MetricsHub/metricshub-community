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
	 *
	 */
	private static final Set<String> SNMP_METHODS = Set.of("get", "getnext", "walk", "table");
	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteSnmpGetService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteSnmpQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

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
			description = "The SNMP action to execute: Get, GetNext, Walk or Table.",
			required = true
		) final String action,
		@ToolParam(description = "The SNMP OID to use in the request.", required = true) final String oId,
		@ToolParam(description = "The columns to select on the SNMP Table query.") final String columns
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("snmp")
			.map((IProtocolExtension extension) -> executeQueryWithExtension(extension, hostname, action, oId, columns))
			.orElse(QueryResponse.builder().isError("SNMP Extension is not available").build());
	}

	/**
	 * Builds the SNMP query payload and executes it with the provided extension.
	 *
	 * @param extension the SNMP protocol extension
	 * @param hostname  the target host
	 * @param action    the SNMP action (get, getNext, walk, table)
	 * @param oid       the target OID
	 * @param columns   optional CSV of integer column indexes for table queries
	 * @return the {@link QueryResponse} with the execution result or an error
	 */
	private QueryResponse executeQueryWithExtension(
		final IProtocolExtension extension,
		final String hostname,
		final String action,
		final String oid,
		final String columns
	) {
		// Make sure the selected SNMP method exists.
		if (!SNMP_METHODS.contains(action.toLowerCase())) {
			return QueryResponse
				.builder()
				.isError("Unknown SNMP query. Only Get, GetNext, Walk and Table are allowed.")
				.build();
		}

		// Create an ObjectNode containing all the SNMP query details.
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("action", new TextNode(action));
		queryNode.set("oid", new TextNode(oid));

		// add the columns if an SNMP table query is invoked.
		if (action.equalsIgnoreCase("table") && columns != null) {
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
					final String result = extension.executeQuery(configuration, queryNode);
					return QueryResponse.builder().response(result).build();
				} catch (Exception e) {
					return QueryResponse
						.builder()
						.isError("failed to execute SNMP %s Query on %s.".formatted(action, hostname))
						.build();
				}
			})
			.orElseGet(() ->
				QueryResponse.builder().isError("No SNMP configuration found for %s.".formatted(hostname)).build()
			);
	}
}
