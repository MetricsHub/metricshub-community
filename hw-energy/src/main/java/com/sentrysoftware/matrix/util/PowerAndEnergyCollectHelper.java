package com.sentrysoftware.matrix.util;

import com.sentrysoftware.matrix.sustainability.HardwarePowerAndEnergyEstimator;
import com.sentrysoftware.matrix.telemetry.MetricFactory;
import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;

public class PowerAndEnergyCollectHelper {

	/**
	 * Computes the estimated energy using two calls to power estimation then collects both power and energy in the monitor
	 * @param monitor the monitor to collect
	 * @param powerMetricName the power metric name e.g: "hw.power{hw.type=\"fan\"}"
	 * @param energyMetricName the energy metric name e.g: "hw.energy{hw.type=\"fan\"}"
	 */
	public static void collectPowerAndEnergy(
		final Monitor monitor,
		final String powerMetricName,
		final String energyMetricName,
		final TelemetryManager telemetryManager,
		final HardwarePowerAndEnergyEstimator hardwarePowerAndEnergyEstimator
	) {
		// Estimate power consumption
		final Double estimatedPower = hardwarePowerAndEnergyEstimator.estimatePower();

		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostConfiguration().getHostname());
		metricFactory.collectNumberMetric(monitor, powerMetricName, estimatedPower, telemetryManager.getStrategyTime());

		// Compute the estimated energy consumption
		final Double estimatedEnergy = hardwarePowerAndEnergyEstimator.estimateEnergy();

		// Collect the estimated energy consumption metric
		metricFactory.collectNumberMetric(monitor, energyMetricName, estimatedEnergy, telemetryManager.getStrategyTime());
	}
}
