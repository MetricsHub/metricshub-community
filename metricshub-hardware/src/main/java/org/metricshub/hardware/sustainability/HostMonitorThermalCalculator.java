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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.constants.CpuConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;

import java.math.RoundingMode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ArrayHelper;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.strategy.utils.MathOperationsHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.constants.CpuConstants;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class HostMonitorThermalCalculator {

	private TelemetryManager telemetryManager;
	private static final String TEMPERATURE_METRIC = "hw.temperature";
	private static final String IS_CPU_SENSOR = "__is_cpu_sensor";
	private static final String HW_HOST_AVERAGE_CPU_TEMPERATURE = "__hw.host.average_cpu_temperature";
	private static final String HW_HOST_AMBIENT_TEMPERATURE = "hw.host.ambient_temperature";
	private static final String HW_HOST_HEATING_MARGIN = "hw.host.heating_margin";
	private static final String TEMPERATURE_WARNING_THRESHOLD = "hw.temperature.limit{limit_type=\"high.degraded\"}";
	private static final String TEMPERATURE_ALARM_THRESHOLD = "hw.temperature.limit{limit_type=\"high.critical\"}";
	private static final String HW_HOST_AVERAGE_CPU_WARNING_THRESHOLD = "__hw.host.average_warning_threshold";

	/**
	 * Compute temperature metrics for the current host monitor:
	 * <ul>
	 * <li><b>{@value #HW_HOST_AMBIENT_TEMPERATURE}</b> the minimum temperature between 5 and 100 degrees Celsius</li>
	 * <li><b>{@value #HW_HOST_AVERAGE_CPU_TEMPERATURE}</b>: the average CPU temperatures</li>
	 * <li><b>{@link CpuConstants#HW_HOST_CPU_THERMAL_DISSIPATION_RATE }</b>: the heat dissipation rate of the
	 * processors (as a fraction of the maximum heat/power they can emit)</li>
	 * </ul>
	 */
	public void computeHostTemperatureMetrics() {
		final Map<String, Monitor> temperatureMonitors = telemetryManager.findMonitorsByType(
			KnownMonitorType.TEMPERATURE.getKey()
		);

		// No temperatures then no computation
		if (temperatureMonitors == null || temperatureMonitors.isEmpty()) {
			log.debug(
				"Hostname {} - Could not compute temperature metrics ({}, {}, {})",
				telemetryManager.getHostname(),
				HW_HOST_AVERAGE_CPU_TEMPERATURE,
				HW_HOST_CPU_THERMAL_DISSIPATION_RATE,
				HW_HOST_CPU_THERMAL_DISSIPATION_RATE
			);
			return;
		}

		double ambientTemperature = 35.0;
		double cpuTemperatureAverage = 0;
		double cpuTemperatureCount = 0;
		double cpuWarningThresholdAverage = 0;
		double cpuWarningThresholdCount = 0;
		Double heatingMargin = null;

		// Loop over all the temperature monitors to compute the ambient temperature, cpuTemperatureCount and cpuTemperatureAverage
		for (final Monitor temperatureMonitor : temperatureMonitors.values()) {
			// Get the temperature value
			final Double temperature = CollectHelper.getUpdatedNumberMetricValue(temperatureMonitor, TEMPERATURE_METRIC);

			// If there is no temperature value, no need to continue process this monitor
			if (temperature == null) {
				continue;
			}

			// Is this the ambient temperature? (which should be the lowest measured temperature... except if it's less than 5°)
			if (temperature < ambientTemperature && temperature > 5) {
				ambientTemperature = temperature;
			}
			final Double warningThreshold = CollectHelper.getNumberMetricValue(
				temperatureMonitor,
				TEMPERATURE_WARNING_THRESHOLD,
				false
			);

			// Get the isCpuSensor flag
			boolean isCpuSensor = false;
			final String isCpuSensorString = temperatureMonitor.getAttribute(IS_CPU_SENSOR);
			if (isCpuSensorString == null) {
				isCpuSensor =
					isCpuSensor(
						warningThreshold,
						temperatureMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME),
						temperatureMonitor.getAttribute("info")
					);
				temperatureMonitor.addAttribute(IS_CPU_SENSOR, String.valueOf(isCpuSensor));
			} else {
				isCpuSensor = Boolean.parseBoolean(isCpuSensorString);
			}

			// Is this a CPU sensor?
			if (isCpuSensor && temperature > 5) {
				cpuTemperatureAverage += temperature;
				cpuTemperatureCount++;
			}

			// Computation of heating margin
			final Double temperatureWarningThreshold = getTemperatureWarningThreshold(
				warningThreshold,
				CollectHelper.getNumberMetricValue(temperatureMonitor, TEMPERATURE_ALARM_THRESHOLD, false)
			);

			if (temperatureWarningThreshold != null) {
				heatingMargin =
					MathOperationsHelper.min(heatingMargin, Math.max(temperatureWarningThreshold - temperature, 0.0));

				cpuWarningThresholdAverage += temperatureWarningThreshold;
				cpuWarningThresholdCount++;
			}
		}

		// Find the host monitor
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());

		// Sets the host ambient temperature as the minimum of all temperature sensors
		if (ambientTemperature < 35) {
			// Update the metric
			metricFactory.collectNumberMetric(
				hostMonitor,
				HW_HOST_AMBIENT_TEMPERATURE,
				ambientTemperature,
				telemetryManager.getStrategyTime()
			);
		}

		// Sets the average CPU temperature (to estimate the heat dissipation of the processors)
		if (cpuTemperatureCount > 0) {
			// Compute the average
			cpuTemperatureAverage /= cpuTemperatureCount;

			cpuTemperatureAverage = NumberHelper.round(cpuTemperatureAverage, 2, RoundingMode.HALF_UP);

			metricFactory.collectNumberMetric(
				hostMonitor,
				HW_HOST_AVERAGE_CPU_TEMPERATURE,
				cpuTemperatureAverage,
				telemetryManager.getStrategyTime()
			);

			if (cpuWarningThresholdCount > 0) {
				cpuWarningThresholdAverage /= cpuWarningThresholdCount;

				cpuWarningThresholdAverage = NumberHelper.round(cpuWarningThresholdAverage, 2, RoundingMode.HALF_UP);

				metricFactory.collectNumberMetric(
					hostMonitor,
					HW_HOST_AVERAGE_CPU_WARNING_THRESHOLD,
					cpuWarningThresholdAverage,
					telemetryManager.getStrategyTime()
				);
			}

			// Calculate the dissipation rate
			computeHostThermalDissipationRate(
				hostMonitor,
				ambientTemperature,
				cpuTemperatureAverage,
				cpuWarningThresholdAverage
			);
		}

		if (heatingMargin != null) {
			metricFactory.collectNumberMetric(
				hostMonitor,
				HW_HOST_HEATING_MARGIN,
				heatingMargin,
				telemetryManager.getStrategyTime()
			);
		}
	}

	/**
	 * Calculate the heat dissipation rate of the processors (as a fraction of the maximum heat/power they can emit).
	 *
	 * @param hostMonitor                The host monitor we wish to update its heat dissipation rate
	 * @param ambientTemperature         The ambient temperature of the host
	 * @param cpuTemperatureAverage      The CPU average temperature previously computed based on the cpu sensor count
	 * @param cpuWarningThresholdAverage The CPU average warning threshold previously computed based on the cpu warning threshold count
	 */
	void computeHostThermalDissipationRate(
		final Monitor hostMonitor,
		final double ambientTemperature,
		final double cpuTemperatureAverage,
		final double cpuWarningThresholdAverage
	) {
		// Get the average CPU temperature computed at the discovery level
		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		final double ambientToWarningDifference = cpuWarningThresholdAverage - ambientTemperature;

		// Avoid the arithmetic exception
		if (ambientToWarningDifference != 0.0) {
			double cpuThermalDissipationRate = (cpuTemperatureAverage - ambientTemperature) / ambientToWarningDifference;

			// Do we have a consistent fraction
			if (cpuThermalDissipationRate >= 0 && cpuThermalDissipationRate <= 1) {
				cpuThermalDissipationRate = NumberHelper.round(cpuThermalDissipationRate, 2, RoundingMode.HALF_UP);
				metricFactory.collectNumberMetric(
					hostMonitor,
					HW_HOST_CPU_THERMAL_DISSIPATION_RATE,
					cpuThermalDissipationRate,
					telemetryManager.getStrategyTime()
				);
			}
		}
	}

	/**
	 * Check if the given information match a CPU sensor
	 *
	 * @param warningThreshold The warning threshold previously computed
	 * @param data             The string data to check
	 * @return <code>true</code> the warning threshold is greater than 10 degrees and the data contains "cpu" or "proc" otherwise
	 *         <code>false</code>
	 */
	static boolean isCpuSensor(final Double warningThreshold, final String... data) {
		// CHECKSTYLE:OFF
		return (
			warningThreshold != null &&
			warningThreshold > 10 &&
			data != null &&
			ArrayHelper.anyMatchLowerCase(HostMonitorThermalCalculator::matchesCpuSensor, data)
		);
		// CHECKSTYLE:ON
	}

	/**
	 * Check whether the given value matches a CPU sensor
	 *
	 * @param value string value to check
	 * @return <code>true</code> if the given value matches "cpu" or "proc" otherwise <code>false</code>
	 */
	static boolean matchesCpuSensor(final String value) {
		return value.contains("cpu") || value.contains("proc");
	}

	/**
	 * Get the temperature threshold value from the given metadata map
	 * @param warningThreshold the warning threshold
	 * @param alarmThreshold the alarm threshold
	 * @return temperature warning threshold of type Double
	 */
	private Double getTemperatureWarningThreshold(final Double warningThreshold, final Double alarmThreshold) {
		// If we only have an alarm threshold, then warningThreshold will be 90% of alarmThreshold
		// If we only have a warning threshold, we are good.
		// If we have both warning and alarm threshold then we return the minimum value
		if (warningThreshold == null && alarmThreshold != null) {
			return NumberHelper.round(alarmThreshold * 0.9, 1, RoundingMode.HALF_UP);
		}
		// return the minimum between warning and alarm
		return MathOperationsHelper.min(warningThreshold, alarmThreshold);
	}
}
