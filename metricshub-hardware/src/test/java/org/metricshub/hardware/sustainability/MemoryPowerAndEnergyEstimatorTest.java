package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.common.Constants.MEMORY_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.MEMORY_POWER_METRIC;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class MemoryPowerAndEnergyEstimatorTest {

	@Test
	void testDoPowerEstimation() {
		Monitor monitor = Monitor.builder().build();
		TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(1696597422644L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
			.build();
		MemoryPowerAndEnergyEstimator memoryPowerAndEnergyEstimator = new MemoryPowerAndEnergyEstimator(
			monitor,
			telemetryManager
		);

		assertEquals(4.0, memoryPowerAndEnergyEstimator.doPowerEstimation());
	}

	@Test
	void testEstimateEnergy() {
		Monitor monitor = Monitor.builder().build();
		TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.strategyTime(1696597422644L)
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
			.build();
		MemoryPowerAndEnergyEstimator memoryPowerAndEnergyEstimator = new MemoryPowerAndEnergyEstimator(
			monitor,
			telemetryManager
		);
		// Estimate energy consumption, no previous collect time
		assertNull(memoryPowerAndEnergyEstimator.estimateEnergy());

		// Estimate power consumption
		Double estimatedPower = memoryPowerAndEnergyEstimator.estimatePower();
		Double estimatedEnergy = memoryPowerAndEnergyEstimator.estimateEnergy();
		assertNull(estimatedEnergy);
		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			MEMORY_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Save the collected power metric
		collectedPowerMetric.save();
		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = memoryPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(monitor, MEMORY_POWER_METRIC, estimatedPower, telemetryManager.getStrategyTime());

		// Estimate the energy
		estimatedEnergy = memoryPowerAndEnergyEstimator.estimateEnergy();
		assertEquals(480.0, estimatedEnergy);
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			MEMORY_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();
		collectedPowerMetric.save();

		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = memoryPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(monitor, MEMORY_POWER_METRIC, estimatedPower, telemetryManager.getStrategyTime());

		// Estimate the energy
		assertEquals(960.0, memoryPowerAndEnergyEstimator.estimateEnergy());
	}
}
