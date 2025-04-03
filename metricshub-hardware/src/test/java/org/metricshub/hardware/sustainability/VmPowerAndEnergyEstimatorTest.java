package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.HOST;
import static org.metricshub.hardware.common.Constants.HOST_1;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.common.Constants.ON;
import static org.metricshub.hardware.common.Constants.VM_1_ONLINE;
import static org.metricshub.hardware.common.Constants.VM_OFFLINE_2;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_3;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_BAD_POWER_SHARE_5;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_NO_POWER_SHARE_4;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_ZERO_POWER_SHARE_1;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_VM_POWER_SHARE_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_VM_POWER_STATE_METRIC;
import static org.metricshub.hardware.util.HwConstants.POWER_SOURCE_ID_ATTRIBUTE;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.StateSetMetric;

class VmPowerAndEnergyEstimatorTest {

	private static Monitor buildMonitor(final String monitorType, final String id) {
		return Monitor.builder().id(id).type(monitorType).build();
	}

	@Test
	void testEstimateVmsPowerConsumption() {
		// Create the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).hostId(HOST).build())
			.build();

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		// Prepare the monitors and their metrics

		final Monitor vmOnline1 = buildMonitor(KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		vmOnline1.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline1, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());

		final Monitor vmOffline2 = buildMonitor(KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		vmOffline2.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value("Off").build());
		metricFactory.collectNumberMetric(vmOffline2, HW_VM_POWER_SHARE_METRIC, 10.0, telemetryManager.getStrategyTime());

		final Monitor vmOnline3 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		vmOnline3.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline3, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());

