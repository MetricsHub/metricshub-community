package org.metricshub.hardware.sustainability;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_ESTIMATED_ENERGY;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_MEASURED_ENERGY;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_MEASURED_POWER;
import static org.metricshub.hardware.constants.EnclosureConstants.HW_ENCLOSURE_POWER;

import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.hardware.util.HwCollectHelper;

@Data
@Slf4j
public class HostMonitorPowerAndEnergyEstimator {

	private TelemetryManager telemetryManager;
	private Monitor hostMonitor;
	private Double powerConsumption;

	public HostMonitorPowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		this.hostMonitor = monitor;
		this.telemetryManager = telemetryManager;
	}

	/**
	 * Estimates the power consumption of the host monitor
	 * @return Double
	 */
	public Double computeEstimatedPower() {
		powerConsumption = estimateHostPowerConsumption(sumEstimatedPowerConsumptions());
		return powerConsumption;
	}

	/**
	 * Computes the measured energy consumption of the host monitor
	 * @return Double
	 */
	public Double computeMeasuredEnergy() {
		// Compute measured energy
		return HwCollectHelper.estimateEnergyUsingPower(
			hostMonitor,
			telemetryManager,
			powerConsumption,
			HW_HOST_MEASURED_POWER,
			HW_HOST_MEASURED_ENERGY,
			telemetryManager.getStrategyTime()
		);
	}

	/**
	 * Computes the estimated energy consumption of the host monitor
	 * @return Double
	 */
	public Double computeEstimatedEnergy() {
		// Compute estimated energy
		return HwCollectHelper.estimateEnergyUsingPower(
			hostMonitor,
			telemetryManager,
			powerConsumption,
			HW_HOST_ESTIMATED_POWER,
			HW_HOST_ESTIMATED_ENERGY,
			telemetryManager.getStrategyTime()
		);
	}

	/**
	 * Computes the real power consumption of the host monitor
	 * @return Double
	 */
	public Double computeMeasuredPower() {
		final Map<String, Monitor> enclosureMonitors = telemetryManager.findMonitorsByType(
			KnownMonitorType.ENCLOSURE.getKey()
		);

		final Double totalMeasuredPowerConsumption = sumEnclosurePowerConsumptions(enclosureMonitors);

		// Adjust monitor power consumptions
		adjustAllPowerConsumptions(sumEstimatedPowerConsumptions(), totalMeasuredPowerConsumption);
		powerConsumption = totalMeasuredPowerConsumption;
		return powerConsumption;
	}

	/**
	 * Adjust the power consumption parameter of all the monitors
	 * @param totalEstimatedPowerConsumption Double value of the total estimated power consumption
	 * @param totalMeasuredPowerConsumption  Double value of the total measured power consumption
	 */
	void adjustAllPowerConsumptions(
		final Double totalEstimatedPowerConsumption,
		final Double totalMeasuredPowerConsumption
	) {
		final String hostname = telemetryManager.getHostname();

		if (totalEstimatedPowerConsumption == null) {
			log.debug(
				"Hostname {} - No power consumption estimated for the monitored devices. Skip power consumption adjustment.",
				hostname
			);
			return;
		}

		// Browse through all the collected objects and perform the adjustment of estimated powers
		final Stream<Monitor> monitorStream = telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.filter(monitor -> !KnownMonitorType.HOST.getKey().equals(monitor.getType())) // We already sum the values for the host
			.filter(monitor -> !KnownMonitorType.ENCLOSURE.getKey().equals(monitor.getType())) // Skip the enclosure
			.filter(monitor -> !KnownMonitorType.VM.getKey().equals(monitor.getType())); // Skip VM monitors as their power is already computed based on the host's power

		if (totalMeasuredPowerConsumption == null) {
			// Let's try next collect
			log.debug(
				"Hostname {} - The measured power consumption is absent." +
				" An attempt to estimate the monitors power consumption will be made during the next collect.",
				hostname
			);

			// Clear the estimated power consumption since the total measured power is not yet available.
			// This will avoid the possible estimated energy gap due to the connector that collects Energy instead of Power Consumption
			// because using the adjustment approach, on each estimated device, the first energy will be calculated based
			// on the first non-adjusted power and the second adjusted power and from collect to collect this energy gap will persist.

			monitorStream.forEach(monitor -> {
				final Map<String, AbstractMetric> metrics = monitor.getMetrics();
				final String powerMetricName = HwCollectHelper.generatePowerMetricNameForMonitorType(monitor.getType());
				final String energyMetricName = HwCollectHelper.generateEnergyMetricNameForMonitorType(monitor.getType());
				metrics.remove(powerMetricName);
				metrics.remove(energyMetricName);
			});

			return;
		}
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		monitorStream.forEach(monitor -> {
			final String powerMetricName = HwCollectHelper.generatePowerMetricNameForMonitorType(monitor.getType());
			final String energyMetricName = HwCollectHelper.generateEnergyMetricNameForMonitorType(monitor.getType());
			final Double powerMetricValue = CollectHelper.getNumberMetricValue(monitor, powerMetricName, false);

			// No estimated power? skip the computation
			if (powerMetricValue == null) {
				return;
			}

			final Double adjustedPowerValue = getAdjustedPowerConsumption(
				powerMetricValue,
				totalEstimatedPowerConsumption,
				totalMeasuredPowerConsumption
			);

			// Collect adjusted power metric
			metricFactory.collectNumberMetric(
				monitor,
				powerMetricName,
				adjustedPowerValue,
				telemetryManager.getStrategyTime()
			);

			final Double adjustedEnergyValue = HwCollectHelper.estimateEnergyUsingPower(
				monitor,
				telemetryManager,
				adjustedPowerValue,
				powerMetricName,
				energyMetricName,
				telemetryManager.getStrategyTime()
			);

			if (adjustedEnergyValue != null) {
				// Collect adjusted energy metric
				metricFactory.collectNumberMetric(
					monitor,
					energyMetricName,
					adjustedEnergyValue,
					telemetryManager.getStrategyTime()
				);
			}
		});
	}

	/**
	 * This method adjusts the power consumption metric value
	 * @param estimatedPowerConsumption the estimated power consumption
	 * @param totalEstimatedPowerConsumption the total estimated power consumption
	 * @param totalMeasuredPowerConsumption the total measured power consumption
	 * @return the adjusted power consumption of type Double
	 */
	Double getAdjustedPowerConsumption(
		final Double estimatedPowerConsumption,
		final Double totalEstimatedPowerConsumption,
		final Double totalMeasuredPowerConsumption
	) {
		return NumberHelper.round(
			(estimatedPowerConsumption / totalEstimatedPowerConsumption) * totalMeasuredPowerConsumption,
			2,
			RoundingMode.HALF_UP
		);
	}

	/**
	 * Setting the host power consumption value as the sum of all the Enclosure power consumption values.
	 * @param enclosureMonitors monitors of type {@link KnownMonitorType} = ENCLOSURE
	 * @return the sum of all enclosures power consumptions
	 */
	Double sumEnclosurePowerConsumptions(@NonNull final Map<String, Monitor> enclosureMonitors) {
		final String hostname = telemetryManager.getHostname();

		// Getting the sums of the enclosures' power consumption values
		Double totalPowerConsumption = enclosureMonitors
			.values()
			.stream()
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.map(monitor -> CollectHelper.getUpdatedNumberMetricValue(monitor, HW_ENCLOSURE_POWER))
			.filter(Objects::nonNull)
			.reduce(Double::sum)
			.orElse(null);

		if (totalPowerConsumption == null) {
			// Let's try next collect
			log.debug("Hostname {} - The power consumption is going to be collected during the next collect.", hostname);
			return null;
		}

		return totalPowerConsumption;
	}

	/**
	 * Perform the sum of all monitor's power consumption
	 *
	 * @return {@link Double} value. <code>null</code> if the power cannot be collected.
	 */
	Double sumEstimatedPowerConsumptions() {
		// Browse through all the collected objects and perform the sum of parameters using the map-reduce
		return telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.filter(monitor -> !KnownMonitorType.HOST.getKey().equals(monitor.getType())) // We already sum the values for the host
			.filter(monitor -> !KnownMonitorType.ENCLOSURE.getKey().equals(monitor.getType())) // Skip the enclosure
			.filter(monitor -> !KnownMonitorType.VM.getKey().equals(monitor.getType())) // Skip VM monitors as their power is already computed based on the host's power
			.map(monitor ->
				CollectHelper.getNumberMetricValue(
					monitor,
					HwCollectHelper.generatePowerMetricNameForMonitorType(monitor.getType()),
					false
				)
			)
			.filter(Objects::nonNull) // skip null power consumption values
			.reduce(Double::sum)
			.orElse(null);
	}

	/**
	 * Estimate the host power consumption.<br> Collects the power consumption and energy.
	 * The estimated total power consumption value is divided by 0.9 to add 10% to the final value
	 * so that we take into account the power supplies' heat dissipation (90% efficiency assumed)
	 * @param totalEstimatedPowerConsumption Sum estimated power consumptions
	 * @return the estimated power consumption of the host
	 */
	Double estimateHostPowerConsumption(final Double totalEstimatedPowerConsumption) {
		final String hostname = telemetryManager.getHostname();

		if (totalEstimatedPowerConsumption == null) {
			log.debug("Hostname {} - No power consumption estimated for the monitored devices.", hostname);
			return null;
		}

		// Add 10% because of the heat dissipation of the power supplies
		final double powerConsumptionValue = NumberHelper.round(
			totalEstimatedPowerConsumption / 0.9,
			2,
			RoundingMode.HALF_UP
		);

		if (powerConsumptionValue > 0) {
			log.debug("Hostname {} - Power Consumption: Estimated at {} Watts.", hostname, powerConsumptionValue);
		} else {
			log.warn(
				"Hostname {} - Power Consumption could not be estimated. Negative value: {}.",
				hostname,
				powerConsumptionValue
			);
		}

		return powerConsumptionValue;
	}
}
