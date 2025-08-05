package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.telemetry.MonitorsVo;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.hardware.strategy.HardwareMonitorNameGenerationStrategy;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.TelemetryResult;
import org.mockito.MockedStatic;

class TroubleshootHostServiceTest {

	private static final String HOSTNAME = "test.domain.net";

	private TroubleshootHostService service;
	private AgentContextHolder agentContextHolder;
	private TelemetryManager telemetryManager;

	@BeforeEach
	void setup() {
		agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(new SnmpExtension()))
			.build();

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		final ConnectorStore connectorStore = mock(ConnectorStore.class);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostId(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(SnmpConfiguration.class, SnmpConfiguration.builder().build()))
			.build();

		telemetryManager =
			TelemetryManager.builder().connectorStore(connectorStore).hostConfiguration(hostConfiguration).build();

		service = new TroubleshootHostService(agentContextHolder);
	}

	@Test
	void testCollectMetricsForHostNoTelemetryManager() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.empty());
			final var result = service.collectMetricsForHost(HOSTNAME, null);

			assertEquals(
				new TelemetryResult(TroubleshootHostService.HOSTNAME_NOT_CONFIGURED_MSG.formatted(HOSTNAME)),
				result,
				"Unexpected result when no telemetry manager is found for the hostname."
			);
		}
	}

	@Test
	void testCollectMetricsForHost() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.of(telemetryManager));
			final TelemetryManager telemetryManagerMock = mock(TelemetryManager.class);
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.newFrom(telemetryManager, null))
				.thenReturn(telemetryManagerMock);

			doNothing()
				.when(telemetryManagerMock)
				.run(
					any(DetectionStrategy.class),
					any(DiscoveryStrategy.class),
					any(SimpleStrategy.class),
					any(HardwarePostDiscoveryStrategy.class),
					any(HardwareMonitorNameGenerationStrategy.class)
				);

			doNothing()
				.when(telemetryManagerMock)
				.run(
					any(PrepareCollectStrategy.class),
					any(ProtocolHealthCheckStrategy.class),
					any(CollectStrategy.class),
					any(SimpleStrategy.class),
					any(HardwarePostCollectStrategy.class),
					any(HardwareMonitorNameGenerationStrategy.class)
				);

			final var expected = new MonitorsVo();
			when(telemetryManagerMock.getVo()).thenReturn(expected);

			assertEquals(
				new TelemetryResult(expected),
				service.collectMetricsForHost(HOSTNAME, null),
				"Unexpected result when triggering resource detection."
			);

			verify(telemetryManagerMock, times(1))
				.run(
					any(DetectionStrategy.class),
					any(DiscoveryStrategy.class),
					any(SimpleStrategy.class),
					any(HardwarePostDiscoveryStrategy.class),
					any(HardwareMonitorNameGenerationStrategy.class)
				);

			verify(telemetryManagerMock, times(1))
				.run(
					any(PrepareCollectStrategy.class),
					any(ProtocolHealthCheckStrategy.class),
					any(CollectStrategy.class),
					any(SimpleStrategy.class),
					any(HardwarePostCollectStrategy.class),
					any(HardwareMonitorNameGenerationStrategy.class)
				);
		}
	}

	@Test
	void testTestAvailableConnectorsForHostNoTelemetryManager() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.empty());
			final var result = service.testAvailableConnectorsForHost(HOSTNAME, null);

			assertEquals(
				new TelemetryResult(TroubleshootHostService.HOSTNAME_NOT_CONFIGURED_MSG.formatted(HOSTNAME)),
				result,
				"Unexpected result when no telemetry manager is found for the hostname."
			);
		}
	}

	@Test
	void testGetMetricsFromCacheForHostNoTelemetryManager() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.empty());
			final var result = service.getMetricsFromCacheForHost(HOSTNAME);

			assertEquals(
				new TelemetryResult(TroubleshootHostService.HOSTNAME_NOT_CONFIGURED_MSG.formatted(HOSTNAME)),
				result,
				"Unexpected result when no telemetry manager is found for the hostname."
			);
		}
	}

	@Test
	void testGetMetricsFromCacheForHost() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.of(telemetryManager));
			final var result = service.getMetricsFromCacheForHost(HOSTNAME);

			assertNotNull(result, "Result should not be null when telemetry manager is found.");
		}
	}

	@Test
	void testTestAvailableConnectorsForHost() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.of(telemetryManager));
			final TelemetryManager telemetryManagerMock = mock(TelemetryManager.class);
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.newFrom(telemetryManager, null))
				.thenReturn(telemetryManagerMock);

			doNothing().when(telemetryManagerMock).run(any(DetectionStrategy.class));

			final var expected = new MonitorsVo();
			when(telemetryManagerMock.getVo()).thenReturn(expected);

			assertEquals(
				new TelemetryResult(expected),
				service.testAvailableConnectorsForHost(HOSTNAME, null),
				"Unexpected result when triggering resource detection."
			);
			verify(telemetryManagerMock, times(1)).run(any(DetectionStrategy.class));
		}
	}
}
