package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.HOST;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
import static org.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.metricshub.engine.constants.Constants.TEST_CONNECTOR_ID;
import static org.metricshub.engine.strategy.AbstractStrategy.CONNECTOR_ID_FORMAT;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

class AbstractStrategyTest {

	@Test
	void testSetJobDurationMetricWithMonitorTypeNoConfiguration() {
		// The job duration metrics are not configured in metricshub.yaml

		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST,
				Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor),
				CONNECTOR,
				Map.of(
					String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy
			.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(new Date().getTime())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		collectStrategy.setJobDurationMetric(
			"collect",
			KnownMonitorType.CONNECTOR.getKey(),
			TEST_CONNECTOR_ID,
			System.currentTimeMillis() - 200,
			System.currentTimeMillis()
		);
		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"connector\", connector_id=\"TestConnector\"}"
				)
				.getValue()
		);
	}

	@Test
	void testSetJobDurationMetricWithMonitorTypeEnabledConfiguration() {
		// The job duration metrics are configured and enabled

		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST,
				Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor),
				CONNECTOR,
				Map.of(
					String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(true)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy
			.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(new Date().getTime())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		collectStrategy.setJobDurationMetric(
			"collect",
			KnownMonitorType.CONNECTOR.getKey(),
			TEST_CONNECTOR_ID,
			System.currentTimeMillis() - 200,
			System.currentTimeMillis()
		);
		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"connector\", connector_id=\"TestConnector\"}"
				)
				.getValue()
		);
	}

	@Test
	void testSetJobDurationMetricWithMonitorTypeDisabledConfiguration() {
		// The job duration metrics are configured and disabled

		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST,
				Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor),
				CONNECTOR,
				Map.of(
					String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy
			.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(new Date().getTime())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		collectStrategy.setJobDurationMetric(
			"collect",
			KnownMonitorType.CONNECTOR.getKey(),
			TEST_CONNECTOR_ID,
			System.currentTimeMillis() - 200,
			System.currentTimeMillis()
		);
		// Check job duration metrics values
		assertNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"connector\", connector_id=\"TestConnector\"}"
				)
		);
	}

	@Test
	void testSetJobDurationMetricWithSurroundingStrategy() {
		// The job duration metrics are configured and enabled

		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST,
				Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor),
				CONNECTOR,
				Map.of(
					String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(true)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final BeforeAllStrategy beforeAllStrategy = BeforeAllStrategy
			.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(new Date().getTime())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.connector(new Connector())
			.build();
		beforeAllStrategy.setJobDurationMetric(
			"beforeAll",
			TEST_CONNECTOR_ID,
			System.currentTimeMillis() - 200,
			System.currentTimeMillis()
		);
		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric("metricshub.job.duration{job.type=\"beforeAll\", connector_id=\"TestConnector\"}")
				.getValue()
		);
	}

	@Test
	void testHasExpectedJobTypesMatching() {
		// Set the monitor jobs in the connector
		final Connector connector = new Connector();
		final SimpleMonitorJob simpleJob = SimpleMonitorJob.simpleBuilder().simple(new Simple()).build();
		final Map<String, MonitorJob> monitors = new HashMap<>(Map.of("simple", simpleJob));
		connector.setMonitors(monitors);

		// Check whether there is a connector monitor job that matches the strategy job name
		final SimpleStrategy simpleStrategy = SimpleStrategy
			.builder()
			.strategyTime(120L)
			.telemetryManager(TelemetryManager.builder().build())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		assertTrue(simpleStrategy.hasExpectedJobTypes(connector, "simple"));
	}

	@Test
	void testHasExpectedJobTypesNotMatching() {
		// Set the monitor jobs in the connector
		final Connector connector = new Connector();
		final SimpleMonitorJob simpleJob = SimpleMonitorJob.simpleBuilder().simple(new Simple()).build();
		final Map<String, MonitorJob> monitors = new HashMap<>(Map.of("simple", simpleJob));
		connector.setMonitors(monitors);

		// Check whether there is a connector monitor job that matches the strategy job name
		final SimpleStrategy simpleStrategy = SimpleStrategy
			.builder()
			.strategyTime(120L)
			.telemetryManager(TelemetryManager.builder().build())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		assertFalse(simpleStrategy.hasExpectedJobTypes(connector, "collect"));
	}

	@Test
	void testHasExpectedJobTypesUnknownJobType() {
		// Set the monitor jobs in the connector
		final Connector connector = new Connector();
		final SimpleMonitorJob simpleJob = SimpleMonitorJob.simpleBuilder().simple(new Simple()).build();
		final Map<String, MonitorJob> monitors = new HashMap<>(Map.of("simple", simpleJob));
		connector.setMonitors(monitors);

		// Check whether an IllegalArgumentException is thrown when the strategy job name is invalid
		final SimpleStrategy simpleStrategy = SimpleStrategy
			.builder()
			.strategyTime(120L)
			.telemetryManager(TelemetryManager.builder().build())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		final IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> simpleStrategy.hasExpectedJobTypes(connector, "unknown")
		);
		assertEquals("Unknown strategy job name: unknown", exception.getMessage());
	}
}
