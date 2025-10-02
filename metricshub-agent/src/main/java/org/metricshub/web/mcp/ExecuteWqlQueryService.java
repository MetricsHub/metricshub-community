package org.metricshub.web.mcp;

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
 * 
 */
public class ExecuteWqlQueryService {

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

	@Tool(
			name = "ExecuteSnmpQuery",
			description = """
			Executes a WQL query via WMI or WinRm on a given hostname and a namespace.
			TODO.
			"""
		)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute SNMP Query on.", required = true) final String hostname,
		@ToolParam(
			description = "The SNMP action to execute: Get, GetNext, Walk or Table.",
			required = true
		) final String protocol,
		@ToolParam(description = "The SNMP OID to use in the request.", required = true) final String query,
		@ToolParam(description = "The columns to select on the SNMP Table query.") final String namespace
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(protocol)
			.map((IProtocolExtension extension) -> executeQueryWithExtension(extension, hostname, query, namespace))
			.orElse(QueryResponse.builder().isError("SNMP Extension is not available").build());
	}

	/**
	 * 
	 * @param extension the SNMP protocol extension
	 * @param hostname  the target host
	 * @param query 
	 * @param namespace
	 * @return
	 */
	private QueryResponse executeQueryWithExtension(
		final IProtocolExtension extension,
		final String hostname,
		final String query,
		final String namespace
	) {
		return MCPConfigHelper
			.resolveAllHostConfigurationsFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			
	}
}
