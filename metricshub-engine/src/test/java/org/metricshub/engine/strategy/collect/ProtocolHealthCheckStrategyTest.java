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
import org.metricshub.engine.telemetry.RequestOutcome;
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

	/**
	 * Runs one health check cycle where the protocol check sends one completed and one failed request, the failed
	 * one being accepted by the health check policy (the host is reported up).
	 */
	private void runCycleRecordingRequests(final TelemetryManager telemetryManager, final long strategyTime) {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn("wbem").when(protocolExtensionMock).getIdentifier(any(TelemetryManager.class));
		doAnswer(invocation -> {
			final TelemetryManager manager = invocation.getArgument(0);
			manager.getRequestStats().record("wbem", RequestOutcome.COMPLETED);
			manager.getRequestStats().record("wbem", RequestOutcome.FAILED);
			return Optional.of(true);
		}).when(protocolExtensionMock).checkProtocol(any(TelemetryManager.class));
		telemetryManager.setStrategyTime(strategyTime);
		new ProtocolHealthCheckStrategy(telemetryManager, strategyTime, clientsExecutorMock, extensionManager).run();
	}

	private static NumberMetric requestMetric(final TelemetryManager telemetryManager, final String state) {
		return telemetryManager.getEndpointHostMonitor()
			.getMetric(
				"metricshub.host.requests{state=\"%s\", operation_type=\"wbem\"}".formatted(state),
				NumberMetric.class
			);
	}

	@Test
	void testRequestMetricsAreCumulativeAcrossCycles() {
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();

		runCycleRecordingRequests(telemetryManager, CURRENT_TIME_MILLIS);
		assertEquals(1.0, requestMetric(telemetryManager, "completed").getValue());
		assertEquals(1.0, requestMetric(telemetryManager, "failed").getValue());
		assertEquals(0.0, requestMetric(telemetryManager, "timeout").getValue());
		assertEquals(
			Map.of("state", "failed", "operation_type", "wbem"),
			requestMetric(telemetryManager, "failed").getAttributes()
		);
		assertEquals("Counter", requestMetric(telemetryManager, "completed").getMetricType());
		// The health check policy is unchanged: a failed request does not make the host down
		assertEquals(
			ProtocolHealthCheckStrategy.UP,
			telemetryManager.getEndpointHostMonitor().getMetric(HOST_OBSERVED_METRIC_NAME, NumberMetric.class).getValue()
		);

		runCycleRecordingRequests(telemetryManager, CURRENT_TIME_MILLIS + 60_000);
		assertEquals(2.0, requestMetric(telemetryManager, "completed").getValue());
		assertEquals(2.0, requestMetric(telemetryManager, "failed").getValue());
		assertEquals(0.0, requestMetric(telemetryManager, "timeout").getValue());
		assertEquals(CURRENT_TIME_MILLIS + 60_000, requestMetric(telemetryManager, "completed").getCollectTime());
	}

	@Test
	void testRequestMetricsAreNotCollectedWithoutSelfMonitoring() {
		final TelemetryManager telemetryManager = createTelemetryManagerWithTestConfig();
		telemetryManager.getHostConfiguration().setEnableSelfMonitoring(false);

		runCycleRecordingRequests(telemetryManager, CURRENT_TIME_MILLIS);

		// The requests are counted, but not collected
		assertEquals(1L, telemetryManager.getRequestStats().snapshot().get("wbem").get(RequestOutcome.FAILED));
		assertTrue(
			telemetryManager.getEndpointHostMonitor()
				.getMetrics()
				.keySet()
				.stream()
				.noneMatch(name -> name.startsWith("metricshub.host.requests"))
		);
	}
}
