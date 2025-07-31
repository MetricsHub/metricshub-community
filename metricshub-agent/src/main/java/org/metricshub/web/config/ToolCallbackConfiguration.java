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

import org.metricshub.web.mcp.HttpProtocolCheckService;
import org.metricshub.web.mcp.IpmiProtocolCheckService;
import org.metricshub.web.mcp.JdbcProtocolCheckService;
import org.metricshub.web.mcp.JmxProtocolCheckService;
import org.metricshub.web.mcp.ListResourcesService;
import org.metricshub.web.mcp.PingToolService;
import org.metricshub.web.mcp.SnmpProtocolCheckService;
import org.metricshub.web.mcp.SnmpV3ProtocolCheckService;
import org.metricshub.web.mcp.SshProtocolCheckService;
import org.metricshub.web.mcp.TriggerResourceCollectService;
import org.metricshub.web.mcp.TriggerResourceDetectionService;
import org.metricshub.web.mcp.WbemProtocolCheckService;
import org.metricshub.web.mcp.WinrmProtocolCheckService;
import org.metricshub.web.mcp.WmiProtocolCheckService;
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
	 * @param pingToolService                 the service handling ICMP ping checks
	 * @param httpProtocolCheckService        the service for checking HTTP protocol availability
	 * @param listResourcesService            the service that returns all the configured hosts
	 * @param ipmiProtocolCheckService        the service for checking IPMI protocol availability
	 * @param jdbcProtocolCheckService        the service for checking JDBC protocol availability
	 * @param jmxProtocolCheckService         the service for checking JMX protocol availability
	 * @param snmpProtocolCheckService        the service for checking SNMP protocol availability
	 * @param snmpV3ProtocolCheckService      the service for checking SNMPv3 protocol availability
	 * @param sshProtocolCheckService         the service for checking SSH protocol availability
	 * @param wbemProtocolCheckService        the service for checking WBEM protocol availability
	 * @param winrmProtocolCheckService       the service for checking WinRM protocol availability
	 * @param wmiProtocolCheckService         the service for checking WMI protocol availability
	 * @param triggerResourceDetectionService the service to trigger resource detection
	 * @param triggerResourceCollectService   the service to trigger resource collection
	 * @return a {@link ToolCallbackProvider} exposing all registered protocol services as tools
	 */
	@Bean
	public ToolCallbackProvider metricshubTools(
		final PingToolService pingToolService,
		final HttpProtocolCheckService httpProtocolCheckService,
		final ListResourcesService listResourcesService,
		final IpmiProtocolCheckService ipmiProtocolCheckService,
		final JdbcProtocolCheckService jdbcProtocolCheckService,
		final JmxProtocolCheckService jmxProtocolCheckService,
		final SnmpProtocolCheckService snmpProtocolCheckService,
		final SnmpV3ProtocolCheckService snmpV3ProtocolCheckService,
		final SshProtocolCheckService sshProtocolCheckService,
		final WbemProtocolCheckService wbemProtocolCheckService,
		final WinrmProtocolCheckService winrmProtocolCheckService,
		final WmiProtocolCheckService wmiProtocolCheckService,
		final TriggerResourceDetectionService triggerResourceDetectionService,
		final TriggerResourceCollectService triggerResourceCollectService
	) {
		return MethodToolCallbackProvider
			.builder()
			.toolObjects(
				pingToolService,
				httpProtocolCheckService,
				listResourcesService,
				ipmiProtocolCheckService,
				jdbcProtocolCheckService,
				jmxProtocolCheckService,
				snmpProtocolCheckService,
				snmpV3ProtocolCheckService,
				sshProtocolCheckService,
				wbemProtocolCheckService,
				winrmProtocolCheckService,
				wmiProtocolCheckService,
				triggerResourceDetectionService,
				triggerResourceCollectService
			)
			.build();
	}
}
