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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.strategy.utils.MathOperationsHelper;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.hardware.constants.CommonConstants;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HwCollectHelper {

	// Create a lookup table that maps monitor type (lowercase) to the corresponding build method.
	private static final Map<String, BiFunction<Monitor, TelemetryManager, String>> MONITOR_NAME_BUILDERS =
		new HashMap<>();

	static {
		MONITOR_NAME_BUILDERS.put("cpu", (monitor, telemetry) -> MonitorNameBuilder.buildCpuName(monitor));
		MONITOR_NAME_BUILDERS.put("memory", (monitor, telemetry) -> MonitorNameBuilder.buildMemoryName(monitor));
		MONITOR_NAME_BUILDERS.put(
			"physical_disk",
			(monitor, telemetry) -> MonitorNameBuilder.buildPhysicalDiskName(monitor)
		);
		MONITOR_NAME_BUILDERS.put("logical_disk", (monitor, telemetry) -> MonitorNameBuilder.buildLogicalDiskName(monitor));
		MONITOR_NAME_BUILDERS.put(
			"disk_controller",
			(monitor, telemetry) -> MonitorNameBuilder.buildDiskControllerName(monitor)
		);
		MONITOR_NAME_BUILDERS.put("network", (monitor, telemetry) -> MonitorNameBuilder.buildNetworkCardName(monitor));
		MONITOR_NAME_BUILDERS.put("fan", (monitor, telemetry) -> MonitorNameBuilder.buildFanName(monitor));
		MONITOR_NAME_BUILDERS.put("power_supply", (monitor, telemetry) -> MonitorNameBuilder.buildPowerSupplyName(monitor));
		MONITOR_NAME_BUILDERS.put("temperature", (monitor, telemetry) -> MonitorNameBuilder.buildTemperatureName(monitor));
		MONITOR_NAME_BUILDERS.put("tape_drive", (monitor, telemetry) -> MonitorNameBuilder.buildTapeDriveName(monitor));
		MONITOR_NAME_BUILDERS.put("robotics", (monitor, telemetry) -> MonitorNameBuilder.buildRoboticsName(monitor));
		// Note: 'enclosure' requires a telemetryManager, so we pass it into the method.
		MONITOR_NAME_BUILDERS.put(
			"enclosure",
			(monitor, telemetry) -> MonitorNameBuilder.buildEnclosureName(telemetry, monitor)
		);
		MONITOR_NAME_BUILDERS.put("vm", (monitor, telemetry) -> MonitorNameBuilder.buildVmName(monitor));
		MONITOR_NAME_BUILDERS.put("voltage", (monitor, telemetry) -> MonitorNameBuilder.buildVoltageName(monitor));
		MONITOR_NAME_BUILDERS.put("blade", (monitor, telemetry) -> MonitorNameBuilder.buildBladeName(monitor));
		MONITOR_NAME_BUILDERS.put("gpu", (monitor, telemetry) -> MonitorNameBuilder.buildGpuName(monitor));
		MONITOR_NAME_BUILDERS.put("battery", (monitor, telemetry) -> MonitorNameBuilder.buildBatteryName(monitor));
		MONITOR_NAME_BUILDERS.put("led", (monitor, telemetry) -> MonitorNameBuilder.buildLedName(monitor));
		MONITOR_NAME_BUILDERS.put("lun", (monitor, telemetry) -> MonitorNameBuilder.buildLunName(monitor));
		MONITOR_NAME_BUILDERS.put("other_device", (monitor, telemetry) -> MonitorNameBuilder.buildOtherDeviceName(monitor));
	}

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

		final ConnectorIdentity connectorIdentity = connector.getConnectorIdentity();
		final Detection detection = connectorIdentity != null ? connectorIdentity.getDetection() : null;
		final Set<String> connectorTags = detection != null ? detection.getTags() : null;
		return connectorTags != null && connectorTags.stream().anyMatch(tag -> tag.equalsIgnoreCase("hardware"));
	}

	/**
	 * Builds the monitor name using the monitor type. The method looks up the correct build function
	 * based on the monitor's type (normalized to lowercase) and applies it; if no matching type is found,
	 * it returns the monitor type as is.
	 *
	 * @param monitor the {@link Monitor} instance
	 * @param telemetryManager the {@link TelemetryManager} instance, required for some build functions
	 * @return the built monitor name, or the monitor type if no match is found.
	 */
	public static String buildMonitorNameUsingType(final Monitor monitor, final TelemetryManager telemetryManager) {
		if (monitor == null) {
			throw new IllegalArgumentException("Monitor cannot be null");
		}
		String type = monitor.getType();
		if (type == null) {
			return null;
		}
		// Normalize type to lowercase
		BiFunction<Monitor, TelemetryManager, String> builder = MONITOR_NAME_BUILDERS.get(type.toLowerCase());
		return builder != null ? builder.apply(monitor, telemetryManager) : type;
	}

	public static String md5Hex(String input) {
		try {
			// Get an instance of the MD5 message digest
			MessageDigest md = MessageDigest.getInstance("MD5");
			// Compute the digest, converting the input string to bytes using UTF-8 encoding
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			// Convert the digest bytes to a hexadecimal string using HexFormat (available since Java 17)
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			// MD5 should always be available in Java implementations
			throw new RuntimeException("MD5 algorithm not available.", e);
		}
	}
}
