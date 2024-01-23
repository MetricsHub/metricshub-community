package org.sentrysoftware.metricshub.engine.strategy.collect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.sentrysoftware.metricshub.engine.constants.Constants.CONNECTOR;
import static org.sentrysoftware.metricshub.engine.constants.Constants.ENCLOSURE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.HOST_ID;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.MonitorFactory;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class PrepareCollectStrategyTest {

	private static final String HW_METRIC = "hw.metric";

	@Test
	void testRunRefreshDiscoveredMetrics() {
		final SnmpConfiguration snmpConfig = SnmpConfiguration.builder().community("public").build();
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(Map.of());

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOST_ID)
					.hostname(MetricsHubConstants.HOST_NAME)
					.sequential(false)
					.configurations(Map.of(SnmpConfiguration.class, snmpConfig))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
		final ClientsExecutor clientExecutor = new ClientsExecutor(telemetryManager);
		final long collectTime = System.currentTimeMillis();
		final MonitorFactory monitorFactory = MonitorFactory
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, "id", MONITOR_ATTRIBUTE_NAME, "name")))
			.discoveryTime(collectTime - 30 * 60 * 1000)
			.connectorId(CONNECTOR)
			.telemetryManager(telemetryManager)
			.monitorType(ENCLOSURE)
			.build();

		final Monitor monitor = monitorFactory.createOrUpdateMonitor();

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		metricFactory.collectMonitorMetrics(
			ENCLOSURE,
			new Connector(),
			monitor,
			MetricsHubConstants.HOST_NAME,
			Map.of(HW_METRIC, "1"),
			collectTime,
			true
		);

		new PrepareCollectStrategy(telemetryManager, collectTime, clientExecutor).run();

		final NumberMetric metric = monitor.getMetric(HW_METRIC, NumberMetric.class);

		assertEquals(metric.getCollectTime(), collectTime);
	}
}