		final Monitor vmOnlineNoPowerShare4 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		vmOnlineNoPowerShare4.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());

		final Monitor vmOnlineBadPowerShare5 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);
		vmOnlineBadPowerShare5.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(
			vmOnlineBadPowerShare5,
			HW_VM_POWER_SHARE_METRIC,
			-15.0,
			telemetryManager.getStrategyTime()
		);

		// Create the host monitor
		final Monitor host = buildMonitor(KnownMonitorType.HOST.getKey(), HOST_1);

		// Set the host monitor estimated power
		metricFactory.collectNumberMetric(host, HW_HOST_ESTIMATED_POWER, 100.0, telemetryManager.getStrategyTime());

		// Add the created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_1);
		telemetryManager.addNewMonitor(vmOnline1, KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		telemetryManager.addNewMonitor(vmOffline2, KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		telemetryManager.addNewMonitor(vmOnline3, KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		telemetryManager.addNewMonitor(vmOnlineNoPowerShare4, KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		telemetryManager.addNewMonitor(vmOnlineBadPowerShare5, KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);

		// Set the totalPowerSharesByPowerSource map
		final Map<String, Double> totalPowerSharesByPowerSource = new HashMap<>(Map.of(HOST_1, 10.0));

		// Init VmPowerAndEnergyEstimator objects and add power source id attribute
		final VmPowerAndEnergyEstimator vmOnline1Estimator = new VmPowerAndEnergyEstimator(
			vmOnline1,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmOnline1.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		final VmPowerAndEnergyEstimator vmOffline2Estimator = new VmPowerAndEnergyEstimator(
			vmOffline2,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmOffline2.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		final VmPowerAndEnergyEstimator vmOnline3Estimator = new VmPowerAndEnergyEstimator(
			vmOnline3,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmOnline3.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		final VmPowerAndEnergyEstimator vmOnlineNoPowerShare4Estimator = new VmPowerAndEnergyEstimator(
			vmOnlineNoPowerShare4,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmOnlineNoPowerShare4.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		final VmPowerAndEnergyEstimator vmOnlineBadPowerShare5Estimator = new VmPowerAndEnergyEstimator(
			vmOnlineBadPowerShare5,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmOnlineBadPowerShare5.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		// Call doPowerEstimation on VmPowerAndEnergyEstimator instances and check the computed power consumption values

		assertEquals(50D, vmOnline1Estimator.doPowerEstimation()); // 50% of the host Power
		assertEquals(0.0, vmOffline2Estimator.doPowerEstimation()); // Offline
		assertEquals(50D, vmOnline3Estimator.doPowerEstimation()); // 50% of the host Power
		assertEquals(0.0, vmOnlineNoPowerShare4Estimator.doPowerEstimation()); // powerShare collected not collected
		assertEquals(0.0, vmOnlineBadPowerShare5Estimator.doPowerEstimation()); // Wrong power share
	}

	@Test
	void testEstimateVmsPowerConsumptionTotalPowerShareZero() {
		// Create the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).hostId(HOST).build())
			.build();

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		// Create the host monitor and set its power metric

		final Monitor host = buildMonitor(KnownMonitorType.HOST.getKey(), HOST_1);

		metricFactory.collectNumberMetric(host, HW_HOST_ESTIMATED_POWER, 100.0, telemetryManager.getStrategyTime());

		// Create a VM monitor
		final Monitor vmOnlineZeroPowerShare1 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_ZERO_POWER_SHARE_1);
		vmOnlineZeroPowerShare1.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		vmOnlineZeroPowerShare1.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		// Set vm power share to 0.0
		metricFactory.collectNumberMetric(
			vmOnlineZeroPowerShare1,
			HW_VM_POWER_SHARE_METRIC,
			0.0,
			telemetryManager.getStrategyTime()
		);

		// Add the monitors to the telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_1);
		telemetryManager.addNewMonitor(
			vmOnlineZeroPowerShare1,
			KnownMonitorType.HOST.getKey(),
			VM_ONLINE_ZERO_POWER_SHARE_1
		);

		// Set the totalPowerSharesByPowerSource map
		final Map<String, Double> totalPowerSharesByPowerSource = new HashMap<>(Map.of(HOST_1, 10.0));
		final VmPowerAndEnergyEstimator vmPowerAndEnergyEstimator = new VmPowerAndEnergyEstimator(
			vmOnlineZeroPowerShare1,
			telemetryManager,
			totalPowerSharesByPowerSource,
			false
		);
		vmPowerAndEnergyEstimator.setTotalPowerSharesByPowerSource(totalPowerSharesByPowerSource);

		// Check the computed power consumption
		assertEquals(0.0, vmPowerAndEnergyEstimator.doPowerEstimation());
	}

	@Test
	void testEstimateVmsPowerConsumptionHostPowerConsumptionMissing() {
		// Create the telemetry manager
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(120L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).hostId(HOST).build())
			.build();

		// Create the host monitor without power consumption metrics
		final Monitor host = buildMonitor(KnownMonitorType.HOST.getKey(), HOST_1);

		// Create a VM monitor
		final Monitor vm1Online = buildMonitor(KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		vm1Online.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		vm1Online.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		// Set the power share metric of the vm monitor
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		metricFactory.collectNumberMetric(vm1Online, HW_VM_POWER_SHARE_METRIC, 10.0, telemetryManager.getStrategyTime());

		// Add the monitors to the telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_1);
		telemetryManager.addNewMonitor(vm1Online, KnownMonitorType.HOST.getKey(), VM_1_ONLINE);

		// Set the totalPowerSharesByPowerSource map
		final Map<String, Double> totalPowerSharesByPowerSource = new HashMap<>(Map.of(HOST_1, 10.0));
		final VmPowerAndEnergyEstimator vmPowerAndEnergyEstimator = new VmPowerAndEnergyEstimator(
			vm1Online,
			telemetryManager,
			totalPowerSharesByPowerSource,
			true
		);
		vmPowerAndEnergyEstimator.setTotalPowerSharesByPowerSource(totalPowerSharesByPowerSource);

		// Check the computed power consumption
		assertNull(vmPowerAndEnergyEstimator.doPowerEstimation());
	}
}
