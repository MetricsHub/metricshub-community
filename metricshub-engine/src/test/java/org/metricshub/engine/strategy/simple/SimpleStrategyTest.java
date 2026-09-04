package org.metricshub.engine.strategy.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.DISK_CONTROLLER;
import static org.metricshub.engine.common.helpers.KnownMonitorType.ENCLOSURE;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.metricshub.engine.constants.Constants.STATUS_INFORMATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimpleStrategyTest {

	@Mock
	private IProtocolExtension protocolExtensionMock;

	private static final Path YAML_TEST_PATH = Paths.get("src", "test", "resources", "test-files", "strategy", "simple");
	private static final String ENCLOSURE_STATUS_METRIC = "hw.status{hw.type=\"enclosure\"}";
	private static final String DISK_CONTROLLER_STATUS_METRIC = "hw.status{hw.type=\"disk_controller\"}";

	@Mock
	private ClientsExecutor clientsExecutorMock;

	static Long strategyTime = new Date().getTime();

	private SimpleStrategy simpleStrategy;

	@Test
	void testRun() throws Exception {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");

		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST.getKey(),
				Map.of("monitor1", hostMonitor),
				CONNECTOR.getKey(),
				Map.of(
					String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), "TestConnectorWithSimple"),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId("host-01")
					.hostname("ec-02")
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.build();

		connectorMonitor.getAttributes().put("id", "TestConnectorWithSimple");

		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		telemetryManager.setConnectorStore(connectorStore);

		// Set simple strategy information
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
		simpleStrategy = SimpleStrategy.builder()
			.clientsExecutor(clientsExecutorMock)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(snmpConfig);
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();
		doReturn(Set.of(SnmpGetNextCriterion.class, SnmpGetCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		// Mock detection criteria result
		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();
		doReturn(CriterionTestResult.success(snmpGetNextCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpGetNextCriterion), anyString(), any(TelemetryManager.class), anyBoolean());

		// Mock source table information for enclosure
		final SnmpTableSource enclosureSource = SnmpTableSource.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.enclosure.simple.sources.source(1)}")
			.build();
		doReturn(
			SourceTable.builder()
				.table(SourceTable.csvToTable("enclosure-1;1;healthy", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(enclosureSource), anyString(), any(TelemetryManager.class));

		// Mock source table information for disk_controller
		final SnmpTableSource diskControllerSource = SnmpTableSource.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.simple.sources.source(1)}")
			.build();
		doReturn(SourceTable.builder().table(SourceTable.csvToTable("1;1;healthy", MetricsHubConstants.TABLE_SEP)).build())
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));

		simpleStrategy.run();

		// Check processed monitors
		final Map<String, Map<String, Monitor>> processedMonitors = telemetryManager.getMonitors();

		final Map<String, Monitor> enclosureMonitors = processedMonitors.get(ENCLOSURE.getKey());
		final Map<String, Monitor> diskControllerMonitors = processedMonitors.get(DISK_CONTROLLER.getKey());

		assertEquals(4, processedMonitors.size());
		assertEquals(1, enclosureMonitors.size());
		assertEquals(1, diskControllerMonitors.size());

		// Check processed monitors metrics
		final Monitor enclosure = enclosureMonitors.get("TestConnectorWithSimple_enclosure_enclosure-1");
		final Monitor diskController = diskControllerMonitors.get("TestConnectorWithSimple_disk_controller_1");

		assertEquals(1.0, enclosure.getMetric(ENCLOSURE_STATUS_METRIC, NumberMetric.class).getValue());
		assertEquals(1.0, diskController.getMetric(DISK_CONTROLLER_STATUS_METRIC, NumberMetric.class).getValue());

		// Check that StatusInformation is collected on the connector monitor (criterion processing success case)
		assertEquals(
			"Executed SnmpGetNextCriterion Criterion:\n" +
				"- OID: 1.3.6.1.4.1.795.10.1.1.3.1.1\n" +
				"\n" +
				"Result:\n" +
				"1.3.6.1.4.1.795.10.1.1.3.1.1.0\tASN_OCTET_STR\tTest\n" +
				"\n" +
				"Message:\n" +
				"====================================\n" +
				"SnmpGetNextCriterion test succeeded:\n" +
				"- OID: 1.3.6.1.4.1.795.10.1.1.3.1.1\n" +
				"\n" +
				"Result: 1.3.6.1.4.1.795.10.1.1.3.1.1.0\tASN_OCTET_STR\tTest\n" +
				"====================================\n" +
				"\n" +
				"Conclusion:\n" +
				"Test on ec-02 SUCCEEDED",
			connectorMonitor.getLegacyTextParameters().get(STATUS_INFORMATION)
		);

		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("monitor1")
				.getMetric(
					"metricshub.job.duration{job.type=\"simple\", monitor.type=\"disk_controller\", connector_id=\"TestConnectorWithSimple\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("monitor1")
				.getMetric(
					"metricshub.job.duration{job.type=\"simple\", monitor.type=\"enclosure\", connector_id=\"TestConnectorWithSimple\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("monitor1")
				.getMetric(
					"metricshub.job.duration{job.type=\"simple\", monitor.type=\"connector\", connector_id=\"TestConnectorWithSimple\"}"
				)
				.getValue()
		);

		// Mock detection criteria result to switch to a failing criterion processing case
		doReturn(CriterionTestResult.failure(snmpGetNextCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpGetNextCriterion), anyString(), any(TelemetryManager.class), anyBoolean());

		// Call DiscoveryStrategy to discover the monitors
		simpleStrategy.run();

		// Check that StatusInformation is collected on the connector monitor (criterion processing failure case)
		assertEquals(
			"Executed SnmpGetNextCriterion Criterion:\n" +
				"- OID: 1.3.6.1.4.1.795.10.1.1.3.1.1\n" +
				"\n" +
				"Result:\n" +
				"1.3.6.1.4.1.795.10.1.1.3.1.1.0\tASN_OCTET_STR\tTest\n" +
				"\n" +
				"Message:\n" +
				"====================================\n" +
				"SnmpGetNextCriterion test ran but failed:\n" +
				"- OID: 1.3.6.1.4.1.795.10.1.1.3.1.1\n" +
				"\n" +
				"Actual result:\n" +
				"1.3.6.1.4.1.795.10.1.1.3.1.1.0\tASN_OCTET_STR\tTest\n" +
				"====================================\n" +
				"\n" +
				"Conclusion:\n" +
				"Test on ec-02 FAILED",
			connectorMonitor.getLegacyTextParameters().get(STATUS_INFORMATION)
		);
	}

	@Test
	void testRunDoesNotRefreshSimpleMetricsNoLongerCollected() throws Exception {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");

		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		connectorMonitor.getAttributes().put("id", "TestConnectorWithSimple");
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST.getKey(),
				Map.of("monitor1", hostMonitor),
				CONNECTOR.getKey(),
				Map.of(
					String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), "TestConnectorWithSimple"),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId("host-01")
					.hostname("ec-02")
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.connectorStore(new ConnectorStore(YAML_TEST_PATH))
			.build();

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(snmpConfig);
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();
		doReturn(Set.of(SnmpGetNextCriterion.class, SnmpGetCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		// Mock detection criteria result
		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();
		doReturn(CriterionTestResult.success(snmpGetNextCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpGetNextCriterion), anyString(), any(TelemetryManager.class), anyBoolean());

		// Mock source table information for enclosure
		final SnmpTableSource enclosureSource = SnmpTableSource.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.enclosure.simple.sources.source(1)}")
			.build();
		doReturn(
			SourceTable.builder()
				.table(SourceTable.csvToTable("enclosure-1;1;healthy", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(enclosureSource), anyString(), any(TelemetryManager.class));

		// Mock source table information for disk_controller
		final SnmpTableSource diskControllerSource = SnmpTableSource.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.simple.sources.source(1)}")
			.build();
		doReturn(SourceTable.builder().table(SourceTable.csvToTable("1;1;healthy", MetricsHubConstants.TABLE_SEP)).build())
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));

		// First collect cycle: both simple jobs collect their metrics
		final long firstCollectTime = strategyTime;
		SimpleStrategy.builder()
			.clientsExecutor(clientsExecutorMock)
			.strategyTime(firstCollectTime)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build()
			.run();

		final Monitor enclosure = telemetryManager
			.getMonitors()
			.get(ENCLOSURE.getKey())
			.get("TestConnectorWithSimple_enclosure_enclosure-1");
		final Monitor diskController = telemetryManager
			.getMonitors()
			.get(DISK_CONTROLLER.getKey())
			.get("TestConnectorWithSimple_disk_controller_1");

		final NumberMetric enclosureStatus = enclosure.getMetric(ENCLOSURE_STATUS_METRIC, NumberMetric.class);
		final NumberMetric diskControllerStatus = diskController.getMetric(
			DISK_CONTROLLER_STATUS_METRIC,
			NumberMetric.class
		);

		// Simple metrics are collected on each collect cycle, so they must not be flagged for a collect time reset
		assertFalse(enclosureStatus.isResetMetricTime());
		assertFalse(diskControllerStatus.isResetMetricTime());
		assertEquals(firstCollectTime, enclosureStatus.getCollectTime());
		assertEquals(firstCollectTime, diskControllerStatus.getCollectTime());

		// Second collect cycle: the enclosure source no longer returns any row
		final long secondCollectTime = firstCollectTime + 60 * 1000;
		doReturn(SourceTable.empty())
			.when(protocolExtensionMock)
			.processSource(eq(enclosureSource), anyString(), any(TelemetryManager.class));

		new PrepareCollectStrategy(telemetryManager, secondCollectTime, clientsExecutorMock, extensionManager).run();
		SimpleStrategy.builder()
			.clientsExecutor(clientsExecutorMock)
			.strategyTime(secondCollectTime)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build()
			.run();

		// The enclosure status was not collected: it keeps its previous collect time and is not reported as updated
		final NumberMetric staleEnclosureStatus = enclosure.getMetric(ENCLOSURE_STATUS_METRIC, NumberMetric.class);
		assertEquals(firstCollectTime, staleEnclosureStatus.getCollectTime());
		assertEquals(firstCollectTime, staleEnclosureStatus.getPreviousCollectTime());
		assertFalse(staleEnclosureStatus.isUpdated());

		// The disk controller status was collected again: it is reported as updated
		final NumberMetric refreshedDiskControllerStatus = diskController.getMetric(
			DISK_CONTROLLER_STATUS_METRIC,
			NumberMetric.class
		);
		assertEquals(secondCollectTime, refreshedDiskControllerStatus.getCollectTime());
		assertEquals(firstCollectTime, refreshedDiskControllerStatus.getPreviousCollectTime());
		assertTrue(refreshedDiskControllerStatus.isUpdated());
	}
}
