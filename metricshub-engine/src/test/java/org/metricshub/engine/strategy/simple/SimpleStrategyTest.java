package org.metricshub.engine.strategy.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.DISK_CONTROLLER;
import static org.metricshub.engine.common.helpers.KnownMonitorType.ENCLOSURE;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.constants.Constants.STATUS_INFORMATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
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
	private static final Path REMOVAL_YAML_TEST_PATH = Paths.get(
		"src",
		"test",
		"resources",
		"test-files",
		"strategy",
		"removal"
	);
	private static final String REMOVAL_CONNECTOR_ID = "TestConnectorWithRemoval";
	private static final String SOURCE_KEY_FORMAT = "${source::monitors.%s.simple.sources.source(1)}";
	private static final String PROCESS = "process";
	private static final String IDLE_ENTITY = "idle_entity";
	private static final String COUNTER_METRIC = "test.counter";
	private static final String RATE_FROM_METRIC = "__test.rate.rate_from";

	@Mock
	private ClientsExecutor clientsExecutorMock;

	static Long strategyTime = new Date().getTime();

	private SimpleStrategy simpleStrategy;

	/**
	 * The CSV tables returned by the mocked protocol extension, by source key. The other sources return an empty table.
	 */
	private final Map<String, String> sourceTables = new HashMap<>();

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

		// The enclosure source failed (empty after a non-empty run): the enclosure is not removed
		assertSame(
			enclosure,
			telemetryManager.getMonitors().get(ENCLOSURE.getKey()).get("TestConnectorWithSimple_enclosure_enclosure-1")
		);
	}

	@Test
	void testRunRemovesMonitorsNoLongerDiscovered() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder());
		final String process1Id = "TestConnectorWithRemoval_process_process-1";
		final String process2Id = "TestConnectorWithRemoval_process_process-2";

		// First collect: the process job maps process-1 and process-2
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(PROCESS), "process-1;10\nprocess-2;20");
		collect(telemetryManager, strategyTime);

		final Monitor process1 = telemetryManager.getMonitors().get(PROCESS).get(process1Id);
		final NumberMetric process1Counter = process1.getMetric(COUNTER_METRIC, NumberMetric.class);
		assertEquals(Set.of(process1Id, process2Id), telemetryManager.getMonitors().get(PROCESS).keySet());

		// Second collect: process-2 is gone
		final long secondCollectTime = strategyTime + 60 * 1000;
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(PROCESS), "process-1;70");
		collect(telemetryManager, secondCollectTime);

		assertEquals(Set.of(process1Id), telemetryManager.getMonitors().get(PROCESS).keySet());

		// The rediscovered monitor is the same instance: it keeps its metrics, their previous values and its rate state
		assertSame(process1, telemetryManager.getMonitors().get(PROCESS).get(process1Id));
		assertSame(process1Counter, process1.getMetric(COUNTER_METRIC, NumberMetric.class));
		assertEquals(70.0, process1Counter.getValue());
		assertEquals(10.0, process1Counter.getPreviousValue());
		assertEquals(strategyTime, process1Counter.getPreviousCollectTime());
		assertEquals(10.0, process1.getMetric(RATE_FROM_METRIC, NumberMetric.class).getPreviousValue());
		assertEquals(1.0, process1.getMetric("test.rate", NumberMetric.class).getValue());
	}

	@Test
	void testRunKeepsMonitorsNoLongerDiscoveredDuringTheirRemovalDelay() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder());
		final String idle1Id = "TestConnectorWithRemoval_idle_entity_idle-1";
		final String idle2Id = "TestConnectorWithRemoval_idle_entity_idle-2";

		// First collect: the idle_entity job (removalDelay: 1m) maps idle-1 and idle-2
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(IDLE_ENTITY), "idle-1;10\nidle-2;20");
		collect(telemetryManager, strategyTime);
		final Monitor idle2 = telemetryManager.getMonitors().get(IDLE_ENTITY).get(idle2Id);

		// idle-2 is no longer returned, but it was discovered 1 minute ago, not more: it is kept with its metrics and its rate state
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(IDLE_ENTITY), "idle-1;30");
		final long endOfRemovalDelay = strategyTime + 60 * 1000;
		collect(telemetryManager, endOfRemovalDelay);

		assertSame(idle2, telemetryManager.getMonitors().get(IDLE_ENTITY).get(idle2Id));
		assertEquals(20.0, idle2.getMetric(COUNTER_METRIC, NumberMetric.class).getValue());
		assertEquals(20.0, idle2.getMetric(RATE_FROM_METRIC, NumberMetric.class).getValue());

		// The first trusted run after the removal delay removes it
		collect(telemetryManager, endOfRemovalDelay + 1);

		assertEquals(Set.of(idle1Id), telemetryManager.getMonitors().get(IDLE_ENTITY).keySet());
	}

	@Test
	void testRunDoesNotRemoveMonitorsOfUntrustedJobs() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(
			HostConfiguration.builder().excludedMonitors(Set.of("excluded"))
		);

		// Each job type has a monitor discovered by a previous run. The enclosure job runs sequentially, the others in the
		// thread pool
		final List<String> trustedTypes = List.of(
			ENCLOSURE.getKey(),
			PROCESS,
			"flat_file",
			"no_sources",
			"negative_removal_delay"
		);
		final List<String> untrustedTypes = List.of(
			"empty_source",
			"no_identifying_attribute",
			"failed_source",
			"no_attributes",
			"excluded",
			"event_log",
			"log_file",
			"missing_source_table",
			"no_mapping_source",
			"before_all_log",
			"before_all_log_copy"
		);
		final Map<String, Monitor> staleMonitors = Stream.concat(trustedTypes.stream(), untrustedTypes.stream()).collect(
			Collectors.toMap(Function.identity(), type -> addStaleMonitor(telemetryManager, type))
		);

		// Every mapping table has a row, except the one of empty_source
		Stream.concat(trustedTypes.stream(), untrustedTypes.stream())
			.filter(type -> !"empty_source".equals(type))
			.forEach(type -> sourceTables.put(SOURCE_KEY_FORMAT.formatted(type), "new;1"));

		// A mapping without attributes is deserialized as an empty map: null is the consolidation job case
		final SimpleMonitorJob noAttributesJob = (SimpleMonitorJob) telemetryManager
			.getConnectorStore()
			.getStore()
			.get(REMOVAL_CONNECTOR_ID)
			.getMonitors()
			.get("no_attributes");
		noAttributesJob.getSimple().getMapping().setAttributes(null);

		// The incremental file source of beforeAll, read by before_all_log and before_all_log_copy
		sourceTables.put("${source::beforeAll.logLines}", "new;1");

		// failed_source's source(2) answered during the previous run: now empty, it is retried then given up
		telemetryManager
			.getHostProperties()
			.getConnectorNamespace(REMOVAL_CONNECTOR_ID)
			.addSourceTable(
				"${source::monitors.failed_source.simple.sources.source(2)}",
				SourceTable.builder().table(SourceTable.csvToTable("previous;1", MetricsHubConstants.TABLE_SEP)).build()
			);

		collect(telemetryManager, strategyTime);

		trustedTypes.forEach(type -> {
			final Map<String, Monitor> monitors = telemetryManager.getMonitors().get(type);
			assertNull(monitors.get(staleMonitors.get(type).getId()), type);
			// The monitor refreshed by the run is kept
			assertNotNull(monitors.get("%s_%s_new".formatted(REMOVAL_CONNECTOR_ID, type)), type);
		});
		untrustedTypes.forEach(type ->
			assertSame(
				staleMonitors.get(type),
				telemetryManager.getMonitors().get(type).get(staleMonitors.get(type).getId()),
				type
			)
		);
	}

	@Test
	void testRunDoesNotRemoveMonitorsWhenConnectorStatusFailed() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder());

		// The detection criterion only keeps Linux: the re-validation fails once and the connector status is FAILED
		telemetryManager.getHostConfiguration().setHostType(DeviceKind.WINDOWS);

		assertStaleEnclosureKept(telemetryManager);
	}

	@Test
	void testRunDoesNotRemoveMonitorsWhenInterrupted() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder());

		// The strategy thread is interrupted (abandoned run): waiting for the jobs of the thread pool throws
		Thread.currentThread().interrupt();
		try {
			assertStaleEnclosureKept(telemetryManager);
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testRunDoesNotRemoveMonitorsWhenInterruptedAfterMapping() {
		// Sequential: no thread pool, only the interrupt check stops the removal
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder().sequential(true));

		// The source of the process job, run after the enclosure job, interrupts the strategy thread
		lenient()
			.doAnswer(invocation -> {
				Thread.currentThread().interrupt();
				return SourceTable.empty();
			})
			.when(protocolExtensionMock)
			.processSource(
				argThat(source -> source != null && SOURCE_KEY_FORMAT.formatted(PROCESS).equals(source.getKey())),
				anyString(),
				any(TelemetryManager.class)
			);

		try {
			assertStaleEnclosureKept(telemetryManager);
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testRunDoesNotRemoveMonitorsAfterStrategyTimeout() {
		assertStaleEnclosureKept(newRemovalTelemetryManager(HostConfiguration.builder().strategyTimeout(0)));
	}

	@Test
	void testRunDoesNotRemoveTheConnectorMonitor() {
		final TelemetryManager telemetryManager = newRemovalTelemetryManager(HostConfiguration.builder());
		final String connectorMonitorId = String.format(
			AbstractStrategy.CONNECTOR_ID_FORMAT,
			CONNECTOR.getKey(),
			REMOVAL_CONNECTOR_ID
		);

		// The connector monitor as created by the detection of a previous cycle
		final Monitor connectorMonitor = telemetryManager.getMonitors().get(CONNECTOR.getKey()).get(connectorMonitorId);
		connectorMonitor.addAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID, REMOVAL_CONNECTOR_ID);
		connectorMonitor.setDiscoveryTime(strategyTime - 60 * 1000);

		// A job declared under the connector type maps a monitor of that type
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(CONNECTOR.getKey()), "new;1");
		collect(telemetryManager, strategyTime);

		assertEquals(
			Set.of(connectorMonitorId, "TestConnectorWithRemoval_connector_new"),
			telemetryManager.getMonitors().get(CONNECTOR.getKey()).keySet()
		);
	}

	/**
	 * Run a collect in which the enclosure job, run sequentially before the other jobs, reads a new enclosure, then
	 * check that the new enclosure is mapped and that the enclosure discovered by a previous run is kept.
	 *
	 * @param telemetryManager The telemetry manager holding the TestConnectorWithRemoval connector
	 */
	private void assertStaleEnclosureKept(final TelemetryManager telemetryManager) {
		final Monitor staleEnclosure = addStaleMonitor(telemetryManager, ENCLOSURE.getKey());
		sourceTables.put(SOURCE_KEY_FORMAT.formatted(ENCLOSURE.getKey()), "new;1");

		collect(telemetryManager, strategyTime);

		final Map<String, Monitor> enclosures = telemetryManager.getMonitors().get(ENCLOSURE.getKey());
		assertSame(staleEnclosure, enclosures.get(staleEnclosure.getId()));
		assertTrue(enclosures.containsKey("TestConnectorWithRemoval_enclosure_new"));
	}

	/**
	 * Create a telemetry manager holding the endpoint host and the TestConnectorWithRemoval connector monitor, and
	 * make the mocked protocol extension return the {@link #sourceTables}.
	 *
	 * @param hostConfigurationBuilder The host configuration to complete
	 * @return a new {@link TelemetryManager}
	 */
	private TelemetryManager newRemovalTelemetryManager(
		final HostConfiguration.HostConfigurationBuilder hostConfigurationBuilder
	) {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		connectorMonitor.getAttributes().put(MONITOR_ATTRIBUTE_ID, REMOVAL_CONNECTOR_ID);

		final TestConfiguration snmpConfig = TestConfiguration.builder().build();

		// Lenient: the jobs of an interrupted strategy may never call the extension
		lenient().doReturn(true).when(protocolExtensionMock).isValidConfiguration(snmpConfig);
		lenient()
			.doReturn(Set.of(SnmpTableSource.class, EventLogSource.class, FileSource.class))
			.when(protocolExtensionMock)
			.getSupportedSources();
		lenient()
			.doAnswer(invocation ->
				SourceTable.builder()
					.table(
						SourceTable.csvToTable(
							sourceTables.get(invocation.<Source>getArgument(0).getKey()),
							MetricsHubConstants.TABLE_SEP
						)
					)
					.build()
			)
			.when(protocolExtensionMock)
			.processSource(any(Source.class), anyString(), any(TelemetryManager.class));

		return TelemetryManager.builder()
			.monitors(
				new HashMap<>(
					Map.of(
						HOST.getKey(),
						Map.of("monitor1", hostMonitor),
						CONNECTOR.getKey(),
						new HashMap<>(
							Map.of(
								String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), REMOVAL_CONNECTOR_ID),
								connectorMonitor
							)
						)
					)
				)
			)
			.hostConfiguration(
				hostConfigurationBuilder
					.hostId("host-01")
					.hostname("ec-02")
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.connectorStore(new ConnectorStore(REMOVAL_YAML_TEST_PATH))
			.build();
	}

	/**
	 * Add a monitor of the given type, discovered by TestConnectorWithRemoval one minute before {@link #strategyTime}.
	 *
	 * @param telemetryManager The telemetry manager
	 * @param monitorType      The monitor type
	 * @return the new {@link Monitor}
	 */
	private static Monitor addStaleMonitor(final TelemetryManager telemetryManager, final String monitorType) {
		return MonitorFactory.builder()
			.telemetryManager(telemetryManager)
			.monitorType(monitorType)
			.connectorId(REMOVAL_CONNECTOR_ID)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "stale")))
			.keys(DEFAULT_KEYS)
			.discoveryTime(strategyTime - 60 * 1000)
			.build()
			.createOrUpdateMonitor();
	}

	/**
	 * Run a collect cycle: {@link PrepareCollectStrategy} then {@link SimpleStrategy}.
	 *
	 * @param telemetryManager The telemetry manager
	 * @param collectTime      The collect time
	 */
	private void collect(final TelemetryManager telemetryManager, final long collectTime) {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();
		new PrepareCollectStrategy(telemetryManager, collectTime, clientsExecutorMock, extensionManager).run();
		new SimpleStrategy(telemetryManager, collectTime, clientsExecutorMock, extensionManager).run();
	}
}
