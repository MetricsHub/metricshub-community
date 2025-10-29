package org.metricshub.engine.strategy.surrounding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.DISK_CONTROLLER;
import static org.metricshub.engine.constants.Constants.ENCLOSURE;
import static org.metricshub.engine.constants.Constants.HEALTHY;
import static org.metricshub.engine.constants.Constants.HOST;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
import static org.metricshub.engine.constants.Constants.ID;
import static org.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.metricshub.engine.constants.Constants.STATUS_INFORMATION;
import static org.metricshub.engine.strategy.AbstractStrategy.CONNECTOR_ID_FORMAT;
import static org.mockito.ArgumentMatchers.any;
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
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BeforeAllStrategyTest {

	// Connector path
	public static final Path TEST_CONNECTOR_PATH = Paths.get(
		"src",
		"test",
		"resources",
		"test-files",
		"strategy",
		"beforeAll"
	);

	@Mock
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private IProtocolExtension protocolExtensionMock;

	private IStrategy collectStrategy;

	private IStrategy discoveryStrategy;

	static Long strategyTime = new Date().getTime();

	TelemetryManager telemetryManager;
	Monitor enclosure;
	Monitor diskController;

	void initTest() {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST,
				Map.of(MONITOR_ID_ATTRIBUTE_VALUE, hostMonitor),
				CONNECTOR,
				Map.of(
					String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), "beforeAllSource"),
					connectorMonitor
				)
			)
		);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostId(HOST_ID)
						.hostname(HOST_NAME)
						.hostType(DeviceKind.LINUX)
						.sequential(false)
						.configurations(Map.of(TestConfiguration.class, snmpConfig))
						.build()
				)
				.build();

		MonitorFactory monitorFactory = MonitorFactory
			.builder()
			.monitorType(ENCLOSURE)
			.telemetryManager(telemetryManager)
			.connectorId("beforeAllSource")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "enclosure-1")))
			.discoveryTime(strategyTime - 30 * 60 * 1000)
			.keys(DEFAULT_KEYS)
			.build();
		enclosure = monitorFactory.createOrUpdateMonitor();

		monitorFactory =
			MonitorFactory
				.builder()
				.monitorType(DISK_CONTROLLER)
				.telemetryManager(telemetryManager)
				.connectorId("beforeAllSource")
				.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "1")))
				.discoveryTime(strategyTime - 30 * 60 * 1000)
				.keys(DEFAULT_KEYS)
				.build();
		diskController = monitorFactory.createOrUpdateMonitor();

		hostMonitor.addAttribute(IS_ENDPOINT, "true");

		connectorMonitor.addAttribute(ID, "beforeAllSource");

		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(TEST_CONNECTOR_PATH);
		telemetryManager.setConnectorStore(connectorStore);
	}

	@Test
	void testRunFromCollect() {
		initTest();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		collectStrategy =
			CollectStrategy
				.builder()
				.clientsExecutor(clientsExecutorMock)
				.strategyTime(strategyTime)
				.telemetryManager(telemetryManager)
				.extensionManager(extensionManager)
				.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();
		doReturn(Set.of(SnmpGetNextCriterion.class, SnmpGetCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		// Mock the criterion
		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();
		doReturn(CriterionTestResult.success(snmpGetNextCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpGetNextCriterion), anyString(), any(TelemetryManager.class));

		// Mock source table information for enclosure
		final SnmpTableSource enclosureSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.30.1")
			.selectColumns("ID,1,2")
			.type("snmpTable")
			.key("${source::monitors.enclosure.collect.sources.source(1)}")
			.build();
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("enclosure-1;1;healthy", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(enclosureSource), anyString(), any(TelemetryManager.class));

		// Mock source table information for disk_controller
		final SnmpTableSource diskControllerSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.31.1")
			.selectColumns("ID,1,2")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.collect.sources.source(1)}")
			.build();
		doReturn(SourceTable.builder().table(SourceTable.csvToTable("1;1;healthy", MetricsHubConstants.TABLE_SEP)).build())
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));

		final SnmpTableSource beforeSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.5")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::beforeAll.snmpSource}")
			.build();
		// Mock source table information for the snmp beforeAll source
		doReturn(
			SourceTable
				.builder()
				.table(
					SourceTable.csvToTable("beforeAllSource-1;1;2;3;4;5;6;7;healthy;health-ok", MetricsHubConstants.TABLE_SEP)
				)
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(beforeSource), anyString(), any(TelemetryManager.class));

		collectStrategy.run();

		// Check metrics
		assertEquals(
			1.0,
			diskController.getMetric("hw.status{hw.type=\"disk_controller\"}", NumberMetric.class).getValue()
		);
		assertEquals(HEALTHY, diskController.getLegacyTextParameters().get(STATUS_INFORMATION));
		assertEquals(1.0, enclosure.getMetric("hw.status{hw.type=\"enclosure\"}", NumberMetric.class).getValue());
		assertEquals(HEALTHY, enclosure.getLegacyTextParameters().get(STATUS_INFORMATION));

		// Check the successful execution of BeforeAllStrategy
		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace("beforeAllSource");
		assertNotNull(connectorNamespace);
		final SourceTable sourceTable = connectorNamespace.getSourceTables().get("${source::beforeAll.snmpSource}");
		assertNotNull(sourceTable);
		List<String> sourceTableLine = sourceTable.getTable().get(0);
		assertEquals("beforeAllSource-1", sourceTableLine.get(0));
		assertEquals("1", sourceTableLine.get(1));
		assertEquals("2", sourceTableLine.get(2));
		assertEquals("3", sourceTableLine.get(3));
		assertEquals("4", sourceTableLine.get(4));
		assertEquals("5", sourceTableLine.get(5));
		assertEquals("6", sourceTableLine.get(6));
		assertEquals("7", sourceTableLine.get(7));
		assertEquals("healthy", sourceTableLine.get(8));
		assertEquals("health-ok", sourceTableLine.get(9));

		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"disk_controller\", connector_id=\"beforeAllSource\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric("metricshub.job.duration{job.type=\"beforeAll\", connector_id=\"beforeAllSource\"}")
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"collect\", monitor.type=\"enclosure\", connector_id=\"beforeAllSource\"}"
				)
				.getValue()
		);
	}

	@Test
	void testRunFromDiscovery() {
		initTest();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		discoveryStrategy =
			DiscoveryStrategy
				.builder()
				.clientsExecutor(clientsExecutorMock)
				.strategyTime(strategyTime)
				.telemetryManager(telemetryManager)
				.extensionManager(extensionManager)
				.build();

		// Mock detection criteria result
		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();
		doReturn(Set.of(SnmpGetNextCriterion.class, SnmpGetCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		// Mock the criterion
		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();
		doReturn(CriterionTestResult.success(snmpGetNextCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpGetNextCriterion), anyString(), any(TelemetryManager.class));

		// Mock source table information for enclosure
		final SnmpTableSource enclosureSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.enclosure.discovery.sources.source(1)}")
			.build();
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("enclosure-1;1;healthy", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(enclosureSource), anyString(), any(TelemetryManager.class));

		// Mock source table information for disk_controller
		final SnmpTableSource diskControllerSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.discovery.sources.source(1)}")
			.build();
		doReturn(
			SourceTable
				.builder()
				.table(
					SourceTable.csvToTable("diskController-1;1;2;3;4;5;6;7;healthy;health-ok", MetricsHubConstants.TABLE_SEP)
				)
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));

		// Mock source table information for the snmp beforeAll source
		final SnmpTableSource beforeAllSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.5")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::beforeAll.snmpSource}")
			.build();
		// Mock source table information for the snmp beforeAll source
		doReturn(
			SourceTable
				.builder()
				.table(
					SourceTable.csvToTable("beforeAllSource-1;1;2;3;4;5;6;7;healthy;health-ok", MetricsHubConstants.TABLE_SEP)
				)
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(beforeAllSource), anyString(), any(TelemetryManager.class));

		discoveryStrategy.run();

		// Check the successful execution of BeforeAllStrategy
		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace("beforeAllSource");
		assertNotNull(connectorNamespace);
		final SourceTable sourceTable = connectorNamespace.getSourceTables().get("${source::beforeAll.snmpSource}");
		assertNotNull(sourceTable);
		assertEquals("beforeAllSource-1", sourceTable.getTable().get(0).get(0));
		assertEquals("1", sourceTable.getTable().get(0).get(1));
		assertEquals("2", sourceTable.getTable().get(0).get(2));
		assertEquals("3", sourceTable.getTable().get(0).get(3));
		assertEquals("4", sourceTable.getTable().get(0).get(4));
		assertEquals("5", sourceTable.getTable().get(0).get(5));
		assertEquals("6", sourceTable.getTable().get(0).get(6));
		assertEquals("7", sourceTable.getTable().get(0).get(7));
		assertEquals("healthy", sourceTable.getTable().get(0).get(8));
		assertEquals("health-ok", sourceTable.getTable().get(0).get(9));
	}
}
