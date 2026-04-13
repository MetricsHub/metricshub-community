package org.metricshub.engine.strategy.collect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.constants.Constants.HOSTNAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtocolHealthCheckStrategyTest {

	private static final long CURRENT_TIME_MILLIS = System.currentTimeMillis();

	@Mock
	private static ClientsExecutor clientsExecutorMock;

	@Mock
	private static IProtocolExtension protocolExtensionMock;

	static Map<String, Map<String, Monitor>> monitors;

	/**
	 * Sets up the test environment before each test method is executed.
	 * Creates a endpoint host monitor with specific properties and initializes the monitors map.
	 */
	@BeforeEach
	void setup() {
		Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		monitors = new HashMap<>(Map.of(HOST.getKey(), Map.of(HOSTNAME, hostMonitor)));
	}

	/**
	 * Creates and returns a TelemetryManager instance with a Test configuration.
	 *
	 * @return A TelemetryManager instance configured with a Test configuration.
	 */
	private TelemetryManager createTelemetryManagerWithTestConfig() {
		// Create a telemetry manager
		return TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOSTNAME)
					.hostname(HOSTNAME)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.strategyTime(System.currentTimeMillis())
			.build();
	}

	@Test
	void testCheckHealth() {
		// Create a telemetry manager using a test configuration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();

		// Create the Extension Manager
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		final String protocol = "snmp";

		doReturn(protocol).when(protocolExtensionMock).getIdentifier();

		doReturn(Optional.of(true)).when(protocolExtensionMock).checkProtocol(any(TelemetryManager.class));

		// Create a new protocol health check strategy
		final ProtocolHealthCheckStrategy healthCheckStrategy = new ProtocolHealthCheckStrategy(
			telemetryManager,
			CURRENT_TIME_MILLIS,
			clientsExecutorMock,
			extensionManager
		);
		assertDoesNotThrow(() -> healthCheckStrategy.run());

		assertEquals(
			ProtocolHealthCheckStrategy.UP,
			telemetryManager
				.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.UP_METRIC_FORMAT.formatted(protocol), NumberMetric.class)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.RESPONSE_TIME_METRIC_FORMAT.formatted(protocol), NumberMetric.class)
		);
	}
}
