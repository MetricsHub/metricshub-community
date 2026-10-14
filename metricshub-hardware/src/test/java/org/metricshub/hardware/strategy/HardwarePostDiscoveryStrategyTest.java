package org.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.hardware.common.Constants.ENCLOSURE_PRESENT_METRIC;
import static org.metricshub.hardware.constants.CommonConstants.CONNECTOR;
import static org.metricshub.hardware.constants.CommonConstants.ENCLOSURE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class HardwarePostDiscoveryStrategyTest {

	private static final String HOST_NAME = UUID.randomUUID().toString();
	private static final String TEMPERATURE = KnownMonitorType.TEMPERATURE.getKey();
	private static final String REMOVAL_CONNECTOR_ID = "TestHardwareConnector";
	private static final String REMOVAL_ENCLOSURE_1 = "TestHardwareConnector_enclosure_enclosure-1";
	private static final String REMOVAL_ENCLOSURE_2 = "TestHardwareConnector_enclosure_enclosure-2";
	private static final String REMOVAL_TEMPERATURE_2 = "TestHardwareConnector_temperature_temperature-2";

	@Test
	void testRunMissingAndPresentDeviceDetectionWithHardwareTag() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity.builder()
				.detection(Detection.builder().tags(Set.of("hardware")).appliesTo(Set.of(DeviceKind.WINDOWS)).build())
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR, connector));

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();

		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR, telemetryManager);

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();
		final long previousDiscoveryTime = discoveryTime - 30 * 60 * 1000;

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(previousDiscoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.keys(DEFAULT_KEYS)
			.build();

		final Monitor enclosureMonitor = enclosureMonitorFactory.createOrUpdateMonitor();

		// Create the extension manager
		final ExtensionManager extensionManager = ExtensionManager.builder().build();

		// Create a host Monitor
		final MonitorFactory hostMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, HOST_NAME, MONITOR_ATTRIBUTE_NAME, HOST_NAME)))
			.discoveryTime(previousDiscoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.HOST.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor hostMonitor = hostMonitorFactory.createOrUpdateMonitor();
		hostMonitor.setAsEndpoint();

		// There is no connector monitor having a hardware tag, so the hw.status won't be set on the host monitor
		new HardwarePostDiscoveryStrategy(telemetryManager, previousDiscoveryTime, clientsExecutor, extensionManager).run();

		assertEquals(1.0, enclosureMonitor.getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class).getValue());

		// Check the hw.status metric of the host monitor
		assertNull(hostMonitor.getMetric("hw.status{hw.type=\"host\", state=\"present\"}", NumberMetric.class));

		// Create a connector Monitor
		final MonitorFactory connectorMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "connector-1", MONITOR_ATTRIBUTE_NAME, "conn-1")))
			.discoveryTime(previousDiscoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(CONNECTOR)
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor connectorMonitor = connectorMonitorFactory.createOrUpdateMonitor();

		// There is a connector monitor having a hardware tag, so the hw.status will be set on the host monitor
		new HardwarePostDiscoveryStrategy(telemetryManager, previousDiscoveryTime, clientsExecutor, extensionManager).run();

		// Check the hw.status metric of the enclosure monitor
		assertEquals(1.0, enclosureMonitor.getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class).getValue());

		// Check the hw.status metric is not set for the connector monitor (Excluded from the candidates)
		assertNull(connectorMonitor.getMetric("hw.status{hw.type=\"connector\", state=\"present\"}", NumberMetric.class));

		// Check the hw.status metric is not set for the host monitor (Excluded from the candidates)
		assertNull(hostMonitor.getMetric("hw.status{hw.type=\"host\", state=\"present\"}", NumberMetric.class));

		new HardwarePostDiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// Check the hw.status metric of the enclosure monitor
		assertEquals(0.0, enclosureMonitor.getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class).getValue());

		// Check the hw.status metric is not set for the connector monitor (Excluded from the candidates)
		assertNull(connectorMonitor.getMetric("hw.status{hw.type=\"connector\", state=\"present\"}", NumberMetric.class));

		// Check the hw.status metric is not set for the host monitor (Excluded from the candidates)
		assertNull(hostMonitor.getMetric("hw.status{hw.type=\"host\", state=\"present\"}", NumberMetric.class));
	}

	@Test
	void testRunPresentDeviceDetectionWithoutHardwareTags() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity.builder().detection(Detection.builder().appliesTo(Set.of(DeviceKind.WINDOWS)).build()).build()
		);
		connectorStore.setStore(Map.of(CONNECTOR, connector));

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.keys(DEFAULT_KEYS)
			.build();

		final Monitor enclosureMonitor = enclosureMonitorFactory.createOrUpdateMonitor();

		// Create a connector Monitor
		final MonitorFactory connectorMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "connector-1", MONITOR_ATTRIBUTE_NAME, "conn-1")))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(CONNECTOR)
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor connectorMonitor = connectorMonitorFactory.createOrUpdateMonitor();

		// Create a host Monitor
		final MonitorFactory hostMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, HOST_NAME, MONITOR_ATTRIBUTE_NAME, HOST_NAME)))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(KnownMonitorType.HOST.getKey())
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor hostMonitor = hostMonitorFactory.createOrUpdateMonitor();
		hostMonitor.setAsEndpoint();

		final ExtensionManager extensionManager = ExtensionManager.builder().build();

		new HardwarePostDiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// Check the hw.status metric of the enclosure monitor
		assertNull(enclosureMonitor.getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class));

		// Check the hw.status metric of the connector monitor
		assertNull(connectorMonitor.getMetric("hw.status{hw.type=\"connector\", state=\"present\"}", NumberMetric.class));

		// Check the hw.status metric of the host monitor
		assertNull(hostMonitor.getMetric("hw.status{hw.type=\"host\", state=\"present\"}", NumberMetric.class));
	}

	@Test
	void testMissingDeviceDetectionAfterConnectorFailure() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity.builder()
				.detection(Detection.builder().tags(Set.of("hardware")).appliesTo(Set.of(DeviceKind.WINDOWS)).build())
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR, connector));

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();

		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR, telemetryManager);

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.keys(DEFAULT_KEYS)
			.build();

		final Monitor enclosureMonitor = enclosureMonitorFactory.createOrUpdateMonitor();

		// Create the extension manager
		final ExtensionManager extensionManager = ExtensionManager.builder().build();

		// There is no connector monitor having a hardware tag, so the hw.status won't be set on the host monitor
		new HardwarePostDiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager).run();

		// Get the hw.status metric of the enclosure monitor
		final NumberMetric presentMetric = enclosureMonitor.getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class);

		// Make sure the presentMetric has been updated
		assertTrue(presentMetric.isUpdated());

		// Get the value of the hw.status metric of the enclosure monitor
		final Double value = presentMetric.getValue();
		assertEquals(1.0, value);

		// Compute next discovery time
		final long nextDiscoveryTime = discoveryTime + 30 * 60 * 1000;

		// Before running the strategy, the save is performed by the engine,
		// it pushes the collectTime of the previous strategy to previousCollectTime in the metric object.
		// Thus we can identify if the metric has been updated or not.
		presentMetric.save();

		// Now set the connector status as false
		StrategyTestHelper.setConnectorStatusInNamespace(false, CONNECTOR, telemetryManager);

		// The connector has failed at the discovery! it means the discovery time didn't change.
		enclosureMonitor.setDiscoveryTime(discoveryTime);

		// Run the strategy again
		new HardwarePostDiscoveryStrategy(telemetryManager, nextDiscoveryTime, clientsExecutor, extensionManager).run();

		// Make sure the presentMetric hasn't been updated due to the connector failure
		assertFalse(presentMetric.isUpdated());
	}

	@Test
	void testMissingDeviceKeptWhenNoLongerDiscovered() {
		final long firstDiscoveryTime = System.currentTimeMillis();
		final long secondDiscoveryTime = firstDiscoveryTime + 30 * 60 * 1000;
		final TelemetryManager telemetryManager = discoverTwice(true, firstDiscoveryTime, secondDiscoveryTime);

		// The missing enclosure is kept, the missing temperature is removed
		final Map<String, Monitor> enclosures = telemetryManager.getMonitors().get(ENCLOSURE);
		assertEquals(Set.of(REMOVAL_ENCLOSURE_1, REMOVAL_ENCLOSURE_2), enclosures.keySet());
		assertEquals(Set.of(REMOVAL_TEMPERATURE_2), telemetryManager.getMonitors().get(TEMPERATURE).keySet());

		// The missing enclosure is reported missing
		new HardwarePostDiscoveryStrategy(
			telemetryManager,
			secondDiscoveryTime,
			new ClientsExecutor(telemetryManager),
			ExtensionManager.builder().build()
		).run();

		assertEquals(
			0.0,
			enclosures.get(REMOVAL_ENCLOSURE_1).getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class).getValue()
		);
		assertEquals(
			1.0,
			enclosures.get(REMOVAL_ENCLOSURE_2).getMetric(ENCLOSURE_PRESENT_METRIC, NumberMetric.class).getValue()
		);
	}

	@Test
	void testAllTypesRemovedWhenNoLongerDiscoveredWithoutHardwareTag() {
		final long firstDiscoveryTime = System.currentTimeMillis();
		final TelemetryManager telemetryManager = discoverTwice(
			false,
			firstDiscoveryTime,
			firstDiscoveryTime + 30 * 60 * 1000
		);

		assertEquals(Set.of(REMOVAL_ENCLOSURE_2), telemetryManager.getMonitors().get(ENCLOSURE).keySet());
		assertEquals(Set.of(REMOVAL_TEMPERATURE_2), telemetryManager.getMonitors().get(TEMPERATURE).keySet());
	}

	/**
	 * Run two discoveries of TestHardwareConnector: the first one discovers enclosure-1 and temperature-1, the second
	 * one enclosure-2 and temperature-2.
	 *
	 * @param hardwareTag         Whether the connector keeps its "hardware" tag
	 * @param firstDiscoveryTime  The time of the first discovery
	 * @param secondDiscoveryTime The time of the second discovery
	 * @return the {@link TelemetryManager} after the second discovery
	 */
	private static TelemetryManager discoverTwice(
		final boolean hardwareTag,
		final long firstDiscoveryTime,
		final long secondDiscoveryTime
	) {
		final ConnectorStore connectorStore = new ConnectorStore(
			Path.of("src", "test", "resources", "test-files", "removal")
		);
		if (!hardwareTag) {
			connectorStore.getStore().get(REMOVAL_CONNECTOR_ID).getConnectorIdentity().getDetection().setTags(Set.of());
		}

		final IConfiguration configuration = mock(IConfiguration.class);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_NAME)
					.hostname(HOST_NAME)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(IConfiguration.class, configuration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();

		// The endpoint host and the connector monitor, created by the detection
		final String hostType = KnownMonitorType.HOST.getKey();
		telemetryManager.addNewMonitor(Monitor.builder().type(hostType).isEndpoint(true).build(), hostType, HOST_NAME);
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR).build();
		connectorMonitor.addAttribute(MONITOR_ATTRIBUTE_ID, REMOVAL_CONNECTOR_ID);
		telemetryManager.addNewMonitor(
			connectorMonitor,
			CONNECTOR,
			String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, CONNECTOR, REMOVAL_CONNECTOR_ID)
		);

		final IProtocolExtension protocolExtension = mock(IProtocolExtension.class);
		doReturn(true).when(protocolExtension).isValidConfiguration(configuration);
		doReturn(Set.of(SnmpTableSource.class)).when(protocolExtension).getSupportedSources();
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtension))
			.build();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		final String enclosureSource = "${source::monitors.enclosure.discovery.sources.source(1)}";
		final String temperatureSource = "${source::monitors.temperature.discovery.sources.source(1)}";
		final Map<String, String> sourceTables = new HashMap<>(
			Map.of(enclosureSource, "enclosure-1", temperatureSource, "temperature-1")
		);
		doAnswer(invocation ->
			SourceTable.builder()
				.table(SourceTable.csvToTable(sourceTables.get(invocation.<Source>getArgument(0).getKey()), TABLE_SEP))
				.build()
		)
			.when(protocolExtension)
			.processSource(any(Source.class), anyString(), any(TelemetryManager.class));

		new DiscoveryStrategy(telemetryManager, firstDiscoveryTime, clientsExecutor, extensionManager).run();

		// Both jobs map a monitor in the second discovery: their passes are trusted
		sourceTables.putAll(Map.of(enclosureSource, "enclosure-2", temperatureSource, "temperature-2"));
		new DiscoveryStrategy(telemetryManager, secondDiscoveryTime, clientsExecutor, extensionManager).run();

		return telemetryManager;
	}
}
