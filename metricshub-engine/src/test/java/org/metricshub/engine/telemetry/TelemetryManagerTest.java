package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.DISK_CONTROLLER;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.KnownMonitorType.LOGICAL_DISK;
import static org.metricshub.engine.common.helpers.KnownMonitorType.PHYSICAL_DISK;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
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
import org.metricshub.engine.configuration.OsCommandTestConfiguration;
import org.metricshub.engine.configuration.SshTestConfiguration;
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
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryManagerTest {

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

	@Test
	void testRun() throws Exception {
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
					.build()
			)
			.build();
		hostMonitor.getAttributes().put(IS_ENDPOINT, "true");
		hostMonitor.setIsEndpoint(true);
		connectorMonitor.getAttributes().put("id", AAC_CONNECTOR_ID);
		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		telemetryManager.setConnectorStore(connectorStore);

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
		assertDoesNotThrow(() ->
			telemetryManager.run(
				new DiscoveryStrategy(telemetryManager, System.currentTimeMillis(), clientsExecutorMock, extensionManager)
			)
		);

		// Check discovered monitors
		final Map<String, Map<String, Monitor>> discoveredMonitors = telemetryManager.getMonitors();
		assertEquals(5, discoveredMonitors.size());
		assertEquals(1, discoveredMonitors.get(HOST.getKey()).size());
		assertEquals(1, discoveredMonitors.get(CONNECTOR.getKey()).size());
		assertEquals(1, discoveredMonitors.get(DISK_CONTROLLER.getKey()).size());
		assertEquals(1, discoveredMonitors.get(PHYSICAL_DISK.getKey()).size());
		assertEquals(1, discoveredMonitors.get(LOGICAL_DISK.getKey()).size());

		// Check discovered monitors order
		final Set<String> expectedOrder = Set.of(
			HOST.getKey(),
			DISK_CONTROLLER.getKey(),
			CONNECTOR.getKey(),
			LOGICAL_DISK.getKey(),
			PHYSICAL_DISK.getKey()
		);
		assertEquals(expectedOrder, discoveredMonitors.keySet());
	}

	@Test
	void testGetHostname() {
		// Create two configurations, with a different hostname each.
		OsCommandTestConfiguration osCommandConfiguration = OsCommandTestConfiguration
			.builder()
			.hostname("OsCommandHostname")
			.build();
		SshTestConfiguration sshConfiguration = SshTestConfiguration.builder().hostname("SshHostname").build();
		// Inject the configurations in the hostConfiguration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("TelemetryManagerHostname")
			.configurations(
				Map.of(OsCommandTestConfiguration.class, osCommandConfiguration, SshTestConfiguration.class, sshConfiguration)
			)
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Test that the order is maintained
		assertEquals(
			"SshHostname",
			telemetryManager.getHostname(List.of(SshTestConfiguration.class, OsCommandTestConfiguration.class))
		);
		assertEquals(
			"OsCommandHostname",
			telemetryManager.getHostname(List.of(OsCommandTestConfiguration.class, SshTestConfiguration.class))
		);
		assertEquals("TelemetryManagerHostname", telemetryManager.getHostname(List.of()));
	}
}
