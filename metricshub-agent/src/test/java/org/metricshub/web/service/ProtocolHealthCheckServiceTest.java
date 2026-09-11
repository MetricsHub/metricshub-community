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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.oscommand.OsCommandConfiguration;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.mcp.ProtocolCheckResponse;
import org.mockito.MockedConstruction;

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
			Map.of("http", HttpConfiguration.builder().build())
		);

		assertEquals("Hostname must be provided.", response.getErrorMessage());
	}

	@Test
	void testCheckWithInlineConfigurationPrefersProtocolHostname() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			"resource-host",
			"http",
			Map.of("http", HttpConfiguration.builder().hostname(HOSTNAME).username("user").build())
		);

		assertTrue(response.isReachable());
		assertEquals(HOSTNAME, response.getHostname());
	}

	@Test
	void testCheckWithInlineConfigurationFallsBackToProtocolHostnameWhenResourceHostnameIsBlank() {
		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			"  ",
			"http",
			Map.of("http", HttpConfiguration.builder().hostname(HOSTNAME).username("user").build())
		);

		assertTrue(response.isReachable());
		assertEquals(HOSTNAME, response.getHostname());
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

	@ParameterizedTest
	@ValueSource(strings = { "ssh", "oscommand" })
	void testCheckFromAgentContextRejectsSiblingProtocolConfiguration(final String protocol) throws Exception {
		final IConfiguration configuration = "ssh".equals(protocol)
			? OsCommandConfiguration.builder().build()
			: SshConfiguration.sshConfigurationBuilder().build();
		final OsCommandExtension extension = configureLocalCommandChecks(Map.of(configuration.getClass(), configuration));

		final ProtocolCheckResponse response = service.checkFromAgentContext("localhost", protocol, 5L, extension);

		assertFalse(response.isReachable());
		verify(extension, never()).checkProtocol(any());
		verify(extension, never()).buildConfiguration(anyString(), any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ssh", "oscommand" })
	void testCheckWithInlineConfigurationRejectsSiblingProtocolConfiguration(final String protocol) {
		final String siblingProtocol = "ssh".equals(protocol) ? "oscommand" : "ssh";
		final IConfiguration configuration = "ssh".equals(protocol)
			? OsCommandConfiguration.builder().build()
			: SshConfiguration.sshConfigurationBuilder().build();
		final OsCommandExtension extension = configureLocalCommandChecks(Map.of());

		final ProtocolCheckResponse response = service.checkWithInlineConfiguration(
			"localhost",
			protocol,
			Map.of(siblingProtocol, configuration)
		);

		assertEquals("Invalid protocol configuration", response.getErrorMessage());
		assertFalse(response.isReachable());
		verify(extension, never()).checkProtocol(any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "SSH", "oscommand" })
	void testLocalChecksUseTheRequestedConfigurationAndKeepLocalExecution(final String protocol) throws Exception {
		try (
			MockedConstruction<OsCommandService> construction = mockConstruction(
				OsCommandService.class,
				(commandService, context) ->
					when(commandService.runLocalCommand(anyString(), anyLong(), any())).thenReturn("test")
			)
		) {
			final OsCommandExtension extension = configureLocalCommandChecks(
				Map.of(
					SshConfiguration.class,
					SshConfiguration.sshConfigurationBuilder().build(),
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().timeout(5L).build()
				)
			);

			assertTrue(service.checkFromAgentContext("localhost", protocol, 5L, extension).isReachable());
			verify(extension).checkProtocol(
				argThat(telemetryManager ->
					telemetryManager
						.getHostConfiguration()
						.getConfigurations()
						.values()
						.stream()
						.allMatch(configuration -> configuration.isCorrespondingProtocol(protocol))
				)
			);

			final OsCommandService commandService = construction.constructed().getFirst();
			verify(commandService).runLocalCommand(eq("echo test"), anyLong(), any());
			when(commandService.runLocalCommand(anyString(), anyLong(), any())).thenReturn(null);
			assertFalse(service.checkFromAgentContext("localhost", protocol, 5L, extension).isReachable());
			verify(commandService, never()).runSshCommand(anyString(), anyString(), any(), anyLong(), any(), any(), any());
		}
	}

	@Test
	void testOsCommandCollectionReportsObservedWithOsCommandLabel() throws Exception {
		try (
			MockedConstruction<OsCommandService> construction = mockConstruction(
				OsCommandService.class,
				(commandService, context) ->
					when(commandService.runLocalCommand(anyString(), anyLong(), any())).thenReturn("test")
			)
		) {
			final Monitor monitor = Monitor.builder().type("host").isEndpoint(true).build();
			final TelemetryManager telemetryManager = TelemetryManager.builder()
				.hostConfiguration(
					HostConfiguration.builder()
						.hostname("localhost")
						.configurations(Map.of(OsCommandConfiguration.class, OsCommandConfiguration.builder().timeout(5L).build()))
						.build()
				)
				.hostProperties(HostProperties.builder().isLocalhost(true).mustCheckSshStatus(true).build())
				.monitors(new HashMap<>(Map.of("host", new HashMap<>(Map.of("localhost", monitor)))))
				.strategyTime(1L)
				.build();
			final ExtensionManager extensionManager = ExtensionManager.builder()
				.withProtocolExtensions(List.of(new OsCommandExtension()))
				.build();

			new ProtocolHealthCheckStrategy(telemetryManager, 1L, mock(ClientsExecutor.class), extensionManager).run();

			assertEquals(1.0, monitor.getMetric("metricshub.host.up{protocol=\"oscommand\"}", NumberMetric.class).getValue());
			assertEquals(1.0, monitor.getMetric("metricshub.host.observed", NumberMetric.class).getValue());
			assertNull(monitor.getMetric("metricshub.host.up{protocol=\"ssh\"}", NumberMetric.class));
			verify(construction.constructed().getFirst()).runLocalCommand("echo test", 5L, null);
		}
	}

	private OsCommandExtension configureLocalCommandChecks(
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations
	) {
		final OsCommandExtension extension = spy(new OsCommandExtension());
		when(agentContextHolder.getAgentContext().getExtensionManager()).thenReturn(
			ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build()
		);
		when(agentContextHolder.getAgentContext().getTelemetryManagers()).thenReturn(
			Map.of(
				"resourceGroup",
				Map.of(
					"localhost",
					TelemetryManager.builder()
						.hostConfiguration(HostConfiguration.builder().hostname("localhost").configurations(configurations).build())
						.build()
				)
			)
		);
		return extension;
	}
}
