package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.DETECTION_FOLDER;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.STRATEGY_TIME;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.detection.ConnectorStagingManager.StagedConnectorIdentifiers;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

class AutomaticDetectionTest {

	private static final String COLUMN_1_REF = "$1";
	private static final String CONNECTOR_LAST_RESORT_ENCLOSURE_ID = "connectorLastResortEnclosure";
	private static final String CONNECTOR_LAST_RESORT_DISK_CONTROLLER_1_ID = "connectorLastResortDiskController1";
	private static final String CONNECTOR_LAST_RESORT_DISK_CONTROLLER_2_ID = "connectorLastResortDiskController2";
	private static final String CONNECTOR_REGULAR_ENCLOSURE_ID = "connectorRegularEnclosure";
	private static final String CONNECTOR_LAST_RESORT_DISK_CONTROLLER_ID = "connectorLastResortDiskController";
	private static final String CONNECTOR_LAST_RESORT_GPU_ID = "connectorLastResortGpu";

	@Test
	void testRunNull() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Set<String> emptySet = Collections.emptySet();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();
		assertThrows(
			IllegalArgumentException.class,
			() -> new AutomaticDetection(null, clientsExecutor, emptySet, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new AutomaticDetection(telemetryManager, null, emptySet, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new AutomaticDetection(telemetryManager, clientsExecutor, null, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new AutomaticDetection(telemetryManager, clientsExecutor, emptySet, null)
		);
	}

	@Test
	void testRunEmptyTelemetryManager() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		assertEquals(
			Collections.emptyList(),
			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty()).run()
		);
	}

