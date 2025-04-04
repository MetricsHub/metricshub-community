package org.metricshub.hardware.util;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.metricshub.hardware.util.HwConstants.HW_ENCLOSURE_ENERGY;
import static org.metricshub.hardware.util.HwConstants.HW_ENCLOSURE_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_ESTIMATED_ENERGY;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_MEASURED_ENERGY;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_MEASURED_POWER;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.sustainability.HardwarePowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.HostMonitorPowerAndEnergyEstimator;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class PowerAndEnergyCollectHelper {

	/**
	 * Computes the estimated energy using two calls to power estimation then collects both power and energy in the monitor
	 * @param monitor the monitor to collect
	 * @param powerMetricName the power metric name e.g: "hw.power{hw.type=\"fan\"}"
	 * @param energyMetricName the energy metric name e.g: "hw.energy{hw.type=\"fan\"}"
	 * @param telemetryManager the telemetry manager {@link TelemetryManager}
	 * @param hardwarePowerAndEnergyEstimator generic estimator class which can used by the different hardware {@link HardwarePowerAndEnergyEstimator}
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
		if (estimatedPower == null) {
			log.warn(
				"Hostname {} - Received null value for power consumption. Consequently, the metric '{}' will not be collected on monitor '{}' (ID: {})",
				telemetryManager.getHostname(),
				powerMetricName,
				monitor.getType(),
				monitor.getId()
			);
			return;
		}

		// Create metricFactory and collect power
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		metricFactory.collectNumberMetric(monitor, powerMetricName, estimatedPower, telemetryManager.getStrategyTime());

		// Compute the estimated energy consumption
		final Double estimatedEnergy = hardwarePowerAndEnergyEstimator.estimateEnergy();
		if (estimatedEnergy == null) {
			log.info(
				"Hostname {} - Received null value for energy. Consequently, the metric '{}' will not be collected during this cycle on monitor '{}' (ID: {})",
				telemetryManager.getHostname(),
				energyMetricName,
				monitor.getType(),
				monitor.getId()
			);
			return;
		}

		// Collect the estimated energy consumption metric
		metricFactory.collectNumberMetric(monitor, energyMetricName, estimatedEnergy, telemetryManager.getStrategyTime());
	}

	/**
	 * Computes the estimated energy using two calls to power estimation then collects both power and energy in the host monitor
	 * @param monitor the monitor to collect
	 * @param telemetryManager the telemetry manager {@link TelemetryManager}
	 * @param hostMonitorEnergyAndPowerEstimator generic estimator class which can used by the different hardware {@link HardwarePowerAndEnergyEstimator}
	 */
	public static boolean collectHostPowerAndEnergy(
		final Monitor monitor,
		final TelemetryManager telemetryManager,
		final HostMonitorPowerAndEnergyEstimator hostMonitorEnergyAndPowerEstimator
	) {
		// Retrieve enclosure monitors
		final Map<String, Monitor> enclosures = telemetryManager.findMonitorsByType(KnownMonitorType.ENCLOSURE.getKey());

		// Create metricFactory to collect metrics
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		// Compute power and energy consumption
		final Double computedPower;
		final Double computedEnergy;

		if (isPowerMeasured(enclosures)) {
			// Compute measured power
			computedPower = hostMonitorEnergyAndPowerEstimator.computeMeasuredPower();
			if (isNullComputedPower(telemetryManager, monitor, HW_HOST_MEASURED_POWER, computedPower)) {
				return true;
			}
			metricFactory.collectNumberMetric(
				monitor,
				HW_HOST_MEASURED_POWER,
				computedPower,
				telemetryManager.getStrategyTime()
			);

			// Compute measured energy
			computedEnergy = hostMonitorEnergyAndPowerEstimator.computeMeasuredEnergy();
			if (isNullComputedEnergy(telemetryManager, monitor, HW_HOST_MEASURED_ENERGY, computedEnergy)) {
				return true;
			}
			metricFactory.collectNumberMetric(
				monitor,
				HW_HOST_MEASURED_ENERGY,
				computedEnergy,
				telemetryManager.getStrategyTime()
			);
			return true;
		} else {
			// Compute estimated power
			computedPower = hostMonitorEnergyAndPowerEstimator.computeEstimatedPower();
			if (isNullComputedPower(telemetryManager, monitor, HW_HOST_ESTIMATED_POWER, computedPower)) {
				return false;
			}
			metricFactory.collectNumberMetric(
				monitor,
				HW_HOST_ESTIMATED_POWER,
				computedPower,
				telemetryManager.getStrategyTime()
			);

			// Compute estimated energy
			computedEnergy = hostMonitorEnergyAndPowerEstimator.computeEstimatedEnergy();
			if (isNullComputedEnergy(telemetryManager, monitor, HW_HOST_ESTIMATED_ENERGY, computedEnergy)) {
				return false;
			}
			metricFactory.collectNumberMetric(
				monitor,
				HW_HOST_ESTIMATED_ENERGY,
				computedEnergy,
				telemetryManager.getStrategyTime()
			);
			return false;
		}
	}

	/**
	 * This method returns whether the computed power is null
	 * @param telemetryManager the telemetry manager
	 * @param monitor a given monitor
	 * @param computedPower the computed energy value
	 * @return boolean
	 */
	static boolean isNullComputedPower(
		final TelemetryManager telemetryManager,
		final Monitor monitor,
		final String powerMetricName,
		final Double computedPower
	) {
		if (computedPower == null) {
			log.warn(
				"Hostname {} - Received null value for power consumption. Consequently, the metric '{}' will not be collected on monitor '{}' (ID: {})",
				telemetryManager.getHostname(),
				powerMetricName,
				monitor.getType(),
				monitor.getId()
			);
			return true;
		}
		return false;
	}

	/**
	 * This method returns whether the computed energy is null
	 * @param telemetryManager the telemetry manager
	 * @param monitor a given monitor
	 * @param computedEnergy the computed energy value
	 * @return boolean
	 */
	static boolean isNullComputedEnergy(
		final TelemetryManager telemetryManager,
		final Monitor monitor,
		final String energyMetricName,
		final Double computedEnergy
	) {
		if (computedEnergy == null) {
			log.info(
				"Hostname {} - Received null value for energy. Consequently, the metric '{}' will not be collected during this cycle on monitor '{}' (ID: {})",
				telemetryManager.getHostname(),
				energyMetricName,
				monitor.getType(),
				monitor.getId()
			);
			return true;
		}
		return false;
	}

	/**
	 * Check if at least one monitor in the given map collects the power consumption or the energy
	 *
	 * @param enclosures map of monitors
	 * @return boolean value
	 */
	private static boolean isPowerMeasured(final Map<String, Monitor> enclosures) {
		return Optional
			.ofNullable(enclosures)
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.anyMatch(monitor ->
				CollectHelper.getUpdatedNumberMetricValue(monitor, HW_ENCLOSURE_POWER) != null ||
				CollectHelper.getUpdatedNumberMetricValue(monitor, HW_ENCLOSURE_ENERGY) != null
			);
	}
}
