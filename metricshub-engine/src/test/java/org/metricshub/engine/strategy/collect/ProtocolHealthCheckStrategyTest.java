package org.metricshub.engine.strategy.collect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.constants.Constants.HOSTNAME;
import static org.metricshub.engine.strategy.AbstractStrategy.HOST_OBSERVED_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

	@Mock
	private static IProtocolExtension additionalProtocolExtensionMock;

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
		return TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
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
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		final String protocol = "snmp";

		doReturn(protocol).when(protocolExtensionMock).getIdentifier(any(TelemetryManager.class));

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
			telemetryManager.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.UP_METRIC_FORMAT.formatted(protocol), NumberMetric.class)
				.getValue()
		);
		assertNotNull(
			telemetryManager.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.RESPONSE_TIME_METRIC_FORMAT.formatted(protocol), NumberMetric.class)
		);
		assertEquals(
			ProtocolHealthCheckStrategy.UP,
			telemetryManager.getEndpointHostMonitor().getMetric(HOST_OBSERVED_METRIC_NAME, NumberMetric.class).getValue()
		);
	}

	@Test
	void testInterruptedCheckReportsNothing() {
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock, additionalProtocolExtensionMock))
			.build();
		final TestConfiguration configuration = (TestConfiguration) telemetryManager.getHostConfiguration()
			.getConfigurations()
			.get(TestConfiguration.class);
		doReturn(true).when(protocolExtensionMock).isValidConfiguration(configuration);
		doReturn(true).when(additionalProtocolExtensionMock).isValidConfiguration(configuration);

		// The job deadline expires during the first check, which then reports the host as not up
		doAnswer(invocation -> {
			Thread.currentThread().interrupt();
			return Optional.of(false);
		}).when(protocolExtensionMock).checkProtocol(any(TelemetryManager.class));

		try {
			new ProtocolHealthCheckStrategy(telemetryManager, CURRENT_TIME_MILLIS, clientsExecutorMock, extensionManager)
				.run();

			// No more check, and a check cut short reports neither the protocol nor the host down
			verify(additionalProtocolExtensionMock, never()).checkProtocol(any(TelemetryManager.class));
			assertTrue(telemetryManager.getEndpointHostMonitor().getMetrics().isEmpty());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testCheckHealthCollectsObservedDownWhenAllProtocolsAreDown() {
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		final String protocol = "ssh";
		doReturn(protocol).when(protocolExtensionMock).getIdentifier(any(TelemetryManager.class));
		doReturn(Optional.of(false)).when(protocolExtensionMock).checkProtocol(any(TelemetryManager.class));

		final ProtocolHealthCheckStrategy healthCheckStrategy = new ProtocolHealthCheckStrategy(
			telemetryManager,
			CURRENT_TIME_MILLIS,
			clientsExecutorMock,
			extensionManager
		);

		assertDoesNotThrow(healthCheckStrategy::run);
		assertEquals(
			ProtocolHealthCheckStrategy.DOWN,
			telemetryManager.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.UP_METRIC_FORMAT.formatted(protocol), NumberMetric.class)
				.getValue()
		);
		assertEquals(
			ProtocolHealthCheckStrategy.DOWN,
			telemetryManager.getEndpointHostMonitor().getMetric(HOST_OBSERVED_METRIC_NAME, NumberMetric.class).getValue()
		);
	}

	@Test
	void testCheckHealthCollectsObservedUpWhenAtLeastOneProtocolIsUp() {
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock, additionalProtocolExtensionMock))
			.build();

		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(true).when(additionalProtocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn("snmp").when(protocolExtensionMock).getIdentifier(any(TelemetryManager.class));
		doReturn("ssh").when(additionalProtocolExtensionMock).getIdentifier(any(TelemetryManager.class));
		doReturn(Optional.of(false)).when(protocolExtensionMock).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(true)).when(additionalProtocolExtensionMock).checkProtocol(any(TelemetryManager.class));

		final ProtocolHealthCheckStrategy healthCheckStrategy = new ProtocolHealthCheckStrategy(
			telemetryManager,
			CURRENT_TIME_MILLIS,
			clientsExecutorMock,
			extensionManager
		);

		assertDoesNotThrow(healthCheckStrategy::run);
		assertEquals(
			ProtocolHealthCheckStrategy.DOWN,
			telemetryManager.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.UP_METRIC_FORMAT.formatted("snmp"), NumberMetric.class)
				.getValue()
		);
		assertEquals(
			ProtocolHealthCheckStrategy.UP,
			telemetryManager.getEndpointHostMonitor()
				.getMetric(ProtocolHealthCheckStrategy.UP_METRIC_FORMAT.formatted("ssh"), NumberMetric.class)
				.getValue()
		);
		assertEquals(
			ProtocolHealthCheckStrategy.UP,
			telemetryManager.getEndpointHostMonitor().getMetric(HOST_OBSERVED_METRIC_NAME, NumberMetric.class).getValue()
		);
	}
}
