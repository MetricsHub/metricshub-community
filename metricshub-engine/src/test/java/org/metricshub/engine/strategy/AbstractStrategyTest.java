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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.TextUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.detection.ConnectorSelection;
import org.metricshub.engine.strategy.detection.ConnectorTestResult;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
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
			telemetryManager.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"connector\", connector_id=\"TestConnector\"}"
				)
				.getValue()
		);
	}

	@Test
	void testInterruptedThreadWritesNoHostMetric() {
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(new HashMap<>(Map.of(HOST, Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor))))
			.hostConfiguration(
				HostConfiguration.builder().hostId(HOST_ID).hostname(HOST_NAME).enableSelfMonitoring(true).build()
			)
			.build();
		final CollectStrategy collectStrategy = CollectStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(STRATEGY_TIME)
			.clientsExecutor(new ClientsExecutor())
			.extensionManager(new ExtensionManager())
			.build();

		// An abandoned thread shares the host monitor with the next run: it writes nothing there
		Thread.currentThread().interrupt();
		try {
			collectStrategy.setJobDurationMetric("collect", "enclosure", TEST_CONNECTOR_ID, 0L, 1000L);
			collectStrategy.collectHostConfigured(HOST_NAME);
			collectStrategy.collectHostObserved(1.0);

			assertTrue(hostMonitor.getMetrics().isEmpty());
		} finally {
			Thread.interrupted();
		}
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
			telemetryManager.getMonitors()
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
			telemetryManager.getMonitors()
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
			telemetryManager.getMonitors()
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
		final IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> simpleStrategy.hasExpectedJobTypes(connector, "unknown")
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
	void testInterruptedValidationLeavesConnectorStatus() {
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.criteria(
								List.of(DeviceTypeCriterion.builder().type("deviceType").keep(Set.of(DeviceKind.LINUX)).build())
							)
							.build()
					)
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);
		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);

		// Interrupted validations (job timeout, reload) count no failure and never stop the connector's job
		Thread.currentThread().interrupt();
		try {
			for (int run = 0; run < MAX_CONSECUTIVE_DETECTION_FAILURES; run++) {
				assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
			}
			assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_OK, true, 0);
		} finally {
			Thread.interrupted();
		}
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

		final Monitor validatedConnectorMonitor = strategy.getTelemetryManager()
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
					.healthChecks(List.of(firstHealthCheckCriterion, secondHealthCheckCriterion, thirdHealthCheckCriterion))
					.build()
			)
			.build();
		final CollectStrategy strategy = newValidationStrategy(connector);

		assertTrue(strategy.validateConnectorDetectionCriteria(connector, HOST_NAME, "collect"));
		assertConnectorStatus(strategy.getTelemetryManager(), STATE_SET_METRIC_FAILED, false, 1);

		final Monitor validatedConnectorMonitor = strategy.getTelemetryManager()
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

	@Test
	void testEmptyResultTypedBySource() {
		final CopySource copy = CopySource.builder().from("${source::a}").build();
		final TableUnionSource union = TableUnionSource.builder().tables(List.of("${source::a}")).build();
		final HttpSource http = HttpSource.builder().url("/x").build();
		final SnmpTableSource snmp = SnmpTableSource.builder().oid("1.3").selectColumns("1").build();
		final TextUnionSource textUnion = TextUnionSource.builder().texts(List.of("${source::a}")).build();

		// Each source is typed statically: a copy by what it copies, as the load-time check found it
		assertEquals(ContentType.TABLE, AbstractStrategy.emptyResult(copy).getContentType());
		copy.setOutputContentType(ContentType.TEXT);
		assertEquals(ContentType.TEXT, AbstractStrategy.emptyResult(copy).getContentType());
		assertEquals(ContentType.TEXT, AbstractStrategy.emptyResult(copy.copy()).getContentType());
		assertEquals(ContentType.TABLE, AbstractStrategy.emptyResult(union).getContentType());
		assertEquals(ContentType.TEXT, AbstractStrategy.emptyResult(http).getContentType());
		assertEquals(ContentType.TABLE, AbstractStrategy.emptyResult(snmp).getContentType());
		assertEquals(ContentType.TEXT, AbstractStrategy.emptyResult(textUnion).getContentType());

		// A fresh result every time
		assertFalse(AbstractStrategy.emptyResult(copy) == AbstractStrategy.emptyResult(copy));
	}

	@Test
	void testInterruptedJobSkipsRemainingSources() {
		final TelemetryManager telemetryManager = newInterruptibleTelemetryManager();
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(SnmpTableSource.class)).when(extension).getSupportedSources();
		final AtomicInteger requests = new AtomicInteger();
		doAnswer(invocation -> {
			// The job deadline expires during the second source, which still returns a table
			if (requests.incrementAndGet() == 2) {
				Thread.currentThread().interrupt();
			}
			return SourceTable.ofTable(SourceTable.csvToTable("a;1", ";"));
		}).when(extension).processSource(any(), anyString(), any());

		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(TEST_CONNECTOR_ID);
		// A table written by another run (the next run, when this thread is an abandoned one)
		namespace.addSourceTable("${source::c}", SourceTable.ofTable(SourceTable.csvToTable("old;1", ";")));

		final List<Source> sources = List.of("a", "b", "c")
			.stream()
			.map(
				key -> (Source) SnmpTableSource.builder().oid("1.3").selectColumns("1").key("${source::" + key + "}").build()
			)
			.toList();
		final SimpleStrategy strategy = SimpleStrategy.builder()
			.strategyTime(STRATEGY_TIME)
			.telemetryManager(telemetryManager)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build())
			.build();

		try {
			final boolean allAnswered = strategy.processSourcesAndComputes(
				sources,
				JobInfo.builder()
					.hostname(HOST_NAME)
					.connectorId(TEST_CONNECTOR_ID)
					.jobName("simple")
					.monitorType("enclosure")
					.build()
			);

			assertEquals(2, requests.get());
			assertFalse(allAnswered);
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(List.of(List.of("a", "1")), namespace.getSourceTable("${source::a}").getTable());
			// The source interrupted while it ran may have been cut short: it did not answer, whatever it returned.
			// The interrupted thread writes nothing more: the table of another run is left alone
			assertNull(namespace.getSourceTable("${source::b}"));
			assertEquals(List.of(List.of("old", "1")), namespace.getSourceTable("${source::c}").getTable());
			assertTrue(namespace.getUnansweredSourceKeys().isEmpty());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testInterruptedJobRollsBackIncrementalCursors() {
		final TelemetryManager telemetryManager = newInterruptibleTelemetryManager();
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(TEST_CONNECTOR_ID);
		namespace.setSourceCursor("${source::sel}", "sel-10");

		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(IpmiSelSource.class, SnmpTableSource.class)).when(extension).getSupportedSources();
		// The SEL poll answers and moves its cursor, then the job deadline expires during the next source
		doAnswer(invocation -> {
			if (invocation.getArgument(0) instanceof IpmiSelSource) {
				namespace.setSourceCursor("${source::sel}", "sel-20");
				return SourceTable.ofTable(SourceTable.csvToTable("event;1", ";"));
			}
			Thread.currentThread().interrupt();
			return SourceTable.empty();
		}).when(extension).processSource(any(), anyString(), any());

		final SimpleStrategy strategy = SimpleStrategy.builder()
			.strategyTime(STRATEGY_TIME)
			.telemetryManager(telemetryManager)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build())
			.build();

		try {
			final boolean allAnswered = strategy.processSourcesAndComputes(
				List.of(
					IpmiSelSource.builder().key("${source::sel}").build(),
					SnmpTableSource.builder().oid("1.3").selectColumns("1").key("${source::next}").build()
				),
				JobInfo.builder()
					.hostname(HOST_NAME)
					.connectorId(TEST_CONNECTOR_ID)
					.jobName("discovery")
					.monitorType("enclosure")
					.build()
			);

			// The job is not mapped: the next poll must return the SEL events again
			assertFalse(allAnswered);
			assertEquals("sel-10", namespace.getSourceCursor("${source::sel}"));
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testInterruptedLastComputeStoresNoTable() throws Exception {
		final TelemetryManager telemetryManager = newInterruptibleTelemetryManager();
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(HttpSource.class)).when(extension).getSupportedSources();
		doReturn(SourceTable.ofText("{\"a\":1}")).when(extension).processSource(any(), anyString(), any());

		// The job deadline expires during the last (and only) compute of the source
		final ClientsExecutor clientsExecutor = mock(ClientsExecutor.class);
		doAnswer(invocation -> {
			Thread.currentThread().interrupt();
			return List.of(List.of("x", "1"));
		}).when(clientsExecutor).executeJson2Table(any(), any(), any());

		final HttpSource source = HttpSource.builder()
			.url("/x")
			.key("${source::a}")
			.computes(List.of(Json2Csv.builder().properties(List.of("a")).build()))
			.build();
		final SimpleStrategy strategy = SimpleStrategy.builder()
			.strategyTime(STRATEGY_TIME)
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutor)
			.extensionManager(ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build())
			.build();

		try {
			final boolean allAnswered = strategy.processSourcesAndComputes(
				List.of(source),
				JobInfo.builder()
					.hostname(HOST_NAME)
					.connectorId(TEST_CONNECTOR_ID)
					.jobName("simple")
					.monitorType("enclosure")
					.build()
			);

			// The half computed table is not stored: the namespace may be the next run's
			assertFalse(allAnswered);
			assertNull(
				telemetryManager.getHostProperties().getConnectorNamespace(TEST_CONNECTOR_ID).getSourceTable("${source::a}")
			);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testInterruptedDetectionSkipsRemainingCriteria() {
		final TelemetryManager telemetryManager = newInterruptibleTelemetryManager();
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(SnmpGetCriterion.class)).when(extension).getSupportedCriteria();
		final AtomicInteger requests = new AtomicInteger();
		doAnswer(invocation -> {
			requests.incrementAndGet();
			Thread.currentThread().interrupt();
			return CriterionTestResult.success(invocation.getArgument(0), "ok");
		}).when(extension).processCriterion(any(), anyString(), any(), anyBoolean());

		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(TEST_CONNECTOR_ID)
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.criteria(
								List.of(
									SnmpGetCriterion.builder().oid("1.3.1").build(),
									SnmpGetCriterion.builder().oid("1.3.2").build()
								)
							)
							.build()
					)
					.build()
			)
			.build();

		try {
			final ConnectorTestResult result = new ConnectorSelection(
				telemetryManager,
				new ClientsExecutor(telemetryManager),
				Set.of(TEST_CONNECTOR_ID),
				ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build()
			).runConnectorDetectionCriteria(connector, HOST_NAME);

			assertEquals(1, requests.get());
			// A detection cut short never matches
			assertFalse(result.isSuccess());
			assertEquals(2, result.getCriterionTestResults().size());
			assertTrue(Thread.currentThread().isInterrupted());

			// The only criterion reports a success, but the interrupt arrived while it was tested
			Thread.interrupted();
			connector.getConnectorIdentity()
				.getDetection()
				.setCriteria(List.of(SnmpGetCriterion.builder().oid("1.3.1").build()));
			final ConnectorTestResult lastCriterionResult = new ConnectorSelection(
				telemetryManager,
				new ClientsExecutor(telemetryManager),
				Set.of(TEST_CONNECTOR_ID),
				ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build()
			).runConnectorDetectionCriteria(connector, HOST_NAME);

			assertEquals(2, requests.get());
			assertFalse(lastCriterionResult.isSuccess());
			assertEquals(2, lastCriterionResult.getCriterionTestResults().size());
		} finally {
			Thread.interrupted();
		}
	}

	private TelemetryManager newInterruptibleTelemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.hostType(DeviceKind.LINUX)
					.sequential(true)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
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
		final ConnectorNamespace connectorNamespace = telemetryManager.getHostProperties()
			.getConnectorNamespace(TEST_CONNECTOR_ID);

		assertEquals(expectedMetricValue, statusMetric.getValue());
		assertEquals(expectedStatusOk, connectorNamespace.isStatusOk());
		assertEquals(expectedConsecutiveFailures, connectorNamespace.getConsecutiveDetectionFailures());
	}
}