	@Test
	void testRunExcludeAllConnectorsFiltering() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final Set<String> connectors = new HashSet<>();
		connectors.add("-" + CONNECTOR);
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId("hostId")
			.hostType(DeviceKind.WINDOWS)
			.resolveHostnameToFqdn(false)
			.connectors(connectors)
			.configurations(configurations)
			.retryDelay(0)
			.build();
		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();
		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, new Connector());
		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			false,
			"",
			"",
			false
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final StagedConnectorIdentifiers stagedConnectorIdentifiers = new ConnectorStagingManager(LOCALHOST)
			.stage(connectorStore, connectors);
		assertEquals(
			new ArrayList<>(),
			new AutomaticDetection(
				telemetryManager,
				clientsExecutor,
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds(),
				ExtensionManager.empty()
			)
				.run()
		);
	}

	@Test
	void testRunAutoDetectionFiltering() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final Set<String> connectors = new HashSet<>();
		connectors.add(CONNECTOR);
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(TestConfiguration.class, new TestConfiguration());
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId("hostId")
			.hostType(DeviceKind.WINDOWS)
			.resolveHostnameToFqdn(false)
			.connectors(connectors)
			.configurations(configurations)
			.retryDelay(0)
			.build();

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final Detection detection = new Detection();
		// Disable Auto detection
		detection.setDisableAutoDetection(true);
		detection.setAppliesTo(Set.of(DeviceKind.WINDOWS));
		detection.setConnectionTypes(Set.of(ConnectionType.LOCAL));

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(detection);

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setSourceTypes(Set.of(SnmpSource.class));

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			false,
			"",
			"",
			false
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final StagedConnectorIdentifiers stagedConnectorIdentifiers = new ConnectorStagingManager(LOCALHOST)
			.stage(connectorStore, connectors);
		assertEquals(
			new ArrayList<>(),
			new AutomaticDetection(
				telemetryManager,
				clientsExecutor,
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds(),
				ExtensionManager.empty()
			)
				.run()
		);
	}

	@Test
	void testDeviceKindFiltering() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final Set<String> connectors = new HashSet<>();
		connectors.add(CONNECTOR);
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(TestConfiguration.class, new TestConfiguration());
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId("hostId")
			.hostType(DeviceKind.WINDOWS)
			.resolveHostnameToFqdn(false)
			.connectors(connectors)
			.configurations(configurations)
			.retryDelay(0)
			.build();

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final Detection detection = new Detection();
		detection.setDisableAutoDetection(false);
		// appliesTo Linux when host is Windows
		detection.setAppliesTo(Set.of(DeviceKind.LINUX));
		detection.setConnectionTypes(Set.of(ConnectionType.LOCAL));

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(detection);

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setSourceTypes(Set.of(SnmpSource.class));

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			false,
			"",
			"",
			false
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final StagedConnectorIdentifiers stagedConnectorIdentifiers = new ConnectorStagingManager(LOCALHOST)
			.stage(connectorStore, connectors);
		assertEquals(
			new ArrayList<>(),
			new AutomaticDetection(
				telemetryManager,
				clientsExecutor,
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds(),
				ExtensionManager.empty()
			)
				.run()
		);
	}

	@Test
	void testConnectionTypesFiltering() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final Set<String> connectors = new HashSet<>();
		connectors.add(CONNECTOR);
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(TestConfiguration.class, new TestConfiguration());
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId("hostId")
			.hostType(DeviceKind.WINDOWS)
			.resolveHostnameToFqdn(false)
			.connectors(connectors)
			.configurations(configurations)
			.retryDelay(0)
			.build();

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final Detection detection = new Detection();
		detection.setDisableAutoDetection(false);
		detection.setAppliesTo(Set.of(DeviceKind.WINDOWS));
		// Connection type Remote
		detection.setConnectionTypes(Set.of(ConnectionType.REMOTE));

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(detection);

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		connector.setSourceTypes(Set.of(SnmpSource.class));

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			false,
			"",
			"",
			false
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final StagedConnectorIdentifiers stagedConnectorIdentifiers = new ConnectorStagingManager(LOCALHOST)
			.stage(connectorStore, connectors);
		assertEquals(
			new ArrayList<>(),
			new AutomaticDetection(
				telemetryManager,
				clientsExecutor,
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds(),
				ExtensionManager.empty()
			)
				.run()
		);
	}

	@Test
	void testAcceptedSourcesFiltering() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final Set<String> connectors = new HashSet<>();
		connectors.add(CONNECTOR);
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(TestConfiguration.class, new TestConfiguration());
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId("hostId")
			.hostType(DeviceKind.WINDOWS)
			.resolveHostnameToFqdn(false)
			.connectors(connectors)
			.configurations(configurations)
			.retryDelay(0)
			.build();

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final Detection detection = new Detection();
		detection.setDisableAutoDetection(false);
		detection.setAppliesTo(Set.of(DeviceKind.WINDOWS));
		detection.setConnectionTypes(Set.of(ConnectionType.LOCAL));

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(detection);

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);
		// Http Source when host is configured with Snmp
		connector.setSourceTypes(Set.of(HttpSource.class));

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			false,
			"",
			"",
			false
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final StagedConnectorIdentifiers stagedConnectorIdentifiers = new ConnectorStagingManager(LOCALHOST)
			.stage(connectorStore, connectors);
		assertEquals(
			new ArrayList<>(),
			new AutomaticDetection(
				telemetryManager,
				clientsExecutor,
				stagedConnectorIdentifiers.getAutoDetectionConnectorIds(),
				ExtensionManager.empty()
			)
				.run()
		);
	}

	@Test
	void testFilterLastResortConnectors() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		{
			// A single "last resort" connector discovering the same hardware monitor as a
			// regular connector
			final Connector lastResortConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_ENCLOSURE_ID)
						.detection(
							Detection
								.builder()
								.appliesTo(Set.of(DeviceKind.LINUX))
								.onLastResort(KnownMonitorType.ENCLOSURE.getKey())
								.build()
						)
						.build()
				)
				.build();

			final ConnectorTestResult lastResortConnectorTestResult = ConnectorTestResult
				.builder()
				.connector(lastResortConnector)
				.build();

			final List<ConnectorTestResult> connectorTestResultList = new ArrayList<>(List.of(lastResortConnectorTestResult));

			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty())
				.filterLastResortConnectors(connectorTestResultList, LOCALHOST);

			// The last resort connector should be kept
			assertEquals(1, connectorTestResultList.size());
			assertTrue(connectorTestResultList.contains(lastResortConnectorTestResult));

			final Map<String, MonitorJob> enclosureMonitorJobs = Map.of(
				KnownMonitorType.ENCLOSURE.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			// Test with two connectors: the last resort and a regular one with a matching
			// monitor type
			final Connector regularConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_REGULAR_ENCLOSURE_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(enclosureMonitorJobs)
				.build();

			final ConnectorTestResult regularConnectorTestResult = ConnectorTestResult
				.builder()
				.connector(regularConnector)
				.build();
			connectorTestResultList.add(regularConnectorTestResult);

			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty())
				.filterLastResortConnectors(connectorTestResultList, LOCALHOST);

			// We should only have the regular connector left
			assertEquals(1, connectorTestResultList.size());
			assertTrue(connectorTestResultList.contains(regularConnectorTestResult));
		}

		{
			// A single "last resort" connector discovering something else than the regular
			// connector
			// Build a list of two connectors: a regular one and a last resort of Disk
			// Controllers
			final Connector lastResortConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_DISK_CONTROLLER_ID)
						.detection(
							Detection
								.builder()
								.appliesTo(Set.of(DeviceKind.LINUX))
								.onLastResort(KnownMonitorType.DISK_CONTROLLER.getKey())
								.build()
						)
						.build()
				)
				.build();
			final ConnectorTestResult lastResortConnectorTestResult = ConnectorTestResult
				.builder()
				.connector(lastResortConnector)
				.build();

			final Map<String, MonitorJob> monitorJobs = Map.of(
				KnownMonitorType.ENCLOSURE.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			final Connector regularConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_REGULAR_ENCLOSURE_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(monitorJobs)
				.build();

			final ConnectorTestResult regularConnectorTestResult = ConnectorTestResult
				.builder()
				.connector(regularConnector)
				.build();

			final List<ConnectorTestResult> connectorTestResultList = new ArrayList<>(
				List.of(lastResortConnectorTestResult, regularConnectorTestResult)
			);

			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty())
				.filterLastResortConnectors(connectorTestResultList, LOCALHOST);

			// Our two connectors should still be in the list as the regular connector does
			// not discover disk controllers
			assertEquals(2, connectorTestResultList.size());
			assertTrue(connectorTestResultList.contains(regularConnectorTestResult));
			assertTrue(connectorTestResultList.contains(lastResortConnectorTestResult));
		}

		{
			// Two identical "last resort" connectors discovering something else than the
			// regular connector

			// Regular connector with an enclosure job
			final Map<String, MonitorJob> enclosureMonitorJobs = Map.of(
				KnownMonitorType.ENCLOSURE.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			final Connector regularConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_REGULAR_ENCLOSURE_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(enclosureMonitorJobs)
				.build();
			final ConnectorTestResult regularConnectorTestResult = ConnectorTestResult
				.builder()
				.connector(regularConnector)
				.build();

			// Last resort connector 1 with a disk controller job
			final Map<String, MonitorJob> diskControllerMonitorJobs = Map.of(
				KnownMonitorType.DISK_CONTROLLER.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			final Connector lastResortConnector1 = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_DISK_CONTROLLER_1_ID)
						.detection(
							Detection
								.builder()
								.appliesTo(Set.of(DeviceKind.LINUX))
								.onLastResort(KnownMonitorType.DISK_CONTROLLER.getKey())
								.build()
						)
						.build()
				)
				.monitors(diskControllerMonitorJobs)
				.build();
			final ConnectorTestResult lastResortConnectorTestResult1 = ConnectorTestResult
				.builder()
				.connector(lastResortConnector1)
				.build();

			// Last resort connector 2 with Disk controller monitor type, no discovery job
			// (no need)
			final Connector lastResortConnector2 = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_DISK_CONTROLLER_2_ID)
						.detection(
							Detection
								.builder()
								.appliesTo(Set.of(DeviceKind.LINUX))
								.onLastResort(KnownMonitorType.DISK_CONTROLLER.getKey())
								.build()
						)
						.build()
				)
				.build();
			final ConnectorTestResult lastResortConnectorTestResult2 = ConnectorTestResult
				.builder()
				.connector(lastResortConnector2)
				.build();

			// Build the list
			final List<ConnectorTestResult> testedConnectors = new ArrayList<>(
				List.of(lastResortConnectorTestResult1, lastResortConnectorTestResult2, regularConnectorTestResult)
			);

			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty())
				.filterLastResortConnectors(testedConnectors, LOCALHOST);

			// The regular connector and the first last resort connector should be in the
			// list. The second last resort connector should
			// have been removed because we already have a connector that discovers the same
			// monitor type (the first last resort connector)
			assertEquals(2, testedConnectors.size());
			assertTrue(testedConnectors.contains(regularConnectorTestResult));
			assertTrue(testedConnectors.contains(lastResortConnectorTestResult1));
		}

		{
			// Two different "last resort" connectors discovering something else than the
			// regular connector

			// Regular connector with an enclosure job
			final Map<String, MonitorJob> enclosureMonitorJobs = Map.of(
				KnownMonitorType.ENCLOSURE.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			final Connector regularConnector = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_REGULAR_ENCLOSURE_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(enclosureMonitorJobs)
				.build();
			final ConnectorTestResult regularConnectorTestResultEnclosure = ConnectorTestResult
				.builder()
				.connector(regularConnector)
				.build();

			// Last resort connector 1 with a disk controller monitor job
			final Map<String, MonitorJob> diskControllerMonitorJobs = Map.of(
				KnownMonitorType.DISK_CONTROLLER.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);
			final Connector lastResortConnectorDiskController = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_DISK_CONTROLLER_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(diskControllerMonitorJobs)
				.build();
			final ConnectorTestResult lastResortConnectorTestResultDiskController = ConnectorTestResult
				.builder()
				.connector(lastResortConnectorDiskController)
				.build();

			// Last resort connector 2 with a GPU monitor job
			final Map<String, MonitorJob> gpuMonitorJobs = Map.of(
				KnownMonitorType.GPU.getKey(),
				StandardMonitorJob
					.standardBuilder()
					.discovery(
						Discovery
							.builder()
							.mapping(Mapping.builder().attributes(Map.of(MONITOR_ATTRIBUTE_ID, COLUMN_1_REF)).build())
							.build()
					)
					.keys(DEFAULT_KEYS)
					.build()
			);

			final Connector lastResortConnectorGpu = Connector
				.builder()
				.connectorIdentity(
					ConnectorIdentity
						.builder()
						.compiledFilename(CONNECTOR_LAST_RESORT_GPU_ID)
						.detection(Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).build())
						.build()
				)
				.monitors(gpuMonitorJobs)
				.build();
			final ConnectorTestResult lastResortConnectorTestResultGpu = ConnectorTestResult
				.builder()
				.connector(lastResortConnectorGpu)
				.build();

			// Build the list
			final List<ConnectorTestResult> testedConnectors = new ArrayList<>(
				List.of(
					lastResortConnectorTestResultDiskController,
					lastResortConnectorTestResultGpu,
					regularConnectorTestResultEnclosure
				)
			);

			new AutomaticDetection(telemetryManager, clientsExecutor, Collections.emptySet(), ExtensionManager.empty())
				.filterLastResortConnectors(testedConnectors, LOCALHOST);

			// All connectors should be kept
			assertEquals(3, testedConnectors.size());
			assertTrue(testedConnectors.contains(regularConnectorTestResultEnclosure));
			assertTrue(testedConnectors.contains(lastResortConnectorTestResultDiskController));
			assertTrue(testedConnectors.contains(lastResortConnectorTestResultGpu));
		}
	}
}
