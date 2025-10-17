package org.metricshub.web.config;

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

import org.metricshub.web.mcp.ExecuteIpmiQueryService;
import org.metricshub.web.mcp.ExecuteJmxQueryService;
import org.metricshub.web.mcp.ExecuteSnmpQueryService;
import org.metricshub.web.mcp.ExecuteWqlQueryService;
import org.metricshub.web.mcp.HostDetailsService;
import org.metricshub.web.mcp.ListConnectorsService;
import org.metricshub.web.mcp.ListResourcesService;
import org.metricshub.web.mcp.PingToolService;
import org.metricshub.web.mcp.ProtocolCheckService;
import org.metricshub.web.mcp.TroubleshootHostService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class providing {@link ToolCallback} instances for tools defined in different sources.
 */
@Configuration
public class ToolCallbackConfiguration {

	/**
	 * Registers a {@link ToolCallbackProvider} that exposes all protocol-specific services
	 * and the {@link PingToolService} for use with AI tools or external integrations.
	 *
	 * @param pingToolService            the service handling ICMP ping checks
	 * @param protocolCheckService       the service for checking protocol availability
	 * @param listResourcesService       the service that returns all the configured hosts
	 * @param troubleshootHostService    the service to trigger resource detection
	 * @param listConnectorsService      the service that lists all connectors supported by MetricsHub
	 * @param hostDetailsService         the service that returns all the host details (protocols, connectors, ...)
	 * @param executeSnmpQueryService    the service for executing SNMP queries (get, getNext, walk or table).
	 * @param executeWqlQueryService     the service for executing WQL queries through WMI or WinRm.
	 * @param executeIpmiQueryService    the service for executing IPMI queries.
	 * @param executeJmxQueryService     the service for executing JMX queries.
	 * @return a {@link ToolCallbackProvider} exposing all registered protocol services as tools
	 */
	@Bean
	public ToolCallbackProvider metricshubTools(
		final PingToolService pingToolService,
		final ProtocolCheckService protocolCheckService,
		final ListResourcesService listResourcesService,
		final TroubleshootHostService troubleshootHostService,
		final ListConnectorsService listConnectorsService,
		final HostDetailsService hostDetailsService,
		final ExecuteSnmpQueryService executeSnmpQueryService,
		final ExecuteWqlQueryService executeWqlQueryService,
		final ExecuteIpmiQueryService executeIpmiQueryService,
		final ExecuteJmxQueryService executeJmxQueryService
	) {
		return MethodToolCallbackProvider
			.builder()
			.toolObjects(
				pingToolService,
				protocolCheckService,
				listResourcesService,
				troubleshootHostService,
				listConnectorsService,
				hostDetailsService,
				executeSnmpQueryService,
				executeWqlQueryService,
				executeIpmiQueryService,
				executeJmxQueryService
			)
			.build();
	}
}
