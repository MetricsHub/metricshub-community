package org.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.common.Constants.ENCLOSURE_PRESENT_METRIC;
import static org.metricshub.hardware.constants.CommonConstants.CONNECTOR;
import static org.metricshub.hardware.constants.CommonConstants.ENCLOSURE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
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

class HardwarePostCollectStrategyTest {

	private static final String HOST_NAME = UUID.randomUUID().toString();

	@Test
	void testRunRefreshPresentMetrics() {
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

		// Get and verify the value of the hw.status metric of the enclosure monitor
		assertEquals(1.0, presentMetric.getValue());

		// Collect the present metric

		// Before running the strategy, the save is performed by the engine,
		// it pushes the collectTime of the previous strategy to previousCollectTime in the metric object.
		// Thus we can identify if the metric has been updated or not.
		presentMetric.save();

		// Compute next discovery time
		final long collectTime = discoveryTime + 2 * 60 * 1000;

		// Now set the connector status as true
		StrategyTestHelper.setConnectorStatusInNamespace(true, CONNECTOR, telemetryManager);

		// Run the strategy again
		new HardwarePostCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager).run();

		// Make sure the presentMetric has been updated
		assertTrue(presentMetric.isUpdated());

		// Get and verify the value of the hw.status metric of the enclosure monitor
		assertEquals(1.0, presentMetric.getValue());

		// Save the current present metric value
		presentMetric.save();

		// Next Collect
		// Compute next collect time
		final long nextCollectTime = collectTime + 2 * 60 * 1000;

		// Now set the connector status as false, it means the connector's detection has failed
		StrategyTestHelper.setConnectorStatusInNamespace(false, CONNECTOR, telemetryManager);

		// Run the strategy again
		new HardwarePostCollectStrategy(telemetryManager, nextCollectTime, clientsExecutor, extensionManager).run();

		// Make sure the presentMetric hasn't been updated
		assertFalse(presentMetric.isUpdated());
	}
}
