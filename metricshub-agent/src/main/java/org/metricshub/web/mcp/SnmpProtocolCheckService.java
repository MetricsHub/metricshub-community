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

import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP Tool Service for checking SNMP protocol reachability of a given host.
 */
@Service
public class SnmpProtocolCheckService {

	/**
	 * The type of the SNMP extension.
	 */
	private static final String SNMP_EXTENSION_TYPE = "snmp";

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private final AgentContextHolder agentContextHolder;

	/**
	 * A service for executing protocol check.
	 */
	private final ProtocolCheckService protocolCheckService;

	@Autowired
	public SnmpProtocolCheckService(
		final AgentContextHolder agentContextHolder,
		final ProtocolCheckService protocolCheckService
	) {
		this.agentContextHolder = agentContextHolder;
		this.protocolCheckService = protocolCheckService;
	}

	/**
	 * Checks whether the specified host is reachable using the SNMP protocol.
	 *
	 * @param hostname the target host to check
	 * @param timeout optional timeout for the SNMP check in seconds
	 * @return a {@link ProtocolCheckResponse} indicating whether the host is reachable and how long the check took
	 */
	@Tool(name = "CheckSnmpProtocol", description = "Checks if a host is reachable using the SNMP protocol.")
	public ProtocolCheckResponse checkSnmpProtocol(
		@ToolParam(description = "The hostname to check") final String hostname,
		@ToolParam(description = "Timeout for the SNMP check in seconds", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(SNMP_EXTENSION_TYPE)
			.map((IProtocolExtension extension) ->
				protocolCheckService.checkProtocolWithExtensionSafe(hostname, extension.getIdentifier(), timeout, extension)
			)
			.orElse(
				ProtocolCheckResponse.builder().hostname(hostname).errorMessage("SNMP extension is not available").build()
			);
	}
}
