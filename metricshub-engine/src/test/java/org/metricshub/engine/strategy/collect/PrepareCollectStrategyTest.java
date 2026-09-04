package org.metricshub.engine.strategy.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.ENCLOSURE;
import static org.metricshub.engine.constants.Constants.HOST_ID;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class PrepareCollectStrategyTest {

	private static final String HW_METRIC = "hw.metric";
	private static final long COLLECT_TIME = System.currentTimeMillis();
	private static final long PREVIOUS_COLLECT_TIME = COLLECT_TIME - 60 * 1000;

	@Test
	void testRunRefreshDiscoveredMetrics() {
		final NumberMetric metric = prepareCollect(true);

		// The discovered metric is not collected by the collect job: its collect time is reset so that it is still exported
		assertEquals(COLLECT_TIME, metric.getCollectTime());
		assertEquals(PREVIOUS_COLLECT_TIME, metric.getPreviousCollectTime());
		assertTrue(metric.isUpdated());
	}

	@Test
	void testRunKeepsCollectTimeOfCollectedMetrics() {
		final NumberMetric metric = prepareCollect(false);

		// The metric is collected by the collect job: its collect time is left untouched until it is actually collected
		assertEquals(PREVIOUS_COLLECT_TIME, metric.getCollectTime());
		assertEquals(PREVIOUS_COLLECT_TIME, metric.getPreviousCollectTime());
		assertFalse(metric.isUpdated());
	}

	/**
	 * Collect a metric at {@link #PREVIOUS_COLLECT_TIME} with the given discovery flag, then run the
	 * {@link PrepareCollectStrategy} at {@link #COLLECT_TIME}.
	 *
	 * @param isDiscovery Whether the metric is collected by a discovery job
	 * @return The prepared metric
	 */
	private static NumberMetric prepareCollect(final boolean isDiscovery) {
		final TestConfiguration snmpConfig = TestConfiguration.builder().build();
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(Map.of());

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(MetricsHubConstants.HOST_NAME)
					.sequential(false)
					.configurations(Map.of(TestConfiguration.class, snmpConfig))
					.build()
			)
			.connectorStore(connectorStore)
			.build();

		// The extension manager is not involved, so let's keep it empty
		final ExtensionManager extensionManager = ExtensionManager.empty();

		final ClientsExecutor clientExecutor = new ClientsExecutor(telemetryManager);
		final MonitorFactory monitorFactory = MonitorFactory.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(COLLECT_TIME - 30 * 60 * 1000)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.keys(DEFAULT_KEYS)
			.build();

		final Monitor monitor = monitorFactory.createOrUpdateMonitor();

		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);

		metricFactory.collectMonitorMetrics(
			ENCLOSURE,
			new Connector(),
			monitor,
			MetricsHubConstants.HOST_NAME,
			Map.of(HW_METRIC, "1"),
			PREVIOUS_COLLECT_TIME,
			isDiscovery
		);

		new PrepareCollectStrategy(telemetryManager, COLLECT_TIME, clientExecutor, extensionManager).run();

		return monitor.getMetric(HW_METRIC, NumberMetric.class);
	}
}
