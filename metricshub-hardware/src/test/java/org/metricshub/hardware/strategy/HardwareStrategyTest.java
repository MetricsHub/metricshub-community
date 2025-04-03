package org.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.hardware.common.Constants.FAN_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_SPEED_METRIC;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.sentrysoftware.metricshub.engine.connector.model.identity.Detection;
import org.sentrysoftware.metricshub.engine.strategy.IStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.ConnectorNamespace;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class HardwareStrategyTest {

	private static final String MONITOR_ID = "monitor_id";
	private static final Long STRATEGY_TIME = 1696597422644L;
	private static final Long NEXT_STRATEGY_TIME = STRATEGY_TIME + 2 * 60 * 1000;
	private static final String FAN = KnownMonitorType.FAN.getKey();
	private static final String HOST = KnownMonitorType.HOST.getKey();
	private static final String CONNECTOR = KnownMonitorType.CONNECTOR.getKey();
	private static final String TEST_CONNECTOR = "TestConnector";

	private TelemetryManager telemetryManager;

	@BeforeEach
	void init() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity
				.builder()
				.detection(Detection.builder().appliesTo(Set.of(DeviceKind.OOB)).tags(Set.of("hardware")).build())
				.build()
		);
		connectorStore.setStore(new HashMap<>(Map.of(TEST_CONNECTOR, connector)));
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
				.strategyTime(STRATEGY_TIME)
				.connectorStore(connectorStore)
				.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(TEST_CONNECTOR, connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);
	}

	@Test
	void testRun() {
		// Create a fan monitor
		final Monitor fanMonitor = Monitor
			.builder()
			.type(FAN)
			.metrics(new HashMap<>(Map.of(FAN_SPEED_METRIC, NumberMetric.builder().value(0.7).build())))
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, TEST_CONNECTOR)))
			.build();

		// Set the previously created fan monitor in telemetryManager
		final Map<String, Monitor> fanMonitors = new HashMap<>(Map.of(MONITOR_ID, fanMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(FAN, fanMonitors)));

		// Create a new HardwareStrategy
		final IStrategy firstHardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
		// Run the new HardwareStrategy
		telemetryManager.run(firstHardwareStrategy);

		// Check the computed and collected power metric
		final NumberMetric power = fanMonitor.getMetric(FAN_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(fanMonitor.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));

		power.save();

		// Next collect
		// Create a new HardwareStrategy
		final IStrategy secondHardwareStrategy = new HardwareStrategy(telemetryManager, NEXT_STRATEGY_TIME);
		// Run the new HardwareStrategy
		telemetryManager.run(secondHardwareStrategy);

		// Check the computed and collected energy metric
		assertNotNull(fanMonitor.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testHasHardwareMonitors() {
		// Scenario 1: the TelemetryManager contains a fan monitor
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager newTelemetryManager = new TelemetryManager();
			// Create a fan monitor
			final Monitor fanMonitor = Monitor.builder().type(FAN).build();

			// Set the previously created fan monitor in telemetryManager
			final Map<String, Monitor> fanMonitors = new HashMap<>(Map.of(MONITOR_ID, fanMonitor));
			newTelemetryManager.setMonitors(new HashMap<>(Map.of(FAN, fanMonitors)));
			assertTrue(hardwareStrategy.hasHardwareMonitors(newTelemetryManager));
		}

		// Scenario 2: the TelemetryManager doesn't define any monitor
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager newTelemetryManager = new TelemetryManager();
			assertFalse(hardwareStrategy.hasHardwareMonitors(newTelemetryManager));
		}

		// Scenario 3: the TelemetryManager contains a host monitor only
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager newTelemetryManager = new TelemetryManager();
			// Create a host monitor
			final Monitor hostMonitor = Monitor.builder().type(HOST).build();

			// Set the previously created host monitor in telemetryManager
			final Map<String, Monitor> hostMonitors = new HashMap<>(Map.of(MONITOR_ID, hostMonitor));
			newTelemetryManager.setMonitors(new HashMap<>(Map.of(HOST, hostMonitors)));
			assertFalse(hardwareStrategy.hasHardwareMonitors(newTelemetryManager));
		}

		// Scenario 4: the TelemetryManager contains a connector monitor only
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager newTelemetryManager = new TelemetryManager();
			// Create a connector monitor
			final Monitor hostMonitor = Monitor.builder().type(CONNECTOR).build();

			// Set the previously created connector monitor in telemetryManager
			final Map<String, Monitor> connectorMonitors = new HashMap<>(Map.of(MONITOR_ID, hostMonitor));
			newTelemetryManager.setMonitors(new HashMap<>(Map.of(CONNECTOR, connectorMonitors)));
			assertFalse(hardwareStrategy.hasHardwareMonitors(newTelemetryManager));
		}
	}
}
