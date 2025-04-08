package org.metricshub.hardware.sustainability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_MOUNT_COUNT_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_UNMOUNT_COUNT_METRIC;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class TapeDrivePowerAndEnergyEstimatorTest {

	private TapeDrivePowerAndEnergyEstimator tapeDrivePowerAndEnergyEstimator;

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
							TAPE_DRIVE_MOUNT_COUNT_METRIC,
							NumberMetric.builder().value(7.0).build(),
							TAPE_DRIVE_UNMOUNT_COUNT_METRIC,
							NumberMetric.builder().value(0.2).build()
						)
					)
				)
				.attributes(new HashMap<>(Map.of("name", "lto123")))
				.build();
		telemetryManager =
			TelemetryManager
				.builder()
				.strategyTime(1696597422644L)
				.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
				.build();
		tapeDrivePowerAndEnergyEstimator = new TapeDrivePowerAndEnergyEstimator(monitor, telemetryManager);
	}

	@Test
	void testEstimatePower() {
		assertEquals(46, tapeDrivePowerAndEnergyEstimator.estimatePower());
	}

	@Test
	void testEstimateEnergy() {
		// Estimate energy consumption, no previous collect time
		assertNull(tapeDrivePowerAndEnergyEstimator.estimateEnergy());

		// Estimate power consumption
		Double estimatedPower = tapeDrivePowerAndEnergyEstimator.estimatePower();
		Double estimatedEnergy = tapeDrivePowerAndEnergyEstimator.estimateEnergy();
		assertNull(estimatedEnergy);
		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			monitor,
			TAPE_DRIVE_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Save the collected power metric
		collectedPowerMetric.save();
		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = tapeDrivePowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			TAPE_DRIVE_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		estimatedEnergy = tapeDrivePowerAndEnergyEstimator.estimateEnergy();
		assertEquals(5520.0, estimatedEnergy);
		final NumberMetric collectedEnergyMetric = metricFactory.collectNumberMetric(
			monitor,
			TAPE_DRIVE_ENERGY_METRIC,
			estimatedEnergy,
			telemetryManager.getStrategyTime()
		);
		collectedEnergyMetric.save();
		collectedPowerMetric.save();

		telemetryManager.setStrategyTime(telemetryManager.getStrategyTime() + 2 * 60 * 1000);

		// Estimate power consumption again
		estimatedPower = tapeDrivePowerAndEnergyEstimator.estimatePower();

		// Collect the new power consumption metric
		metricFactory.collectNumberMetric(
			monitor,
			TAPE_DRIVE_POWER_METRIC,
			estimatedPower,
			telemetryManager.getStrategyTime()
		);

		// Estimate the energy
		assertEquals(11040.0, tapeDrivePowerAndEnergyEstimator.estimateEnergy());
	}
}
