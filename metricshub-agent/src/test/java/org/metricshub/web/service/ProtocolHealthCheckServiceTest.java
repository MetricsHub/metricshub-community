package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.mcp.ProtocolCheckResponse;

class ProtocolHealthCheckServiceTest {

	private static final String HOSTNAME = "example-host";

	private ProtocolHealthCheckService service;
	private HttpExtension httpExtension;
	private AgentContextHolder agentContextHolder;

	@BeforeEach
	void setUp() {
		httpExtension = mock(HttpExtension.class);
		doReturn("http").when(httpExtension).getIdentifier();
		doReturn(true).when(httpExtension).isSupportedConfigurationType(eq("http"));
		doReturn(false).when(httpExtension).isSupportedConfigurationType(eq("wmi"));
		doReturn(true).when(httpExtension).isValidConfiguration(any(HttpConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any());

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(httpExtension))
			.build();

		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		service = new ProtocolHealthCheckService(agentContextHolder);
	}

	@Test
	void testCheckWithInlineConfigurationRequiresHostname() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			"  ",
			"http",
			Map.of("http", HttpConfiguration.builder().hostname(HOSTNAME).build())
		);

		assertEquals("Hostname must be provided.", response.getErrorMessage());
	}

	@Test
	void testCheckWithInlineConfigurationRejectsInvalidConfiguration() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(HOSTNAME, "http", Map.of());

		assertEquals(HOSTNAME, response.getHostname());
		assertEquals("Invalid protocol configuration", response.getErrorMessage());
	}

	@Test
	void testCheckWithInlineConfigurationRejectsUnavailableExtension() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			HOSTNAME,
			"wmi",
			Map.of("wmi", SshConfiguration.sshConfigurationBuilder().username("admin").build())
		);

		assertEquals(HOSTNAME, response.getHostname());
		assertEquals("wmi extension is not available", response.getErrorMessage());
	}

	@Test
	void testCheckWithInlineConfigurationRejectsInvalidTypedConfiguration() {
		doReturn(false).when(httpExtension).isValidConfiguration(any(HttpConfiguration.class));

		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			HOSTNAME,
			"http",
			Map.of("http", HttpConfiguration.builder().hostname(HOSTNAME).build())
		);

		assertEquals(HOSTNAME, response.getHostname());
		assertEquals("Invalid protocol configuration", response.getErrorMessage());
	}

	@Test
	void testCheckWithInlineConfigurationReturnsReachableResult() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			HOSTNAME,
			"http",
			Map.of(
				"http",
				HttpConfiguration.builder().hostname(HOSTNAME).username("user").password("pass".toCharArray()).build()
			)
		);

		assertNotNull(response);
		assertTrue(response.isReachable());
		assertEquals(HOSTNAME, response.getHostname());
		assertTrue(response.getResponseTime() >= 0);
	}

	@Test
	void testCheckWithInlineConfigurationReturnsUnreachableWhenExtensionReportsFalse() {
		doReturn(Optional.of(Boolean.FALSE)).when(httpExtension).checkProtocol(any());

		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			HOSTNAME,
			"http",
			Map.of(
				"http",
				HttpConfiguration.builder().hostname(HOSTNAME).username("user").password("pass".toCharArray()).build()
			)
		);

		assertFalse(response.isReachable());
		assertEquals(HOSTNAME, response.getHostname());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testCheckFromAgentContextReturnsUnreachableWhenNoConfigurations() {
		final ProtocolCheckResponse response = service.checkFromAgentContext(HOSTNAME, "http", 5L, httpExtension);

		assertEquals(HOSTNAME, response.getHostname());
		assertFalse(response.isReachable());
	}
}
