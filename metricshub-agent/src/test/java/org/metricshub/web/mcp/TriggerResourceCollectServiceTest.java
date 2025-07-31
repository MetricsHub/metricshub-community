package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.*;
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
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.hardware.strategy.HardwareMonitorNameGenerationStrategy;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.metricshub.web.AgentContextHolder;
import org.mockito.MockedStatic;

class TriggerResourceCollectServiceTest {

	private static final String HOSTNAME = "test.domain.net";

	private TriggerResourceCollectService service;
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

		service = new TriggerResourceCollectService(agentContextHolder);
	}

	@Test
	void testTriggerResourceMetricCollectionNoTelemetryManager() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.empty());
			String result = service.triggerResourceMetricCollection(HOSTNAME);

			assertEquals(
				"The hostname " +
				HOSTNAME +
				" is not currently monitored by MetricsHub. Please configure a new resource for this host to begin monitoring.",
				result,
				"Unexpected result when no telemetry manager is found for the hostname."
			);
		}
	}

	@Test
	void testTriggerResourceMetricCollection() {
		try (MockedStatic<MCPConfigHelper> mockedMCPConfigHelper = mockStatic(MCPConfigHelper.class)) {
			mockedMCPConfigHelper
				.when(() -> MCPConfigHelper.findTelemetryManagerByHostname(HOSTNAME, agentContextHolder))
				.thenReturn(Optional.of(telemetryManager));
			final TelemetryManager telemetryManagerMock = mock(TelemetryManager.class);
			mockedMCPConfigHelper.when(() -> MCPConfigHelper.newFrom(telemetryManager)).thenReturn(telemetryManagerMock);

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

			final String expected = "{\"status\":\"success\"}";
			when(telemetryManagerMock.toJson()).thenReturn(expected);

			assertEquals(
				expected,
				service.triggerResourceMetricCollection(HOSTNAME),
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
}
