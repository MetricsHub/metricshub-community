package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.hardware.common.Constants.HW_CONNECTOR;
import static org.metricshub.hardware.common.Constants.HW_CPU_POWER;
import static org.metricshub.hardware.common.Constants.HW_ENCLOSURE_POWER;
import static org.metricshub.hardware.common.Constants.HW_MEMORY_POWER;
import static org.metricshub.hardware.common.Constants.HW_PHYSICAL_DISK_POWER;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_MEASURED_ENERGY;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_MEASURED_POWER;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class HostMonitorPowerAndEnergyEstimatorTest {

	/**
	 * Build a new host monitor
	 *
	 * @return {@link Monitor} instance
	 */
	private Monitor buildHostMonitor() {
		return Monitor
			.builder()
			.id(KnownMonitorType.HOST.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.HOST.getKey())
			.build();
	}

	@Test
	void testComputeMeasuredPowerNoData() {
		// Create host and enclosure monitors
		final Monitor host = buildHostMonitor();
		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.HOST.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.build();

		// Initialize the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostId(LOCALHOST).build())
			.build();

		// Add the previously created monitors to the telemetry manager
		telemetryManager.addNewMonitor(host, host.getType(), host.getId());
		telemetryManager.addNewMonitor(enclosure, enclosure.getType(), enclosure.getId());

		// Call computeMeasuredPower and check its response
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator =
			new HostMonitorPowerAndEnergyEstimator(host, telemetryManager);
		Double measuredPower = hostMonitorEnergyAndPowerEstimator.computeMeasuredPower();
		assertNull(measuredPower);
		assertNull(CollectHelper.getUpdatedNumberMetricValue(host, HW_HOST_MEASURED_POWER));
		assertNull(CollectHelper.getUpdatedNumberMetricValue(host, HW_HOST_ESTIMATED_POWER));
	}

	@Test
	void testComputeMeasuredPowerEnclosureHasPower() {
		// Create host and enclosure monitors
		final Monitor host = buildHostMonitor();
		final Monitor enclosure = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.build();

		// Initialize the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostId(LOCALHOST).build())
			.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(HW_CONNECTOR, connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);

		// Add the power metric to enclosure
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		metricFactory.collectNumberMetric(enclosure, HW_ENCLOSURE_POWER, 120.0, telemetryManager.getStrategyTime());

		// Add the previously created monitors to the telemetry manager
		telemetryManager.addNewMonitor(host, host.getType(), host.getId());
		telemetryManager.addNewMonitor(enclosure, enclosure.getType(), enclosure.getId());

		// Call computeMeasuredPower and check its response
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator =
			new HostMonitorPowerAndEnergyEstimator(host, telemetryManager);
		assertEquals(120.0, hostMonitorEnergyAndPowerEstimator.computeMeasuredPower());
		assertNull(CollectHelper.getUpdatedNumberMetricValue(host, HW_HOST_MEASURED_POWER));
	}

	@Test
	void testComputeEstimatedPowerFirstCollect() {
		// Initialize the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostId(LOCALHOST).build())
			.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(HW_CONNECTOR, connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);

		// Initialize the host and other monitors
		final Monitor host = buildHostMonitor();

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.build();

		final Monitor cpu = Monitor
			.builder()
			.id("cpu1")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.CPU.getKey())
			.build();
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime());

		final Monitor memory = Monitor
			.builder()
			.id("memory1")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.MEMORY.getKey())
			.build();

		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime());

		final Monitor disk = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime());

		final Monitor diskNoPower = Monitor
			.builder()
			.id("disk_noPower")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		final Monitor missingDisk = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.id("disk_nvm_2")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		// Add the previously created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), KnownMonitorType.HOST.getKey());
		telemetryManager.addNewMonitor(cpu, KnownMonitorType.CPU.getKey(), "cpu1");
		telemetryManager.addNewMonitor(disk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_1");
		telemetryManager.addNewMonitor(memory, KnownMonitorType.MEMORY.getKey(), "memory1");
		telemetryManager.addNewMonitor(diskNoPower, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_noPower");
		telemetryManager.addNewMonitor(missingDisk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_2");
		telemetryManager.addNewMonitor(enclosure, KnownMonitorType.ENCLOSURE.getKey(), KnownMonitorType.ENCLOSURE.getKey());

		// Call computeEstimatedPower and check its response
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator =
			new HostMonitorPowerAndEnergyEstimator(host, telemetryManager);
		assertEquals(77.78, hostMonitorEnergyAndPowerEstimator.computeEstimatedPower());
	}

	@Test
	void testComputeEstimatedPowerSecondCollect() {
		// Initialize the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(new Date().getTime())
			.hostConfiguration(HostConfiguration.builder().hostId(LOCALHOST).build())
			.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(HW_CONNECTOR, connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);

		// Initialize the host and other monitors
		final Monitor host = buildHostMonitor();

		// Set a previous collected host power value
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final NumberMetric previousPowerValue = metricFactory.collectNumberMetric(
			host,
			HW_HOST_ESTIMATED_POWER,
			60.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		// Initialize the monitors

		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.build();

		final Monitor cpu = Monitor
			.builder()
			.id("cpu1")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.CPU.getKey())
			.build();
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime());

		final Monitor memory = Monitor
			.builder()
			.id("memory1")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.MEMORY.getKey())
			.build();

		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime());

		final Monitor disk = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime());

		final Monitor diskNoPower = Monitor
			.builder()
			.id("disk_noPower")
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		final Monitor missingDisk = Monitor
			.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, HW_CONNECTOR)))
			.id("disk_nvm_2")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.build();

		// Add the previously created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), KnownMonitorType.HOST.getKey());
		telemetryManager.addNewMonitor(cpu, KnownMonitorType.CPU.getKey(), "cpu1");
		telemetryManager.addNewMonitor(disk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_1");
		telemetryManager.addNewMonitor(memory, KnownMonitorType.MEMORY.getKey(), "memory1");
		telemetryManager.addNewMonitor(diskNoPower, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_noPower");
		telemetryManager.addNewMonitor(missingDisk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_2");
		telemetryManager.addNewMonitor(enclosure, KnownMonitorType.ENCLOSURE.getKey(), KnownMonitorType.ENCLOSURE.getKey());

		// Call computeEstimatedPower and computeEstimatedEnergy and check their response
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator =
			new HostMonitorPowerAndEnergyEstimator(host, telemetryManager);
		hostMonitorEnergyAndPowerEstimator.computeEstimatedPower();
		assertEquals(9333.6, hostMonitorEnergyAndPowerEstimator.computeEstimatedEnergy());
	}

	@Test
	void testComputeMeasuredPowerHostHasEnergy() {
		// Build host and enclosure monitors
		final Monitor host = buildHostMonitor();
		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.HOST.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.build();

		// Initialize the telemetry manager and add metrics to created monitors
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostId(LOCALHOST).build())
			.build();
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		metricFactory.collectNumberMetric(host, HW_HOST_MEASURED_ENERGY, 3520255.0, telemetryManager.getStrategyTime());

		// Add the created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, host.getType(), host.getId());
		telemetryManager.addNewMonitor(enclosure, enclosure.getType(), enclosure.getId());

		// Call computeMeasuredEnergy and check its response
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator =
			new HostMonitorPowerAndEnergyEstimator(host, telemetryManager);
		hostMonitorEnergyAndPowerEstimator.computeMeasuredEnergy();

		assertEquals(3520255.0, CollectHelper.getNumberMetricValue(host, HW_HOST_MEASURED_ENERGY, false));
	}

	@Test
	void testGetAdjustedPowerConsumption() {
		final HostMonitorPowerAndEnergyEstimator estimator = new HostMonitorPowerAndEnergyEstimator(null, null);

		// null numerator
		assertEquals(0.0, estimator.getAdjustedPowerConsumption(null, 10.0, 50.0));

		// zero numerator
		assertEquals(0.0, estimator.getAdjustedPowerConsumption(0.0, 10.0, 50.0));

		// zero denominator
		assertEquals(0.0, estimator.getAdjustedPowerConsumption(5.0, 0.0, 50.0));

		// normal case: (2 / 10) * 50 = 10.0
		assertEquals(10.0, estimator.getAdjustedPowerConsumption(2.0, 10.0, 50.0));

		// HALF_UP rounding case
		assertEquals(1.67, estimator.getAdjustedPowerConsumption(1.0, 6.0, 10.0), 0.0001);
	}
}
