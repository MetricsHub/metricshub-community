package org.sentrysoftware.metricshub.hardware.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.sentrysoftware.metricshub.hardware.common.Constants.FAN_ENERGY_METRIC;
import static org.sentrysoftware.metricshub.hardware.common.Constants.FAN_POWER_METRIC;
import static org.sentrysoftware.metricshub.hardware.common.Constants.FAN_SPEED_METRIC;
import static org.sentrysoftware.metricshub.hardware.common.Constants.HW_CONNECTOR;
import static org.sentrysoftware.metricshub.hardware.common.Constants.LOCALHOST;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
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

	private TelemetryManager telemetryManager;

	@BeforeEach
	void init() {
		telemetryManager =
			TelemetryManager.builder().hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build()).build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(HW_CONNECTOR, connectorNamespace)))
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
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
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
			final TelemetryManager telemetryManager = new TelemetryManager();
			// Create a fan monitor
			final Monitor fanMonitor = Monitor.builder().type(FAN).build();

			// Set the previously created fan monitor in telemetryManager
			final Map<String, Monitor> fanMonitors = new HashMap<>(Map.of(MONITOR_ID, fanMonitor));
			telemetryManager.setMonitors(new HashMap<>(Map.of(FAN, fanMonitors)));
			assertTrue(hardwareStrategy.hasHardwareMonitors(telemetryManager));
		}

		// Scenario 2: the TelemetryManager doesn't define any monitor
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager telemetryManager = new TelemetryManager();
			assertFalse(hardwareStrategy.hasHardwareMonitors(telemetryManager));
		}

		// Scenario 3: the TelemetryManager contains a host monitor only
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager telemetryManager = new TelemetryManager();
			// Create a host monitor
			final Monitor hostMonitor = Monitor.builder().type(HOST).build();

			// Set the previously created host monitor in telemetryManager
			final Map<String, Monitor> hostMonitors = new HashMap<>(Map.of(MONITOR_ID, hostMonitor));
			telemetryManager.setMonitors(new HashMap<>(Map.of(HOST, hostMonitors)));
			assertFalse(hardwareStrategy.hasHardwareMonitors(telemetryManager));
		}

		// Scenario 4: the TelemetryManager contains a connector monitor only
		{
			final HardwareStrategy hardwareStrategy = new HardwareStrategy(telemetryManager, STRATEGY_TIME);
			final TelemetryManager telemetryManager = new TelemetryManager();
			// Create a connector monitor
			final Monitor hostMonitor = Monitor.builder().type(CONNECTOR).build();

			// Set the previously created connector monitor in telemetryManager
			final Map<String, Monitor> connectorMonitors = new HashMap<>(Map.of(MONITOR_ID, hostMonitor));
			telemetryManager.setMonitors(new HashMap<>(Map.of(CONNECTOR, connectorMonitors)));
			assertFalse(hardwareStrategy.hasHardwareMonitors(telemetryManager));
		}
	}
}
