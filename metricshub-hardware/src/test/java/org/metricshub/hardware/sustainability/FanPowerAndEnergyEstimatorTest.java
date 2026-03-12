package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.FAN_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_SPEED_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_SPEED_RATIO_METRIC;
import static org.metricshub.hardware.common.Constants.LOCALHOST;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class FanPowerAndEnergyEstimatorTest {

	private FanPowerAndEnergyEstimator fanPowerAndEnergyEstimator;

	private Monitor monitor = null;
	private TelemetryManager telemetryManager = null;

	@BeforeEach
	void init() {
		monitor =
			Monitor
				.builder()
				.metrics(
					new HashMap<>(
						Map.of(
							FAN_SPEED_METRIC,
							NumberMetric.builder().value(7.0).build(),
							FAN_SPEED_RATIO_METRIC,
							NumberMetric.builder().value(0.2).build()
						)
					)
				)
				.build();
		telemetryManager =
			TelemetryManager
				.builder()
				.strategyTime(1696597422644L)
				.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
				.build();
		fanPowerAndEnergyEstimator = new FanPowerAndEnergyEstimator(monitor, telemetryManager);
	}

	@Test
	void testEstimatePower() {
		// If fanSpeed metric value is a valid positive number
		assertEquals(0.007, fanPowerAndEnergyEstimator.estimatePower());

		// If fanSpeed metric value is not a valid positive number
		monitor.getMetric(FAN_SPEED_METRIC, NumberMetric.class).setValue(-7000.0);
		assertEquals(1.0, fanPowerAndEnergyEstimator.estimatePower());
	}

	@Test
	void testEstimateEnergy() {
		// Estimate energy consumption, no previous collect time
		assertNull(fanPowerAndEnergyEstimator.estimateEnergy());

		// Estimate power consumption
		Double estimatedPower = fanPowerAndEnergyEstimator.estimatePower();
		Double estimatedEnergy = fanPowerAndEnergyEstimator.estimateEnergy();
		assertNull(estimatedEnergy);
		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			FAN_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Save the collected power metric
		collectedPowerMetric.save();
		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = fanPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(monitor, FAN_POWER_METRIC, estimatedPower, telemetryManager.getStrategyTime());

		// Estimate the energy
		estimatedEnergy = fanPowerAndEnergyEstimator.estimateEnergy();
		assertEquals(0.84, estimatedEnergy);
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			FAN_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();
		collectedPowerMetric.save();

		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = fanPowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(monitor, FAN_POWER_METRIC, estimatedPower, telemetryManager.getStrategyTime());

		// Estimate the energy
		assertEquals(1.68, fanPowerAndEnergyEstimator.estimateEnergy());
	}
}
