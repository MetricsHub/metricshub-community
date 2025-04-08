package org.metricshub.engine.strategy.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.DISK_CONTROLLER;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.KnownMonitorType.LOGICAL_DISK;
import static org.metricshub.engine.common.helpers.KnownMonitorType.PHYSICAL_DISK;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
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
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.criterion.SnmpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Multiply;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoveryStrategyTest {

	private static final Path YAML_TEST_PATH = Paths.get(
		"src",
		"test",
		"resources",
		"test-files",
		"strategy",
		"discovery"
	);
	private static final String AAC_CONNECTOR_ID = "AAC";

	@Mock
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private IProtocolExtension protocolExtensionMock;

	static Long strategyTime = new Date().getTime();
	private DiscoveryStrategy discoveryStrategy;

	@Test
	void testRun() throws Exception {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST.getKey(),
				Map.of("anyMonitorId", hostMonitor),
				CONNECTOR.getKey(),
				Map.of(String.format(CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), AAC_CONNECTOR_ID), connectorMonitor)
			)
		);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
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
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");
		hostMonitor.setIsEndpoint(true);
		connectorMonitor.getAttributes().put("id", AAC_CONNECTOR_ID);
		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		telemetryManager.setConnectorStore(connectorStore);
		discoveryStrategy =
			DiscoveryStrategy
				.builder()
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

		final SnmpCriterion snmpCriterion = SnmpGetNextCriterion
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();

		// Mock detection criteria result
		doReturn(CriterionTestResult.success(snmpCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpCriterion), anyString(), any(TelemetryManager.class));
		// Mock Disk Controller Source
		final SnmpTableSource diskControllerSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.discovery.sources.source(1)}")
			.build();
		// Mock source table information for disk controller
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("controller-1;1;Adaptec1;bios53v2;firmware32", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));
		// Mock Physical Disk Source
		final SnmpTableSource physicalDiskSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.5.1")
			.selectColumns("ID,2,6,7,8,10,11")
			.type("snmpTable")
			.key("${source::monitors.physical_disk.discovery.sources.source(1)}")
			.computes(
				List.of(
					KeepOnlyMatchingLines.builder().column(3).valueList("0").type("keepOnlyMatchingLines").build(),
					Multiply.builder().column(6).value("$7").type("multiply").build()
				)
			)
			.build();
		// Mock source table information for physical_disk
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("disk-1;1;0;vendor-1;5;500000;512", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(physicalDiskSource), anyString(), any(TelemetryManager.class));

		// Mock Logical Disk Source
		final SnmpTableSource logicalDiskSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.1")
			.selectColumns("ID,2,4,6")
			.type("snmpTable")
			.key("${source::monitors.logical_disk.discovery.sources.source(1)}")
			.computes(List.of(Multiply.builder().column(3).value("1048576").type("multiply").build()))
			.build();
		// Mock source table information for logical_disk
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("logical-disk-1;1;500;RAID-5", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(logicalDiskSource), anyString(), any(TelemetryManager.class));
		// Call DiscoveryStrategy to discover the monitors
		discoveryStrategy.run();
		// Check discovered monitors
		Map<String, Map<String, Monitor>> discoveredMonitors = telemetryManager.getMonitors();
		assertEquals(5, discoveredMonitors.size());
		assertEquals(1, discoveredMonitors.get(HOST.getKey()).size());
		assertEquals(1, discoveredMonitors.get(CONNECTOR.getKey()).size());
		assertEquals(1, discoveredMonitors.get(DISK_CONTROLLER.getKey()).size());
		assertEquals(1, discoveredMonitors.get(PHYSICAL_DISK.getKey()).size());
		assertEquals(1, discoveredMonitors.get(LOGICAL_DISK.getKey()).size());
		// Check discovered monitors order
		Set<String> expectedOrder = Set.of(
			HOST.getKey(),
			DISK_CONTROLLER.getKey(),
			CONNECTOR.getKey(),
			LOGICAL_DISK.getKey(),
			PHYSICAL_DISK.getKey()
		);
		assertEquals(expectedOrder, discoveredMonitors.keySet());

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
			"Test on host.name SUCCEEDED",
			connectorMonitor.getLegacyTextParameters().get(STATUS_INFORMATION)
		);

		// Mock detection criteria result to switch to a failing criterion processing case
		doReturn(CriterionTestResult.failure(snmpCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpCriterion), anyString(), any(TelemetryManager.class));

		// Call DiscoveryStrategy to discover the monitors
		discoveryStrategy.run();

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
			"Test on host.name FAILED",
			connectorMonitor.getLegacyTextParameters().get(STATUS_INFORMATION)
		);

		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\"," + " monitor.type=\"logical_disk\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\"," + " monitor.type=\"disk_controller\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\"," + " monitor.type=\"physical_disk\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\"," + " monitor.type=\"connector\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
	}

	@Test
	void testRunWithIncludeFilter() throws Exception {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).build();
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST.getKey(),
				Map.of("anyMonitorId", hostMonitor),
				CONNECTOR.getKey(),
				Map.of(String.format(CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), AAC_CONNECTOR_ID), connectorMonitor)
			)
		);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
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
					.includedMonitors(Set.of(DISK_CONTROLLER.getKey()))
					.build()
			)
			.build();
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");
		hostMonitor.setIsEndpoint(true);
		connectorMonitor.getAttributes().put("id", AAC_CONNECTOR_ID);
		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		telemetryManager.setConnectorStore(connectorStore);
		discoveryStrategy =
			DiscoveryStrategy
				.builder()
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

		final SnmpCriterion snmpCriterion = SnmpGetNextCriterion
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();

		// Mock detection criteria result
		doReturn(CriterionTestResult.success(snmpCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpCriterion), anyString(), any(TelemetryManager.class));
		// Mock Disk Controller Source
		final SnmpTableSource diskControllerSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1")
			.selectColumns("ID,1,3,7,8")
			.type("snmpTable")
			.key("${source::monitors.disk_controller.discovery.sources.source(1)}")
			.build();
		// Mock source table information for disk controller
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("controller-1;1;Adaptec1;bios53v2;firmware32", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(diskControllerSource), anyString(), any(TelemetryManager.class));

		// Call DiscoveryStrategy to discover the monitors
		discoveryStrategy.run();
		// Check discovered monitors
		Map<String, Map<String, Monitor>> discoveredMonitors = telemetryManager.getMonitors();
		assertEquals(3, discoveredMonitors.size());
		assertEquals(1, discoveredMonitors.get(HOST.getKey()).size());
		assertEquals(1, discoveredMonitors.get(CONNECTOR.getKey()).size());
		assertEquals(1, discoveredMonitors.get(DISK_CONTROLLER.getKey()).size());
		assertNull(discoveredMonitors.get(PHYSICAL_DISK.getKey()));
		assertNull(discoveredMonitors.get(LOGICAL_DISK.getKey()));
	}

	@Test
	void testRunWithExcludeFilter() throws Exception {
		// Create host and connector monitors and set them in the telemetry manager
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).build();
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(
				HOST.getKey(),
				Map.of("anyMonitorId", hostMonitor),
				CONNECTOR.getKey(),
				Map.of(String.format(CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), AAC_CONNECTOR_ID), connectorMonitor)
			)
		);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
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
					.excludedMonitors(Set.of(DISK_CONTROLLER.getKey()))
					.build()
			)
			.build();
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");
		hostMonitor.setIsEndpoint(true);
		connectorMonitor.getAttributes().put("id", AAC_CONNECTOR_ID);
		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		telemetryManager.setConnectorStore(connectorStore);
		discoveryStrategy =
			DiscoveryStrategy
				.builder()
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

		final SnmpCriterion snmpCriterion = SnmpGetNextCriterion
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.3.1.1")
			.type("snmpGetNext")
			.build();

		// Mock detection criteria result
		doReturn(CriterionTestResult.success(snmpCriterion, "1.3.6.1.4.1.795.10.1.1.3.1.1.0	ASN_OCTET_STR	Test"))
			.when(protocolExtensionMock)
			.processCriterion(eq(snmpCriterion), anyString(), any(TelemetryManager.class));
		// Mock Physical Disk Source
		final SnmpTableSource physicalDiskSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.5.1")
			.selectColumns("ID,2,6,7,8,10,11")
			.type("snmpTable")
			.key("${source::monitors.physical_disk.discovery.sources.source(1)}")
			.computes(
				List.of(
					KeepOnlyMatchingLines.builder().column(3).valueList("0").type("keepOnlyMatchingLines").build(),
					Multiply.builder().column(6).value("$7").type("multiply").build()
				)
			)
			.build();
		// Mock source table information for physical_disk
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("disk-1;1;0;vendor-1;5;500000;512", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(physicalDiskSource), anyString(), any(TelemetryManager.class));

		// Mock Logical Disk Source
		final SnmpTableSource logicalDiskSource = SnmpTableSource
			.builder()
			.oid("1.3.6.1.4.1.795.10.1.1.4.1")
			.selectColumns("ID,2,4,6")
			.type("snmpTable")
			.key("${source::monitors.logical_disk.discovery.sources.source(1)}")
			.computes(List.of(Multiply.builder().column(3).value("1048576").type("multiply").build()))
			.build();
		// Mock source table information for logical_disk
		doReturn(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable("logical-disk-1;1;500;RAID-5", MetricsHubConstants.TABLE_SEP))
				.build()
		)
			.when(protocolExtensionMock)
			.processSource(eq(logicalDiskSource), anyString(), any(TelemetryManager.class));
		// Call DiscoveryStrategy to discover the monitors
		discoveryStrategy.run();
		// Check discovered monitors
		Map<String, Map<String, Monitor>> discoveredMonitors = telemetryManager.getMonitors();
		assertEquals(4, discoveredMonitors.size());
		assertEquals(1, discoveredMonitors.get(HOST.getKey()).size());
		assertEquals(1, discoveredMonitors.get(CONNECTOR.getKey()).size());
		assertNull(discoveredMonitors.get(DISK_CONTROLLER.getKey()));
		assertEquals(1, discoveredMonitors.get(PHYSICAL_DISK.getKey()).size());
		assertEquals(1, discoveredMonitors.get(LOGICAL_DISK.getKey()).size());
		// Check job duration metrics values
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\", monitor.type=\"logical_disk\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
		assertNotNull(
			telemetryManager
				.getMonitors()
				.get("host")
				.get("anyMonitorId")
				.getMetric(
					"metricshub.job.duration{job.type=\"discovery\", monitor.type=\"physical_disk\", connector_id=\"AAC\"}"
				)
				.getValue()
		);
	}

	@Test
	void testHasAllIdentifyingAttributes() {
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
		discoveryStrategy =
			DiscoveryStrategy
				.builder()
				.clientsExecutor(clientsExecutorMock)
				.strategyTime(strategyTime)
				.telemetryManager(new TelemetryManager())
				.extensionManager(extensionManager)
				.build();
		assertTrue(discoveryStrategy.hasAllIdentifyingAttributes(Set.of("id1", "id2"), Map.of("id1", "1", "id2", "2")));
		assertFalse(discoveryStrategy.hasAllIdentifyingAttributes(Set.of("id1", "id2"), Map.of("id1", "1")));
	}
}
