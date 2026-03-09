package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.HW_HOST_AMBIENT_TEMPERATURE;
import static org.metricshub.hardware.common.Constants.HW_HOST_AVERAGE_CPU_TEMPERATURE;
import static org.metricshub.hardware.common.Constants.TEMPERATURE_METRIC;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class HostMonitorThermalCalculatorTest {

	private HostMonitorThermalCalculator hostMonitorThermalCalculator;
	private TelemetryManager telemetryManager;
	private static final String TEMPERATURE_MONITOR_ID = "temperatureMonitor";
	private static final String HOST_ID = "host";
	private static final String IS_CPU_SENSOR = "__is_cpu_sensor";
	private static final String HW_HOST_CPU_THERMAL_DISSIPATION_RATE = "__hw.host.cpu.thermal_dissipation_rate";
	private static final String TRUE_STRING = "true";
	private static final String HW_HOST_HEATING_MARGIN = "hw.host.heating_margin";
	private static final String TEMPERATURE_WARNING_THRESHOLD = "hw.temperature.limit{limit_type=\"high.degraded\"}";
	private static final String TEMPERATURE_ALARM_THRESHOLD = "hw.temperature.limit{limit_type=\"high.critical\"}";

	@BeforeEach
	void setup() {
		telemetryManager =
			TelemetryManager
				.builder()
				.strategyTime(100L)
				.hostConfiguration(HostConfiguration.builder().hostId(HOST_ID).hostname(HOST_ID).build())
				.build();
		hostMonitorThermalCalculator = new HostMonitorThermalCalculator(telemetryManager);
	}

	@Test
	void testRunWithComputeTemperatureMetrics() {
		final Monitor host = Monitor.builder().id(HOST_ID).type(KnownMonitorType.HOST.getKey()).build();

		// Set host as endpoint
		host.setAsEndpoint();

		// temperatureMonitors is null

		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertNull(host.getMetric(TEMPERATURE_METRIC, NumberMetric.class));

		// temperatureMonitors is empty

		final Map<String, Map<String, Monitor>> monitors = telemetryManager.getMonitors();
		monitors.put(KnownMonitorType.TEMPERATURE.getKey(), new LinkedHashMap<>());

		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertNull(host.getMetric(TEMPERATURE_METRIC, NumberMetric.class));

		// No CPU sensor

		final Monitor temperatureMonitor = Monitor
			.builder()
			.id(TEMPERATURE_MONITOR_ID)
			.type(KnownMonitorType.TEMPERATURE.getKey())
			.build();

		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_ID);
		telemetryManager.addNewMonitor(temperatureMonitor, KnownMonitorType.TEMPERATURE.getKey(), TEMPERATURE_MONITOR_ID);

		assertNull(host.getMetric(HW_HOST_AMBIENT_TEMPERATURE, NumberMetric.class));
		assertNull(host.getMetric(HW_HOST_AVERAGE_CPU_TEMPERATURE, NumberMetric.class));

		final MetricFactory metricFactory = new MetricFactory(HOST_ID, new ConnectorStore());
		metricFactory.collectNumberMetric(temperatureMonitor, TEMPERATURE_METRIC, 10.0, telemetryManager.getStrategyTime());
		hostMonitorThermalCalculator.computeHostTemperatureMetrics();

		assertEquals(10.0, host.getMetric(HW_HOST_AMBIENT_TEMPERATURE, NumberMetric.class).getValue());
		assertNull(host.getMetric(HW_HOST_AVERAGE_CPU_TEMPERATURE, NumberMetric.class));

		// Present CPU sensor
		host.setMetrics(new HashMap<>());
		temperatureMonitor.getAttributes().put(IS_CPU_SENSOR, TRUE_STRING);

		metricFactory.collectNumberMetric(temperatureMonitor, TEMPERATURE_METRIC, 10.0, telemetryManager.getStrategyTime());

		telemetryManager.setMonitors(new LinkedHashMap<>());
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_ID);
		telemetryManager.addNewMonitor(temperatureMonitor, KnownMonitorType.TEMPERATURE.getKey(), TEMPERATURE_MONITOR_ID);

		assertNull(host.getMetric(HW_HOST_AMBIENT_TEMPERATURE, NumberMetric.class));
		assertNull(host.getMetric(HW_HOST_AVERAGE_CPU_TEMPERATURE, NumberMetric.class));

		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertEquals(10.0, host.getMetric(HW_HOST_AMBIENT_TEMPERATURE, NumberMetric.class).getValue());
		assertEquals(10.0, host.getMetric(HW_HOST_AVERAGE_CPU_TEMPERATURE, NumberMetric.class).getValue());
		assertEquals(0.0, host.getMetric(HW_HOST_CPU_THERMAL_DISSIPATION_RATE, NumberMetric.class).getValue());

		// Add another temperature monitor
		final Monitor otherTemperatureMonitor = Monitor
			.builder()
			.id(TEMPERATURE_MONITOR_ID)
			.type(KnownMonitorType.TEMPERATURE.getKey())
			.build();

		otherTemperatureMonitor.getAttributes().put(IS_CPU_SENSOR, TRUE_STRING);

		metricFactory.collectNumberMetric(
			otherTemperatureMonitor,
			TEMPERATURE_METRIC,
			20.0,
			telemetryManager.getStrategyTime()
		);
		telemetryManager.addNewMonitor(
			otherTemperatureMonitor,
			KnownMonitorType.TEMPERATURE.getKey(),
			TEMPERATURE_MONITOR_ID + "_2"
		);

		// Check the estimation of the host thermal dissipation rate
		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertEquals(0.0, host.getMetric(HW_HOST_CPU_THERMAL_DISSIPATION_RATE, NumberMetric.class).getValue());

		// Heating margin check
		assertNull(host.getMetric(HW_HOST_HEATING_MARGIN, NumberMetric.class));

		metricFactory.collectNumberMetric(
			temperatureMonitor,
			TEMPERATURE_ALARM_THRESHOLD,
			40.0,
			telemetryManager.getStrategyTime()
		);
		telemetryManager.addNewMonitor(temperatureMonitor, KnownMonitorType.TEMPERATURE.getKey(), TEMPERATURE_MONITOR_ID);
		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertNotNull(host.getMetric(HW_HOST_HEATING_MARGIN, NumberMetric.class));
		// Heating margin is 40.0 * 0.9 - 10.0 = 26.0
		assertEquals(26.0, host.getMetric(HW_HOST_HEATING_MARGIN, NumberMetric.class).getValue());

		metricFactory.collectNumberMetric(
			temperatureMonitor,
			TEMPERATURE_WARNING_THRESHOLD,
			30.0,
			telemetryManager.getStrategyTime()
		);
		telemetryManager.addNewMonitor(temperatureMonitor, KnownMonitorType.TEMPERATURE.getKey(), TEMPERATURE_MONITOR_ID);
		hostMonitorThermalCalculator.computeHostTemperatureMetrics();
		assertNotNull(host.getMetric(HW_HOST_HEATING_MARGIN, NumberMetric.class));
		// Heating margin is 30.0 - 10.0 = 20.0
		assertEquals(20.0, host.getMetric(HW_HOST_HEATING_MARGIN, NumberMetric.class).getValue());
	}
}
