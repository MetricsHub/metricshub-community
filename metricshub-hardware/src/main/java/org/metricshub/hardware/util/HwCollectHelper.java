package org.metricshub.hardware.util;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.hardware.constants.CommonConstants.PRESENT_STATUS;
import static org.metricshub.hardware.constants.VmConstants.HW_VM_POWER_SHARE_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_VM_POWER_STATE_METRIC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.strategy.utils.MathOperationsHelper;
import org.metricshub.engine.strategy.utils.StrategyHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.hardware.constants.CommonConstants;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HwCollectHelper {

	/**
	 * Check if the given value is a valid positive
	 *
	 * @param value The {@link Double} value to check
	 * @return <code>true</code> if the value is not null and greater than equals 0
	 */
	public static boolean isValidPositive(final Double value) {
		return value != null && value >= 0;
	}

	/**
	 * Check if the given ratio value is not null and greater than or equals 0 and less than or equals 1
	 *
	 * @param ratio The ratio value to check
	 * @return boolean value, <code>true</code> if the ratio is valid otherwise <code>false</code>
	 */
	public static boolean isValidRatio(final Double ratio) {
		return ratio != null && ratio >= 0 && ratio <= 1;
	}

	/**
	 * Estimates energy consumption using power consumption estimated value
	 * @param monitor the monitor to collect
	 * @param telemetryManager the telemetry manager
	 * @param estimatedPower the previously estimated power consumption
	 * @param powerMetricName the metricName to estimate e.g: hw.power{hw.type="fan"} or hw.power{hw.type="fan"}
	 * @param energyMetricName the metricName to estimate e.g: hw.energy{hw.type="fan"} or hw.energy{hw.type="fan"}
	 * @param collectTime the current collect time in milliseconds
	 * @return estimated Double energy value
	 */
	public static Double estimateEnergyUsingPower(
		final Monitor monitor,
		final TelemetryManager telemetryManager,
		final Double estimatedPower,
		final String powerMetricName,
		final String energyMetricName,
		final Long collectTime
	) {
		final String hostname = telemetryManager.getHostname();

		final Double collectTimePrevious = CollectHelper.getNumberMetricCollectTime(monitor, powerMetricName, true);

		final Double deltaTimeMs = MathOperationsHelper.subtract(
			powerMetricName,
			Double.valueOf(collectTime),
			collectTimePrevious,
			hostname
		);

		// Convert deltaTimeMs from milliseconds (ms) to seconds
		final Double deltaTime = deltaTimeMs != null ? deltaTimeMs / 1000.0 : null;

		// Calculate the energy usage over time. e.g from Power Consumption: E = P * T
		final Double energyUsage = MathOperationsHelper.multiply(powerMetricName, estimatedPower, deltaTime, hostname);

		if (energyUsage != null) {
			// The counter will start from the energy usage
			Double energy = energyUsage;

			// The previous counter is needed to make a sum with the delta counter value on this collect
			final Double previousEnergy = CollectHelper.getNumberMetricValue(monitor, energyMetricName, true);

			// Ok, we have the previous counter value ? sum the previous counter and the current delta counter
			if (previousEnergy != null) {
				energy += previousEnergy;
			}

			// Everything is good return the counter metric
			return energy;
		} else {
			log.debug(
				"Hostname {} - Cannot calculate energy {} for monitor {}. Current raw value {} - Current time {} - Previous time {}.",
				hostname,
				energyMetricName,
				monitor.getId(),
				estimatedPower,
				collectTime,
				collectTimePrevious
			);
		}
		return null;
	}

	/**
	 * Calculate a rate per second for the given metric between the current collect and the previous collect
	 * @param monitor           The monitor from which to retrieve the metric value
	 * @param counterMetricName The name of the counter metric we want to calculate the rate from
	 * @param rateMetricName    The name of the rate metric we are caculating
	 * @param hostname          The hostname
	 * @return the calculated rate
	 */
	public static Double calculateMetricRatePerSecond(
		final Monitor monitor,
		final String counterMetricName,
		final String rateMetricName,
		final String hostname
	) {
		final Double value = CollectHelper.getNumberMetricValue(monitor, counterMetricName, false);
		final Double previousValue = CollectHelper.getNumberMetricValue(monitor, counterMetricName, true);
		final Double collectTime = CollectHelper.getNumberMetricCollectTime(monitor, counterMetricName, false);
		final Double previousCollectTime = CollectHelper.getNumberMetricCollectTime(monitor, counterMetricName, true);

		return Optional
			.ofNullable(
				MathOperationsHelper.rate(rateMetricName, value, previousValue, collectTime, previousCollectTime, hostname)
			)
			.map(rate -> rate * 1000.0) // Convert rate from per millisecond to per second
			.orElse(null);
	}

	/**
	 * Generates the corresponding power metric name base on monitor type
	 * @param monitorType the type of the monitor
	 * @return power metric's name  e.g: hw.power{hw.type="network"} (General format is: hw.power{hw.type="&lt;type&gt;"})
	 */
	public static String generatePowerMetricNameForMonitorType(final String monitorType) {
		return "hw.power{hw.type=\"" + monitorType + "\"}";
	}

	/**
	 * Generates the corresponding energy metric name base on monitor type
	 * @param monitorType the type of the monitor
	 * @return energy metric's name  e.g: hw.energy{hw.type="network"} (General format is: hw.energy{hw.type="&lt;type&gt;"})
	 */
	public static String generateEnergyMetricNameForMonitorType(final String monitorType) {
		return "hw.energy{hw.type=\"" + monitorType + "\"}";
	}

	/**
	 * Get the VM's power share which is assumed not null and >= 0.0
	 *
	 * @param vm VM {@link Monitor} instance
	 * @return Double value. Returns 0.0 if the power share is null or less than 0.0 or the VM is not online
	 */
	public static Double getVmPowerShare(Monitor vm) {
		if (!isVmOnline(vm)) {
			return 0.0;
		}

		final Double powerShare = CollectHelper.getNumberMetricValue(vm, HW_VM_POWER_SHARE_METRIC, false);
		if (powerShare != null && powerShare >= 0.0) {
			return powerShare;
		}

		return 0.0;
	}

	/**
	 * @param vm	The VM whose online status should be determined.
	 *
	 * @return		Whether the given VM is online.
	 */
	private static boolean isVmOnline(Monitor vm) {
		return "on".equals(CollectHelper.getStateSetMetricValue(vm, HW_VM_POWER_STATE_METRIC, false));
	}

	/**
	 * Checks whether the current monitor has the metric {@link CommonConstants#PRESENT_STATUS}
	 * @param monitor A given monitor
	 * @return true or false
	 */

	static boolean hasPresentMetric(final Monitor monitor) {
		return monitor.getMetrics().containsKey(String.format(PRESENT_STATUS, monitor.getType()));
	}

	/**
	 * Checks if a given monitor is missing or not. Missing means the present value is 0.
	 * If the monitor is not eligible to missing devices then it can never be missing.
	 * @param monitor A given monitor
	 * @return <code>true</code> if the monitor is missing otherwise <code>false</code>
	 */
	public static boolean isMissing(final Monitor monitor) {
		if (!hasPresentMetric(monitor)) {
			return false;
		}

		final NumberMetric presentMetric = monitor.getMetric(
			String.format(PRESENT_STATUS, monitor.getType()),
			NumberMetric.class
		);
		final Double present = presentMetric != null ? presentMetric.getValue() : null;

		return Double.valueOf(0).equals(present);
	}

	/**
	 * Checks if the connector associated with the provided monitor has a "hardware" tag.
	 *
	 * @param monitor the monitor containing the connector ID attribute
	 * @param telemetryManager the telemetry manager containing the connector store
	 * @return true if the connector has a "hardware" tag, false otherwise
	 */
	public static boolean connectorHasHardwareTag(final Monitor monitor, final TelemetryManager telemetryManager) {
		if (monitor == null) {
			return false;
		}
		final ConnectorStore telemetryManagerConnectorStore = telemetryManager.getConnectorStore();
		if (telemetryManagerConnectorStore == null) {
			log.error("Hostname {} - ConnectorStore does not exist.", telemetryManager.getHostname());
			return false;
		}

		final Map<String, Connector> store = telemetryManagerConnectorStore.getStore();

		if (store == null) {
			log.error("Hostname {} - ConnectorStore store does not exist.", telemetryManager.getHostname());
			return false;
		}

		final String connectorId = monitor.getAttribute(MONITOR_ATTRIBUTE_CONNECTOR_ID);

		if (connectorId == null) {
			log.error(
				"Hostname {} - Monitor {} connector_id attribute does not exist.",
				telemetryManager.getHostname(),
				monitor.getId()
			);
			return false;
		}

		final Connector connector = store.get(connectorId);

		if (connector == null) {
			log.error(
				"Hostname {} - Monitor {} connector_id attribute does not correspond to any valid connector id.",
				telemetryManager.getHostname(),
				monitor.getId()
			);
			return false;
		}

		return StrategyHelper.isHardwareConnector(connector);
	}

	/**
	 * Whether a metric with a given metricNamePrefix is collected or not for the given monitor.
	 *
	 * @param monitor The monitor instance where the metric is collected.
	 * @param metricNamePrefix The prefix of the metric name to check for.
	 * @return true if a metric with a given metricNamePrefix is collected, false otherwise.
	 */
	public static boolean isMetricCollected(final Monitor monitor, final String metricNamePrefix) {
		return monitor
			.getMetrics()
			.values()
			.stream()
			.anyMatch(metric -> {
				// Extract the metric name prefix
				final String currentMetricNamePrefix = MetricFactory.extractName(metric.getName());
				final Map<String, String> metricAttributes = metric.getAttributes();
				// CHECKSTYLE:OFF
				return (
					metricNamePrefix.equals(currentMetricNamePrefix) &&
					(!metricAttributes.containsKey("hw.type") || monitor.getType().equals(metricAttributes.get("hw.type"))) &&
					metric.isUpdated()
				);
				// CHECKSTYLE:ON
			});
	}

	/**
	 * Get the metric from the monitor by metric name prefix and attributes
	 * @param hostname         The hostname of the monitor
	 * @param monitor          The monitor instance where the metric is collected
	 * @param metricNamePrefix The metric name prefix. E.g 'hw.errors.limit'
	 * @param metricAttributes A key value pair of attributes to be matched with the metric attributes
	 * @return Optional of the metric if found, otherwise an empty Optional
	 */
	public static Optional<NumberMetric> findMetricByNamePrefixAndAttributes(
		@NonNull String hostname,
		@NonNull final Monitor monitor,
		@NonNull final String metricNamePrefix,
		@NonNull final Map<String, String> metricAttributes
	) {
		// Get the metric from the monitor by metric name prefix and attributes
		// This atomic integer is used to log a warning if multiple metrics are found with the same prefix and attributes
		final AtomicInteger count = new AtomicInteger(0);
		return monitor
			.getMetrics()
			.values()
			.stream()
			.filter(metric -> {
				// Extract the metric name prefix and check if the metric attributes are contained in the given attributes
				final boolean result =
					metric.isUpdated() &&
					metricNamePrefix.equals(MetricFactory.extractName(metric.getName())) &&
					containsAllEntries(metric.getAttributes(), metricAttributes);

				// Log a warning if multiple metrics are found with the same prefix and attributes
				if (result && count.incrementAndGet() > 1) {
					log.warn(
						"Hostname {} - Multiple metrics found for the same prefix {} and attributes: {}",
						hostname,
						metricNamePrefix,
						metricAttributes
					);
				}
				return result;
			})
			.map(NumberMetric.class::cast)
			.findFirst();
	}

	/**
	 * Checks if all entries of the second map are contained in the first map.
	 * This method iterates through all entries of the second map and checks if each entry is present
	 * in the first map with the same key and value.
	 *
	 * @param firstMap  the map to be checked for containing all entries of the second map
	 * @param secondMap the map whose entries are to be checked against the first map
	 * @return {@code true} if all entries of the second map are contained in the first map,
	 * {@code false} otherwise
	 */
	public static boolean containsAllEntries(Map<String, String> firstMap, Map<String, String> secondMap) {
		// Checks if the second map entries are all contained within the first map
		return secondMap
			.entrySet()
			.stream()
			.allMatch(entry -> firstMap.containsKey(entry.getKey()) && firstMap.get(entry.getKey()).equals(entry.getValue()));
	}
}
