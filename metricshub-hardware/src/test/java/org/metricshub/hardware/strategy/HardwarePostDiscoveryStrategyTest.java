package org.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.common.Constants.ENCLOSURE_PRESENT_METRIC;
import static org.metricshub.hardware.util.HwConstants.CONNECTOR;
import static org.metricshub.hardware.util.HwConstants.ENCLOSURE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.metricshub.engine.telemetry.metric.NumberMetric;

class HardwarePostDiscoveryStrategyTest {

	private static final String HOST_NAME = UUID.randomUUID().toString();

	@Test
	void testRunMissingAndPresentDeviceDetectionWithHardwareTag() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity
				.builder()
				.detection(Detection.builder().tags(Set.of("hardware")).appliesTo(Set.of(DeviceKind.WINDOWS)).build())
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR, connector));

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();

		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR, telemetryManager);

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();
		final long previousDiscoveryTime = discoveryTime - 30 * 60 * 1000;

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory
			.builder()
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
		final MonitorFactory hostMonitorFactory = MonitorFactory
			.builder()
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
		final MonitorFactory connectorMonitorFactory = MonitorFactory
			.builder()
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

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.keys(DEFAULT_KEYS)
			.build();

		final Monitor enclosureMonitor = enclosureMonitorFactory.createOrUpdateMonitor();

		// Create a connector Monitor
		final MonitorFactory connectorMonitorFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "connector-1", MONITOR_ATTRIBUTE_NAME, "conn-1")))
			.discoveryTime(discoveryTime)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(CONNECTOR)
			.keys(DEFAULT_KEYS)
			.build();
		final Monitor connectorMonitor = connectorMonitorFactory.createOrUpdateMonitor();

		// Create a host Monitor
		final MonitorFactory hostMonitorFactory = MonitorFactory
			.builder()
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
			ConnectorIdentity
				.builder()
				.detection(Detection.builder().tags(Set.of("hardware")).appliesTo(Set.of(DeviceKind.WINDOWS)).build())
				.build()
		);
		connectorStore.setStore(Map.of(CONNECTOR, connector));

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostId(HOST_NAME).hostname(HOST_NAME).sequential(false).build())
			.connectorStore(connectorStore)
			.build();

		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR, telemetryManager);

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final long discoveryTime = System.currentTimeMillis();

		// Create an enclosure monitor
		final MonitorFactory enclosureMonitorFactory = MonitorFactory
			.builder()
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
}
