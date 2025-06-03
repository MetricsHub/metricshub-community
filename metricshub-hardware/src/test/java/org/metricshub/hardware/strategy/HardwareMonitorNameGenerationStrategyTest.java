package org.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.constants.CommonConstants.DISPLAY_ID;
import static org.metricshub.hardware.constants.CommonConstants.ID_COUNT;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.MonitorNameBuilder;

/**
 * Test class for {@link HardwareMonitorNameGenerationStrategy}.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>Monitors are grouped by connector ID and type, then assigned sequential ID_COUNT values
 *       according to the lexicographically sorted MD5 of their attributes.</li>
 *   <li>Monitors with a blank or missing name receive a generated name via {@link MonitorNameBuilder}.</li>
 *   <li>Monitors that already have a non-blank name are not overwritten.</li>
 * </ul>
 */
class HardwareMonitorNameGenerationStrategyTest {

	private static final String HOST_NAME = "test-host";
	private static final String CONNECTOR_ID = "connector-1";

	private TelemetryManager telemetryManager;
	private ClientsExecutor clientsExecutor;
	private ExtensionManager extensionManager;

	@BeforeEach
	void setUp() {
		// 1. Create a ConnectorStore with one connector that has the "hardware" tag:
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity
				.builder()
				.detection(Detection.builder().tags(Set.of("hardware")).appliesTo(Set.of(DeviceKind.WINDOWS)).build())
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR_ID, connector));

		// 2. Build TelemetryManager with the connector store and a HostConfiguration:
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
				.connectorStore(connectorStore)
				.build();

		// 3. Mark the connector status as OK so that isConnectorStatusOk(...) returns true:
		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR_ID, telemetryManager);

		clientsExecutor = new ClientsExecutor(telemetryManager);
		extensionManager = ExtensionManager.builder().build();
	}

	@Test
	void testIdCountAssignmentAndNameGenerationForBlankNames() {
		final long discoveryTime = System.currentTimeMillis();

		// Create two monitors of the same type and connector, both with blank names,
		// but different MONITOR_ATTRIBUTE_ID so that their MD5 hashes are distinct.
		final Map<String, String> firstMonitorAttributes = new HashMap<>();
		firstMonitorAttributes.put(MONITOR_ATTRIBUTE_ID, "a"); // ID 'a'
		firstMonitorAttributes.put(MONITOR_ATTRIBUTE_NAME, ""); // blank name

		final Map<String, String> secondMonitorAttributes = new HashMap<>();
		secondMonitorAttributes.put(MONITOR_ATTRIBUTE_ID, "b"); // ID 'b'
		secondMonitorAttributes.put(MONITOR_ATTRIBUTE_NAME, ""); // blank name

		final MonitorFactory factory1 = MonitorFactory
			.builder()
			.attributes(new HashMap<>(firstMonitorAttributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.ENCLOSURE.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor monitor1 = factory1.createOrUpdateMonitor();

		final MonitorFactory factory2 = MonitorFactory
			.builder()
			.attributes(new HashMap<>(secondMonitorAttributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.ENCLOSURE.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor monitor2 = factory2.createOrUpdateMonitor();

		// Both monitors start with blank MONITOR_ATTRIBUTE_NAME:
		assertTrue(monitor1.getAttribute(MONITOR_ATTRIBUTE_NAME).isBlank());
		assertTrue(monitor2.getAttribute(MONITOR_ATTRIBUTE_NAME).isBlank());

		// Run the naming strategy:
		new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// After running, each monitor should have an ID_COUNT attribute:
		final String idCount1 = monitor1.getAttribute(ID_COUNT);
		final String idCount2 = monitor2.getAttribute(ID_COUNT);
		assertNotNull(idCount1, "Monitor1 should have an ID_COUNT attribute");
		assertNotNull(idCount2, "Monitor2 should have an ID_COUNT attribute");

		// Compute expected ordering by MD5 of "id=<value>;name=;"
		// We know MD5("id=a;name=;") = "76df5b970f97d60766196c4c34cf1117"
		// and MD5("id=b;name=;") = "50fd33a232f6a90691880b6a6e8ee448"
		// "50fd..." < "76df...", so the "b" monitor should receive ID_COUNT = "1",
		// and the "a" monitor should receive ID_COUNT = "2".
		assertEquals("2", idCount1, "Monitor with MONITOR_ATTRIBUTE_ID='a' should get sequence '2'");
		assertEquals("1", idCount2, "Monitor with MONITOR_ATTRIBUTE_ID='b' should get sequence '1'");

		// Each monitor had a blank name, so the strategy should generate one via MonitorNameBuilder.
		final String generatedName1 = monitor1.getAttribute(MONITOR_ATTRIBUTE_NAME);
		final String generatedName2 = monitor2.getAttribute(MONITOR_ATTRIBUTE_NAME);
		assertNotNull(generatedName1);
		assertNotNull(generatedName2);
		assertFalse(generatedName1.isBlank(), "Monitor1 should receive a non-blank generated name");
		assertFalse(generatedName2.isBlank(), "Monitor2 should receive a non-blank generated name");

		// The generated names must differ (because ID_COUNT differs and hashing order differs):
		assertNotEquals(generatedName1, generatedName2, "Generated names should be distinct when ID_COUNT differs");
	}

	@Test
	void testExistingNameIsNotOverwritten() {
		final long discoveryTime = System.currentTimeMillis();

		// Create a single monitor with a pre-set non-blank name:
		final Map<String, String> attributes = new HashMap<>();
		attributes.put(MONITOR_ATTRIBUTE_ID, "fixed-id");
		attributes.put(MONITOR_ATTRIBUTE_NAME, "my-custom-name");

		final MonitorFactory factory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(attributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.ENCLOSURE.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor monitor = factory.createOrUpdateMonitor();

		// Confirm initial name is what we set:
		assertEquals("my-custom-name", monitor.getAttribute(MONITOR_ATTRIBUTE_NAME));

		// Run the naming strategy:
		new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// Since it already had a non-blank name, the MONITOR_ATTRIBUTE_NAME should remain unchanged:
		assertEquals(
			"my-custom-name",
			monitor.getAttribute(MONITOR_ATTRIBUTE_NAME),
			"Existing non-blank name should not be overwritten by the strategy"
		);

		// It should still receive an ID_COUNT attribute based on its only member in the group (i.e., "1"):
		final String idCount = monitor.getAttribute(ID_COUNT);
		assertNotNull(idCount, "Monitor should have an ID_COUNT attribute even if name was preset");
		assertEquals("1", idCount, "With only one monitor in its group, ID_COUNT must be '1'");
	}

	@Test
	void testCpuNameGeneratedInSetMonitorsNames() {
		final long discoveryTime = System.currentTimeMillis();

		// 1. Create a CPU monitor with blank name but with DISPLAY_ID, DEVICE_ID, VENDOR, MODEL:
		final Map<String, String> cpuAttributes = new HashMap<>();
		cpuAttributes.put(MONITOR_ATTRIBUTE_ID, "cpu-1");
		cpuAttributes.put(MONITOR_ATTRIBUTE_NAME, ""); // blank name so builder runs
		cpuAttributes.put(DISPLAY_ID, "Core i7"); // displayId to be used as base
		cpuAttributes.put("device_id", "cpu0"); // fallback if displayId missing
		cpuAttributes.put("vendor", "Intel");
		cpuAttributes.put("model", "Xeon");

		final MonitorFactory cpuFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(cpuAttributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.CPU.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor cpuMonitor = cpuFactory.createOrUpdateMonitor();

		// 2. Run the naming strategy:
		new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// 3. After running, CPU monitor should have ID_COUNT set and a generated name:
		final String idCountCpu = cpuMonitor.getAttribute(ID_COUNT);
		assertNotNull(idCountCpu, "CPU monitor should have an ID_COUNT attribute");

		final String cpuName = cpuMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME);
		assertNotNull(cpuName, "CPU monitor should have a generated name");
		// Since we provided DISPLAY_ID="Core i7", VENDOR="Intel", MODEL="Xeon", expect:
		// "Core i7 (Intel - Xeon)"
		assertEquals("Core i7 (Intel - Xeon)", cpuName);
	}

	@Test
	void testPhysicalDiskNameGeneratedInSetMonitorsNames() {
		final long discoveryTime = System.currentTimeMillis();

		// 1. Create a PhysicalDisk monitor with blank name, type, DISPLAY_ID, VENDOR:
		final Map<String, String> diskAttributes = new HashMap<>();
		diskAttributes.put(MONITOR_ATTRIBUTE_ID, "disk-1");
		diskAttributes.put(MONITOR_ATTRIBUTE_NAME, ""); // blank name
		diskAttributes.put("physical_disk_device_type", "SSD"); // prefix type
		diskAttributes.put(DISPLAY_ID, "Disk1"); // displayId to use
		diskAttributes.put("device_id", "pd0"); // fallback
		diskAttributes.put("vendor", "Seagate");

		final MonitorFactory diskFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(diskAttributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.PHYSICAL_DISK.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor diskMonitor = diskFactory.createOrUpdateMonitor();

		// 2. Run the naming strategy:
		new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// 3. After running, PhysicalDisk monitor should have ID_COUNT set and a generated name:
		final String idCountDisk = diskMonitor.getAttribute(ID_COUNT);
		assertNotNull(idCountDisk, "PhysicalDisk monitor should have an ID_COUNT attribute");

		final String diskName = diskMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME);
		assertNotNull(diskName, "PhysicalDisk monitor should have a generated name");
		assertEquals("Disk1 (Seagate)", diskName);
	}

	@Test
	void testMonitorWithoutHardwareTagIsIgnored() {
		final long discoveryTime = System.currentTimeMillis();

		// 1. Create a ConnectorStore with one connector that does NOT have the "hardware" tag:
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity
				.builder()
				.detection(
					Detection
						.builder()
						// no "hardware" tag here
						.tags(Set.of())
						.appliesTo(Set.of(DeviceKind.WINDOWS))
						.build()
				)
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR_ID, connector));

		// 2. Build TelemetryManager
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
				.connectorStore(connectorStore)
				.build();
		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR_ID, telemetryManager);

		// 3. Create a CPU monitor (type=CPU) under that connector
		final Map<String, String> cpuAttributes = new HashMap<>();
		cpuAttributes.put(MONITOR_ATTRIBUTE_ID, "cpu-ignored");
		cpuAttributes.put(MONITOR_ATTRIBUTE_NAME, ""); // blank name
		cpuAttributes.put("vendor", "Intel");
		cpuAttributes.put("model", "Xeon");

		final MonitorFactory cpuFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(cpuAttributes))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR_ID)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.CPU.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor cpuMonitor = cpuFactory.createOrUpdateMonitor();

		// Sanity check: name is blank initially
		assertEquals("", cpuMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME));

		// 4. Run the naming strategy
		new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// 5. Since connector lacks "hardware" tag, strategy should ignore cpuMonitor:
		//    - No ID_COUNT attribute
		//    - Name remains blank
		assertNull(cpuMonitor.getAttribute(ID_COUNT));
		assertEquals("", cpuMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME));
	}
}
