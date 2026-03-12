package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.common.Constants.ROBOTICS_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.ROBOTICS_MOVE_COUNT_METRIC;
import static org.metricshub.hardware.common.Constants.ROBOTICS_POWER_METRIC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class RoboticsPowerAndEnergyEstimatorTest {

	private RoboticsPowerAndEnergyEstimator roboticsPowerAndEnergyEstimator;

	private Monitor monitor = null;
	private TelemetryManager telemetryManager = null;

	@BeforeEach
	void init() {
		monitor =
			Monitor
				.builder()
				.metrics(new HashMap<>(Map.of(ROBOTICS_MOVE_COUNT_METRIC, NumberMetric.builder().value(7.0).build())))
				.build();
		telemetryManager =
			TelemetryManager
				.builder()
				.strategyTime(1696597422644L)
				.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
				.build();
		roboticsPowerAndEnergyEstimator = new RoboticsPowerAndEnergyEstimator(monitor, telemetryManager);
	}

	@Test
	void testEstimatePower() {
		// If moveCount metric value is not null
		monitor.getMetric(ROBOTICS_MOVE_COUNT_METRIC, NumberMetric.class).setValue(7.0);
		assertEquals(154.0, roboticsPowerAndEnergyEstimator.estimatePower());

		// If moveCount metric value is null
		monitor.setMetrics(Collections.emptyMap());
		assertEquals(48.0, roboticsPowerAndEnergyEstimator.estimatePower());
	}

	@Test
	void testEstimateEnergy() {
		// Estimate energy consumption, no previous collect time
		assertNull(roboticsPowerAndEnergyEstimator.estimateEnergy());

		// Estimate power consumption
		Double estimatedPower = roboticsPowerAndEnergyEstimator.estimatePower();
		Double estimatedEnergy = roboticsPowerAndEnergyEstimator.estimateEnergy();
		assertNull(estimatedEnergy);
		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			ROBOTICS_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Save the collected power metric
		collectedPowerMetric.save();
		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = roboticsPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			ROBOTICS_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		estimatedEnergy = roboticsPowerAndEnergyEstimator.estimateEnergy();
		assertEquals(18480.0, estimatedEnergy);
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			ROBOTICS_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();
		collectedPowerMetric.save();

		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = roboticsPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			ROBOTICS_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		assertEquals(36960.0, roboticsPowerAndEnergyEstimator.estimateEnergy());
	}
}
