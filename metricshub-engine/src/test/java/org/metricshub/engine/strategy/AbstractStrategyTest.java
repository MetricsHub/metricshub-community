package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.CONNECTOR_STATUS_METRIC_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_CONSECUTIVE_DETECTION_FAILURES;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_FAILED;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_OK;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.HOST;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
import static org.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.metricshub.engine.constants.Constants.TEST_CONNECTOR_ID;
import static org.metricshub.engine.strategy.AbstractStrategy.CONNECTOR_ID_FORMAT;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.detection.ConnectorSelection;
import org.metricshub.engine.strategy.detection.ConnectorTestResult;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

class AbstractStrategyTest {

	private static final long STRATEGY_TIME = 1234L;

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

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy.builder()
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

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(true)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy.builder()
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

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy.builder()
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

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.sequential(false)
					.enableSelfMonitoring(true)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();
		final BeforeAllStrategy beforeAllStrategy = BeforeAllStrategy.builder()
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
		final SimpleStrategy simpleStrategy = SimpleStrategy.builder()
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
		final SimpleStrategy simpleStrategy = SimpleStrategy.builder()
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
		final SimpleStrategy simpleStrategy = SimpleStrategy.builder()
			.strategyTime(120L)
			.telemetryManager(TelemetryManager.builder().build())
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			simpleStrategy.hasExpectedJobTypes(connector, "unknown")
		);
		assertEquals("Unknown strategy job name: unknown", exception.getMessage());
	}

	@Test
	void testHealthCheckFailure() {
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Criterion healthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.WINDOWS))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(List.of(healthCheckCriterion))
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_FAILED, false, 1);

		for (int failureCount = 2; failureCount < MAX_CONSECUTIVE_DETECTION_FAILURES; failureCount++) {
			assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
			assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_FAILED, false, failureCount);
		}

		assertFalse(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(
			strategy.getTelemetryManager(),
			STATE_SET_METRIC_FAILED,
			false,
			MAX_CONSECUTIVE_DETECTION_FAILURES
		);
	}

	@Test
	void testOneHealthCheckValidation() {
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.WINDOWS))
			.build();
		final Criterion healthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(List.of(healthCheckCriterion))
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);

		final Monitor validatedConnectorMonitor = strategy
			.getTelemetryManager()
			.findMonitorByTypeAndId(
				KnownMonitorType.CONNECTOR.getKey(),
				String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID)
			);
		final String statusInformation = validatedConnectorMonitor.getLegacyTextParameters().get("StatusInformation");

		assertNotNull(statusInformation);
		assertEquals(1, statusInformation.split("Executed DeviceTypeCriterion Criterion", -1).length - 1);
		assertTrue(statusInformation.contains("- Keep: [LINUX]"));
		assertFalse(statusInformation.contains("- Keep: [WINDOWS]"));
	}

	@Test
	void testThreeHealthChecksValidation() {
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.AIX))
			.build();
		final Criterion firstHealthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Criterion secondHealthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.exclude(Set.of(DeviceKind.WINDOWS))
			.build();
		final Criterion thirdHealthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.STORAGE))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(
						List.of(firstHealthCheckCriterion, secondHealthCheckCriterion, thirdHealthCheckCriterion)
					)
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_FAILED, false, 1);

		final Monitor validatedConnectorMonitor = strategy
			.getTelemetryManager()
			.findMonitorByTypeAndId(
				KnownMonitorType.CONNECTOR.getKey(),
				String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID)
			);
		final String statusInformation = validatedConnectorMonitor.getLegacyTextParameters().get("StatusInformation");

		assertNotNull(statusInformation);
		assertEquals(3, statusInformation.split("Executed DeviceTypeCriterion Criterion", -1).length - 1);
		assertTrue(statusInformation.contains("- Keep: [LINUX]"));
		assertTrue(statusInformation.contains("- Exclude: [WINDOWS]"));
		assertTrue(statusInformation.contains("- Keep: [STORAGE]"));
		assertFalse(statusInformation.contains("- Keep: [AIX]"));
	}

	@Test
	void testHealthChecksWithoutDetection() {
		final Criterion healthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.WINDOWS))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.healthChecks(List.of(healthCheckCriterion))
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_FAILED, false, 1);
	}

	@Test
	void testDetectionThenHealthChecks() {
		final Set<DeviceKind> detectionPlatforms = new HashSet<>(Set.of(DeviceKind.LINUX));
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(detectionPlatforms)
			.build();
		final Criterion healthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(List.of(healthCheckCriterion))
					.build()
			)
			.build();
		final TelemetryManager telemetryManager = newTelemetryManager(connector);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		final List<ConnectorTestResult> detectionResults = new ConnectorSelection(
			telemetryManager,
			clientsExecutor,
			Set.of(TEST_CONNECTOR_ID),
			new ExtensionManager()
		).run();
		assertEquals(1, detectionResults.size());
		assertTrue(detectionResults.get(0).isSuccess());

		detectionPlatforms.clear();
		detectionPlatforms.add(DeviceKind.WINDOWS);
		final CollectStrategy strategy = newValidationStrategy(telemetryManager, clientsExecutor);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);
	}

	@Test
	void testNullHealthChecksFallback() {
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertNotNull(connector.getConnectorIdentity().getHealthChecks());
		assertTrue(connector.getConnectorIdentity().getHealthChecks().isEmpty());
		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);
	}

	@Test
	void testEmptyHealthChecksFallback() {
		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(List.of())
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(connector.getConnectorIdentity().getHealthChecks().isEmpty());
		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);
	}

	private CollectStrategy newValidationStrategy(final Connector connector) {
		final TelemetryManager telemetryManager = newTelemetryManager(connector);
		return newValidationStrategy(telemetryManager, new ClientsExecutor(telemetryManager));
	}

	private CollectStrategy newValidationStrategy(
		final TelemetryManager telemetryManager,
		final ClientsExecutor clientsExecutor
	) {
		return CollectStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(STRATEGY_TIME)
			.clientsExecutor(clientsExecutor)
			.extensionManager(new ExtensionManager())
			.build();
	}

	private TelemetryManager newTelemetryManager(final Connector connector) {
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.addOne(TEST_CONNECTOR_ID, connector);

		return TelemetryManager.builder()
			.monitors(
				new HashMap<>(
					Map.of(
						CONNECTOR,
						Map.of(
							String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID),
							connectorMonitor
						)
					)
				)
			)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.LINUX)
					.enableSelfMonitoring(false)
					.sequential(true)
					.build()
			)
			.connectorStore(connectorStore)
			.build();
	}

	private void assertConnectorStatus(
		final TelemetryManager telemetryManager,
		final String expectedMetricValue,
		final boolean expectedStatusOk,
		final int expectedConsecutiveFailures
	) {
		final Monitor connectorMonitor = telemetryManager.findMonitorByTypeAndId(
			KnownMonitorType.CONNECTOR.getKey(),
			String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), TEST_CONNECTOR_ID)
		);
		final StateSetMetric statusMetric = connectorMonitor.getMetric(CONNECTOR_STATUS_METRIC_KEY, StateSetMetric.class);
		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(TEST_CONNECTOR_ID);

		assertEquals(expectedMetricValue, statusMetric.getValue());
		assertEquals(expectedStatusOk, connectorNamespace.isStatusOk());
		assertEquals(expectedConsecutiveFailures, connectorNamespace.getConsecutiveDetectionFailures());
	}
}
