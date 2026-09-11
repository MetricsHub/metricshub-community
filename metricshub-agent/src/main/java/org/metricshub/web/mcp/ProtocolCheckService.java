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

import java.util.List;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.ProtocolHealthCheckService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP tool service responsible for checking the reachability and availability of a host
 * using a specified protocol (e.g., SSH, WinRM, WMI, etc.).
 */
@Service
public class ProtocolCheckService implements IMCPToolService {

	/**
	 * Default pool size for protocol checks.
	 */
	private static final int DEFAULT_PROTOCOL_CHECK_POOL_SIZE = 60;

	private final AgentContextHolder agentContextHolder;

	private final ProtocolHealthCheckService protocolHealthCheckService;

	/**
	 * Constructor for ProtocolCheckService.
	 *
	 * @param agentContextHolder          the {@link AgentContextHolder} instance to access the agent context
	 * @param protocolHealthCheckService  shared protocol health-check executor
	 */
	@Autowired
	public ProtocolCheckService(
		final AgentContextHolder agentContextHolder,
		final ProtocolHealthCheckService protocolHealthCheckService
	) {
		this.agentContextHolder = agentContextHolder;
		this.protocolHealthCheckService = protocolHealthCheckService;
	}

	/**
	 * Checks whether the specified host is reachable using the specified protocol.
	 *
	 * @param hostname the target host to check
	 * @param protocol the name of the protocol to check (e.g., http, ipmi, jdbc, jmx, oscommand, snmp, snmpv3, ssh, wbem, winrm, wmi)
	 * @param timeout optional timeout for the protocol check in seconds
	 * @param poolSize optional pool size for concurrent protocol checks; defaults to {@value #DEFAULT_PROTOCOL_CHECK_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a {@link ProtocolCheckResponse} indicating whether the host is reachable and how long the check took
	 */
	@Tool(
		name = "CheckProtocol",
		description = """
		Determines if the specified hosts are accessible using a given protocol.
		Supported protocols include: http, ipmi, jdbc, jmx, oscommand, snmp, snmpv3, ssh, wbem, winrm, and wmi.
		OS Command (oscommand) checks command execution on the machine running MetricsHub.
		The target may be localhost, a loopback address, or a hostname/IP address resolving to that machine.
		SSH checks targeting that machine also use local command execution, following the collection optimization.
		Provides a response detailing the host reachability status along with the response time.
		"""
	)
	public MultiHostToolResponse<ProtocolCheckResponse> checkProtocol(
		@ToolParam(description = "The hostname(s) to check") final List<String> hostname,
		@ToolParam(
			description = """
			The name of the protocol to check. Supported protocols include: http, ipmi, jdbc, jmx, oscommand, snmp, snmpv3, ssh, wbem, winrm, and wmi.
			oscommand applies to the machine running MetricsHub, including localhost, loopback addresses, and hostnames/IPs resolving to that machine.
			""",
			required = true
		) final String protocol,
		@ToolParam(description = "Timeout for the protocol check in seconds", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent protocol checks. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		final String protocolIdentifier = protocol;
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_PROTOCOL_CHECK_POOL_SIZE);
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(protocol)
			.map((IProtocolExtension extension) ->
				executeForHosts(
					hostname,
					this::buildNullHostnameResponse,
					host ->
						HostToolResponse.<ProtocolCheckResponse>builder()
							.hostname(host)
							.response(protocolHealthCheckService.checkFromAgentContext(host, protocol, timeout, extension))
							.build(),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError(protocolIdentifier + " extension is not available"));
	}

	/**
	 * Builds a {@link HostToolResponse} indicating that the hostname parameter was
	 * not provided.
	 *
	 * @return a host-level response with an error payload highlighting the missing hostname
	 */
	private HostToolResponse<ProtocolCheckResponse> buildNullHostnameResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() ->
			ProtocolCheckResponse.builder().errorMessage(NULL_HOSTNAME_ERROR).build()
		);
	}
}
