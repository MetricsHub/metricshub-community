package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.CPU_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.CPU_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.util.HwConstants.HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;

import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class CpuPowerEstimatorTest {

	@Test
	void testEstimatePowerAndEnergy() {
		final Monitor monitor = Monitor.builder().build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(1696597422644L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
			.build();
		final CpuPowerEstimator cpuPowerEstimator = new CpuPowerEstimator(monitor, telemetryManager);
		// Default is 0.25 * (2500000000 / 1000000000) * 19 = 1187.5
		Double estimatedPower = cpuPowerEstimator.estimatePower();
		assertEquals(11.875, estimatedPower);
		assertNull(cpuPowerEstimator.estimateEnergy());

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			CPU_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);
		collectedPowerMetric.save();

		// CPU speed limit is 2000000000 and CPU thermal dissipation rate is 0.5
		telemetryManager.setStrategyTime(1696597422644L + 60 * 1000);
		metricFactory.collectNumberMetric(
			monitor,
			HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX,
			2000000000D,
			telemetryManager.getStrategyTime()
		);

		metricFactory.collectNumberMetric(
			monitor,
			HW_HOST_CPU_THERMAL_DISSIPATION_RATE,
			0.5,
			telemetryManager.getStrategyTime()
		);

		// Estimated power is 0.5 * (2000000000 / 1000000000) * 19 = 19
		estimatedPower = cpuPowerEstimator.estimatePower();
		assertEquals(19D, estimatedPower);
		collectedPowerMetric.save();

		// Estimated energy is 1140.0
		Double estimatedEnergy = cpuPowerEstimator.estimateEnergy();
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			CPU_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();

		assertEquals(1140D, estimatedEnergy);

		// CPU speed limit is 5000000000 and CPU thermal dissipation rate is 0.4
		telemetryManager.setStrategyTime(1696597422644L + 2 * 60 * 1000);
		metricFactory.collectNumberMetric(
			monitor,
			HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX,
			5000000000D,
			telemetryManager.getStrategyTime()
		);

		metricFactory.collectNumberMetric(
			monitor,
			"__hw.host.cpu.thermal_dissipation_rate",
			0.4,
			telemetryManager.getStrategyTime()
		);

		// Estimated power is 0.4 * (5000000000 / 1000000000) * 19 = 7.6
		estimatedPower = cpuPowerEstimator.estimatePower();
		assertEquals(38D, estimatedPower);
		collectedPowerMetric.save();

		// Estimated energy is 5700.0
		estimatedEnergy = cpuPowerEstimator.estimateEnergy();
		collectedEnergyMetric.save();

		assertEquals(5700D, estimatedEnergy);
	}
}
