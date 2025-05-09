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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;
import static org.metricshub.hardware.constants.BatteryConstants.BATTERY_TRIM_PATTERN;
import static org.metricshub.hardware.constants.BatteryConstants.BATTERY_TYPE;
import static org.metricshub.hardware.constants.BladeConstants.BLADE_NAME;
import static org.metricshub.hardware.constants.BladeConstants.BLADE_TRIM_PATTERN;
import static org.metricshub.hardware.constants.CommonConstants.DEVICE_ID;
import static org.metricshub.hardware.constants.CommonConstants.DISPLAY_ID;
import static org.metricshub.hardware.constants.CommonConstants.ENCLOSURE;
import static org.metricshub.hardware.constants.CommonConstants.ID_COUNT;
import static org.metricshub.hardware.constants.CommonConstants.ID_MAX_LENGTH;
import static org.metricshub.hardware.constants.CommonConstants.LOCALHOST;
import static org.metricshub.hardware.constants.CommonConstants.LOCATION;
import static org.metricshub.hardware.constants.CommonConstants.MODEL;
import static org.metricshub.hardware.constants.CommonConstants.VENDOR;
import static org.metricshub.hardware.constants.CommonConstants.WHITE_SPACE_REPEAT_REGEX;
import static org.metricshub.hardware.constants.CpuConstants.CPU_TRIM_PATTERN;
import static org.metricshub.hardware.constants.DiskControllerConstants.DISK_CONTROLLER_NUMBER;
import static org.metricshub.hardware.constants.DiskControllerConstants.DISK_CONTROLLER_TRIM_PATTERN;
import static org.metricshub.hardware.constants.EnclosureConstants.BLADE_ENCLOSURE;
import static org.metricshub.hardware.constants.EnclosureConstants.COMPUTER;
import static org.metricshub.hardware.constants.EnclosureConstants.ENCLOSURE_TYPE;
import static org.metricshub.hardware.constants.EnclosureConstants.STORAGE;
import static org.metricshub.hardware.constants.EnclosureConstants.SWITCH;
import static org.metricshub.hardware.constants.FanConstants.FAN_SENSOR_LOCATION;
import static org.metricshub.hardware.constants.FanConstants.FAN_TRIM_PATTERN;
import static org.metricshub.hardware.constants.GpuConstants.GPU_MEMORY_LIMIT_METRIC;
import static org.metricshub.hardware.constants.GpuConstants.GPU_TRIM_PATTERN;
import static org.metricshub.hardware.constants.LedConstants.COLOR;
import static org.metricshub.hardware.constants.LedConstants.LED_TRIM_PATTERN;
import static org.metricshub.hardware.constants.LedConstants.NAME;
import static org.metricshub.hardware.constants.LogicalDiskConstants.LOGICAL_DISK_SIZE;
import static org.metricshub.hardware.constants.LogicalDiskConstants.LOGICAL_DISK_TRIM_PATTERN;
import static org.metricshub.hardware.constants.LogicalDiskConstants.LOGICAL_DISK_TYPE;
import static org.metricshub.hardware.constants.LogicalDiskConstants.RAID_LEVEL;
import static org.metricshub.hardware.constants.LunConstants.LOCAL_DEVICE_NAME;
import static org.metricshub.hardware.constants.LunConstants.LUN_TRIM_PATTERN;
import static org.metricshub.hardware.constants.LunConstants.REMOTE_DEVICE_NAME;
import static org.metricshub.hardware.constants.MemoryConstants.MEMORY_SIZE_METRIC;
import static org.metricshub.hardware.constants.MemoryConstants.MEMORY_TRIM_PATTERN;
import static org.metricshub.hardware.constants.MemoryConstants.MEMORY_TYPE;
import static org.metricshub.hardware.constants.NetworkConstants.NETWORK_CARD_TRIM_PATTERN;
import static org.metricshub.hardware.constants.NetworkConstants.NETWORK_DEVICE_TYPE;
import static org.metricshub.hardware.constants.NetworkConstants.NETWORK_VENDOR_MODEL_TRIM_PATTERN;
import static org.metricshub.hardware.constants.OtherDeviceConstants.ADDITIONAL_LABEL;
import static org.metricshub.hardware.constants.OtherDeviceConstants.OTHER_DEVICE_TYPE;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.PHYSICAL_DISK_DEVICE_TYPE;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.PHYSICAL_DISK_SIZE_METRIC;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.PHYSICAL_DISK_TRIM_PATTERN;
import static org.metricshub.hardware.constants.PowerSupplyConstants.POWER_SUPPLY_LIMIT_METRIC;
import static org.metricshub.hardware.constants.PowerSupplyConstants.POWER_SUPPLY_TRIM_PATTERN;
import static org.metricshub.hardware.constants.PowerSupplyConstants.POWER_SUPPLY_TYPE;
import static org.metricshub.hardware.constants.RoboticsConstants.ROBOTICS_TRIM_PATTERN;
import static org.metricshub.hardware.constants.RoboticsConstants.ROBOTIC_TYPE;
import static org.metricshub.hardware.constants.TapeDriveConstants.TAPE_DRIVE_TRIM_PATTERN;
import static org.metricshub.hardware.constants.TemperatureConstants.TEMPERATURE_SENSOR_LOCATION;
import static org.metricshub.hardware.constants.TemperatureConstants.TEMPERATURE_TRIM_PATTERN;
import static org.metricshub.hardware.constants.VmConstants.HOSTNAME;
import static org.metricshub.hardware.constants.VmConstants.VM_TRIM_PATTERN;
import static org.metricshub.hardware.constants.VoltageConstants.VOLTAGE_SENSOR_LOCATION;
import static org.metricshub.hardware.constants.VoltageConstants.VOLTAGE_TRIM_PATTERN;
import static org.metricshub.hardware.util.HwCollectHelper.findMetricByNamePrefixAndAttributes;
import static org.metricshub.hardware.util.HwCollectHelper.isMetricCollected;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * This class is responsible for building monitor names based on various attributes, metrics and conditions.
 */
public class MonitorNameBuilder {

	public static final String HP_OPEN_VMS_COMPUTER = "HP Open-VMS Computer";
	public static final String HP_TRU64_UNIX_COMPUTER = "HP Tru64 Computer";
	public static final String HP_UX_COMPUTER = "HP-UX Computer";
	public static final String IBM_AIX_COMPUTER = "IBM AIX Computer";
	public static final String LINUX_COMPUTER = "Linux Computer";
	public static final String MGMT_CARD_ENCLOSURE = "Management Card";
	public static final String WINDOWS_COMPUTER = "Windows Computer";
	public static final String NETWORK_SWITCH_ENCLOSURE = "Network Switch";
	public static final String STORAGE_ENCLOSURE = "Storage System";
	public static final String SUN_SOLARIS_COMPUTER = "Oracle Solaris Computer";
	public static final String LOCALHOST_ENCLOSURE = System.getProperty("os.name") + " " + System.getProperty("os.arch");
	private static final Pattern WHITE_SPACE_REPEAT_PATTERN = Pattern.compile(WHITE_SPACE_REPEAT_REGEX);
	private static final Map<DeviceKind, String> COMPUTE_DISPLAY_NAMES;

	// Create a lookup table that maps monitor type (lowercase) to the corresponding build method.
	private static final Map<String, BiFunction<Monitor, TelemetryManager, String>> MONITOR_NAME_BUILDERS =
		new HashMap<>();

	static {
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.CPU.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildCpuName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.MEMORY.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildMemoryName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.PHYSICAL_DISK.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildPhysicalDiskName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.LOGICAL_DISK.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildLogicalDiskName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.DISK_CONTROLLER.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildDiskControllerName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.NETWORK.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildNetworkCardName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.FAN.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildFanName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.POWER_SUPPLY.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildPowerSupplyName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.TEMPERATURE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildTemperatureName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.TAPE_DRIVE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildTapeDriveName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.ROBOTICS.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildRoboticsName(monitor)
		);
		// Note: 'enclosure' requires a telemetryManager, so we pass it into the method.
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.ENCLOSURE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildEnclosureName(telemetry, monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.VM.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildVmName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.VOLTAGE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildVoltageName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.BLADE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildBladeName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.GPU.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildGpuName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.BATTERY.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildBatteryName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.LED.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildLedName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.LUN.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildLunName(monitor)
		);
		MONITOR_NAME_BUILDERS.put(
			KnownMonitorType.OTHER_DEVICE.getKey(),
			(monitor, telemetry) -> MonitorNameBuilder.buildOtherDeviceName(monitor)
		);
	}

	static {
		final Map<DeviceKind, String> map = new EnumMap<>(DeviceKind.class);
		for (DeviceKind deviceKind : DeviceKind.values()) {
			// @formatter:off
			// @CHECKSTYLE:OFF
			final String value =
				switch (deviceKind) {
					case VMS -> HP_OPEN_VMS_COMPUTER;
					case TRU64 -> HP_TRU64_UNIX_COMPUTER;
					case HPUX -> HP_UX_COMPUTER;
					case AIX -> IBM_AIX_COMPUTER;
					case LINUX -> LINUX_COMPUTER;
					case OOB -> MGMT_CARD_ENCLOSURE;
					case WINDOWS -> WINDOWS_COMPUTER;
					case NETWORK -> NETWORK_SWITCH_ENCLOSURE;
					case STORAGE -> STORAGE_ENCLOSURE;
					case SOLARIS -> SUN_SOLARIS_COMPUTER;
					default -> null;
				};
			// @CHECKSTYLE:ON
			// @formatter:on
			map.put(deviceKind, value);
		}

		COMPUTE_DISPLAY_NAMES = Collections.unmodifiableMap(map);
	}

	// The hostname of the monitor
	static String hostname;

	/**
	 * Constructor for MonitorNameBuilder
	 * @param hostName The hostname of the monitor
	 */
	public MonitorNameBuilder(String hostName) {
		hostname = hostName;
	}

	/**
	 * Checks whether the given string has meaningful content – that is, it is not null,
	 * empty, or composed solely of whitespace characters.
	 *
	 * @param data the {@link String} to be checked
	 * @return {@code true} if the string has meaningful content; {@code false} otherwise
	 */
	protected static boolean hasMeaningfulContent(final String data) {
		return data != null && !data.trim().isEmpty();
	}

	/**
	 * Trims unwanted characters from the specified name. This method removes commas,
	 * empty parentheses, and replaces multiple whitespace characters with a single space,
	 * then trims any leading or trailing whitespace.
	 *
	 * @param name the {@link String} name to be trimmed
	 * @return the trimmed name
	 */
	static String trimUnwantedCharacters(final String name) {
		if (name == null) {
			return null;
		}

		String cleaned = name.replace(",", "").replace("()", "");

		cleaned = WHITE_SPACE_REPEAT_PATTERN.matcher(cleaned).replaceAll(WHITE_SPACE);

		return cleaned.trim();
	}

	/**
	 * Joins the given non-empty words using the specified separator.
	 *
	 * @param words     an array of {@link String} words to be joined
	 * @param separator the {@link String} separator to use between words
	 * @return a {@link String} representing the joined words
	 */
	static String joinWords(final String[] words, final String separator) {
		return Arrays.stream(words).filter(MonitorNameBuilder::hasMeaningfulContent).collect(Collectors.joining(separator));
	}

	/**
	 * Joins the given non-empty words using a dash ( - ) as the separator.
	 *
	 * @param words an array of {@link String} words to be joined
	 * @return a {@link String} representing the joined words
	 */
	public static String joinWords(final String[] words) {
		return joinWords(words, " - ");
	}

	/**
	 * Joins vendor and model information from the provided attributes map.
	 * If the model already contains the vendor (ignoring the case), the method returns the model;
	 * otherwise, it joins the vendor and model with a whitespace separator.
	 *
	 * @param attributes the {@link Map} containing vendor and model information
	 * @return the joined vendor and model string
	 */
	private static String joinVendorAndModel(final Map<String, String> attributes) {
		final String vendor = attributes.get(VENDOR);
		final String model = attributes.get(MODEL);

		if (vendor != null && model != null && model.toLowerCase().contains(vendor.toLowerCase())) {
			// The model includes the vendor, so no need to join them
			return model;
		}
		return joinWords(new String[] { vendor, model }, WHITE_SPACE);
	}

	/**
	 * Determines whether the attributes indicate a localhost.
	 * This method checks whether the value associated with the key {@code LOCATION}
	 * matches the {@code LOCALHOST} constant.
	 *
	 * @param attributes the {@link Map} of attributes containing location information
	 * @return {@code true} if the attributes indicates a localhost; {@code false} otherwise
	 */
	public static boolean isLocalhost(final Map<String, String> attributes) {
		if (attributes != null) {
			return LOCALHOST.equalsIgnoreCase(attributes.get(LOCATION));
		}
		return false;
	}

	/**
	 * Determines the display name for a computer based on its host location.
	 * If the host monitor is local, it returns the local host enclosure name;
	 * otherwise, it looks up the display name based on the provided {@link DeviceKind}.
	 *
	 * @param hostMonitor the {@link Monitor} instance representing the host (of type HOST)
	 * @param deviceKind  the {@link DeviceKind} of the host monitor
	 * @return the display name for the computer
	 */
	public static String handleComputerDisplayName(
		@NonNull final Monitor hostMonitor,
		@NonNull final DeviceKind deviceKind
	) {
		if (isLocalhost(hostMonitor.getAttributes())) {
			return LOCALHOST_ENCLOSURE;
		} else {
			return COMPUTE_DISPLAY_NAMES.get(deviceKind);
		}
	}

	/**
	 * Converts a byte count to a human-readable string using binary (1024-based) units.
	 *
	 * @param bytes the number of bytes, or {@code null} to return {@code null}
	 * @return a string such as "1.5 MiB"
	 */
	private static String humanReadableByteCountBin(final Double bytes) {
		if (bytes == null) {
			return null;
		}

		long valueLong = bytes.longValue();
		long absValue = valueLong == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(valueLong);

		if (absValue < 1024) {
			return valueLong + " B";
		}

		long value = absValue;
		final CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absValue > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(valueLong);

		return String.format("%.1f %cB", value / 1024.0, ci.current());
	}

	/**
	 * Converts a byte count to a human-readable string using SI (decimal) units.
	 *
	 * @param bytes the number of bytes
	 * @return a string such as "1.5 MB"
	 */
	private static String humanReadableByteCountSI(final Double bytes) {
		// Treat the ±999 B corner-case first
		if (-1000 < bytes && bytes < 1000) {
			return bytes + " B";
		}

		double value = bytes;
		final CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		while (value <= -999_950 || value >= 999_950) {
			value /= 1000;
			ci.next();
		}

		return String.format("%.1f %cB", value / 1000.0, ci.current());
	}

	/**
	 * Builds the full name for a hardware device following the standard naming convention:
	 * <code>[type: ] [name] [(additional-label)]</code>.
	 * <p>
	 * The method uses the display ID if available; otherwise, it falls back to the device ID (after applying
	 *  optional regex trim). If the resulting name exceeds a maximum length, the ID count is used.
	 * Additional label fields are joined (if non-empty) and appended within parentheses.
	 * </p>
	 *
	 * @param type                  the type prefix to be added (maybe {@code null})
	 * @param displayId             the primary display name for the device
	 * @param deviceId              the device identifier to use as a fallback name
	 * @param idCount               the ID count (must be non-null)
	 * @param trimPattern           a {@link Pattern} (regex) used to remove unwanted words from the device ID;
	 *                              may be {@code null}
	 * @param additionalLabelFields optional additional label fields to append inside parentheses
	 * @return the full name following the standard naming convention
	 */
	public static String buildName(
		final String type,
		final String displayId,
		final String deviceId,
		@NonNull final String idCount,
		final Pattern trimPattern,
		final String... additionalLabelFields
	) {
		final StringBuilder fullName = new StringBuilder();

		// Add the type if provided
		if (hasMeaningfulContent(type)) {
			fullName.append(type).append(": ");
		}

		// Determine the base name to use
		String name = null;
		if (hasMeaningfulContent(displayId)) {
			name = displayId;
		} else if (hasMeaningfulContent(deviceId)) {
			name = (trimPattern == null) ? deviceId : trimPattern.matcher(deviceId).replaceAll("");
			if (name.length() > ID_MAX_LENGTH) {
				name = idCount;
			}
		}
		// Fallback to idCount if no meaningful name is found
		if (!hasMeaningfulContent(name)) {
			name = idCount;
		}
		fullName.append(name);

		// Append additional label information if available
		final String additionalLabel = joinWords(additionalLabelFields);
		if (hasMeaningfulContent(additionalLabel)) {
			fullName.append(" (").append(additionalLabel).append(")");
		}
		return trimUnwantedCharacters(fullName.toString());
	}

	/**
	 * Builds the battery name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the battery device
	 * @return the formatted battery name
	 */
	public static String buildBatteryName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			BATTERY_TRIM_PATTERN,
			joinVendorAndModel(attributes),
			attributes.get(BATTERY_TYPE)
		);
	}

	/**
	 * Builds the blade name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the blade device
	 * @return the formatted blade name
	 */
	public static String buildBladeName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			BLADE_TRIM_PATTERN,
			attributes.get(BLADE_NAME),
			attributes.get(MODEL)
		);
	}

	/**
	 * Builds the CPU name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the CPU device
	 * @return the formatted CPU name
	 */
	public static String buildCpuName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Format the CPU maximum speed
		final String cpuMaxSpeed = getCpuMaxSpeed(monitor);
		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			CPU_TRIM_PATTERN,
			attributes.get(VENDOR),
			attributes.get(MODEL),
			cpuMaxSpeed
		);
	}

	/**
	 * Retrieves the maximum speed of the CPU from the monitor's metrics.
	 *
	 * @param monitor the {@link Monitor} instance containing the CPU metrics
	 * @return a formatted string representing the CPU speed in MHz or GHz, or an empty string if unavailable
	 */
	private static String getCpuMaxSpeed(final Monitor monitor) {
		final String cpuSpeedMetricNamePrefix = "hw.cpu.speed.limit";
		Optional<NumberMetric> cpuMaxSpeedMetric = null;
		if (isMetricCollected(monitor, cpuSpeedMetricNamePrefix)) {
			final Map<String, String> cpuSpeedMetricAttributes = Map.of("limit_type", "max");
			cpuMaxSpeedMetric =
				findMetricByNamePrefixAndAttributes(hostname, monitor, cpuSpeedMetricNamePrefix, cpuSpeedMetricAttributes);
		}

		String cpuMaxSpeed = "";
		if (cpuMaxSpeedMetric.isPresent()) {
			double cpuMaxSpeedDouble = cpuMaxSpeedMetric.get().getValue();
			try {
				if (cpuMaxSpeedDouble < 1000D) {
					cpuMaxSpeed = String.format("%.0f MHz", cpuMaxSpeedDouble);
				} else {
					cpuMaxSpeed = String.format("%.2f GHz", (cpuMaxSpeedDouble / 1000D));
				}
			} catch (NumberFormatException nfe) {
				cpuMaxSpeed = null;
			}
		}
		return cpuMaxSpeed;
	}

	/**
	 * Builds the disk controller name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the disk controller
	 * @return the formatted disk controller name
	 */
	public static String buildDiskControllerName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			"Disk Controller",
			attributes.get(DISPLAY_ID),
			attributes.get(DISK_CONTROLLER_NUMBER),
			attributes.get(ID_COUNT),
			DISK_CONTROLLER_TRIM_PATTERN,
			joinVendorAndModel(attributes)
		);
	}

	/**
	 * Builds the enclosure name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the enclosure
	 * @return the formatted enclosure name
	 */
	public static String buildEnclosureName(final TelemetryManager telemetryManager, final Monitor monitor) {
		final DeviceKind deviceKind = telemetryManager.getHostConfiguration().getHostType();

		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();

		final Map<String, String> attributes = monitor.getAttributes();

		// Find the enclosure type
		String enclosureType = attributes.get(ENCLOSURE_TYPE);
		if (enclosureType != null) {
			// @formatter:off
			// @CHECKSTYLE:OFF
			enclosureType =
				switch (enclosureType.toLowerCase()) {
					case "", "computer" -> COMPUTER;
					case "storage" -> STORAGE;
					case "blade" -> BLADE_ENCLOSURE;
					case "switch" -> SWITCH;
					default -> ENCLOSURE;
				};
			// @CHECKSTYLE:ON
			// @formatter:on
		} else {
			enclosureType = ENCLOSURE;
		}

		// If enclosureDisplayID is specified, use it and put the rest in parentheses
		String enclosureDisplayId = attributes.get(DISPLAY_ID);
		String additionalInfo = null;
		final String vendorModel = joinVendorAndModel(attributes);
		if (hasMeaningfulContent(vendorModel)) {
			// We will use vendor/model as enclosure DisplayId if it is not set
			if (hasMeaningfulContent(enclosureDisplayId)) {
				enclosureDisplayId = enclosureDisplayId.trim();
				// Add vendor/model as additionalInfo in parentheses
				additionalInfo = vendorModel.trim();
			} else {
				// Use it as enclosure DisplayId
				enclosureDisplayId = vendorModel.trim();
			}
		} else if (COMPUTER.equals(enclosureType)) {
			// Find the computer display name
			String computerDisplayName = handleComputerDisplayName(hostMonitor, deviceKind);
			if (hasMeaningfulContent(computerDisplayName)) {
				computerDisplayName = computerDisplayName.trim();
				// We will use computer display name as enclosure DisplayId if it is still not set
				if (hasMeaningfulContent(enclosureDisplayId)) {
					// Add computerDisplayName as additionalInfo in parentheses
					additionalInfo = computerDisplayName;
				} else {
					// Use it as enclosure DisplayId
					enclosureDisplayId = computerDisplayName;
				}
			}
		}
		// Build the name
		return buildName(
			enclosureType,
			enclosureDisplayId,
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			null,
			additionalInfo
		);
	}

	/**
	 * Builds the fan name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the fan device
	 * @return the formatted fan name
	 */
	public static String buildFanName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			FAN_TRIM_PATTERN,
			attributes.get(FAN_SENSOR_LOCATION)
		);
	}

	/**
	 * Builds the LED name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the LED device
	 * @return the formatted LED name
	 */
	public static String buildLedName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Format the LED color
		String ledColor = attributes.get(COLOR);
		if (hasMeaningfulContent(ledColor)) {
			ledColor = ledColor.substring(0, 1).toUpperCase() + ledColor.substring(1).toLowerCase();
		}

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			LED_TRIM_PATTERN,
			ledColor,
			attributes.get(NAME)
		);
	}

	/**
	 * Builds the logical disk name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the logical disk device
	 * @return the formatted logical disk name
	 */
	public static String buildLogicalDiskName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Format the RAID level
		String logicalDiskRaidLevel = attributes.get(RAID_LEVEL);
		if (logicalDiskRaidLevel != null) {
			try {
				int logicalDiskRaidLevelD = Integer.parseInt(logicalDiskRaidLevel);
				logicalDiskRaidLevel = String.format("RAID %d", logicalDiskRaidLevelD);
			} catch (NumberFormatException nfe) {
				// Do nothing if parsing fails
			}
		}

		// Retrieve the size metric
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		final AbstractMetric logicalDiskSize = metrics.get(LOGICAL_DISK_SIZE);

		// Build the name
		return buildName(
			attributes.get(LOGICAL_DISK_TYPE),
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			LOGICAL_DISK_TRIM_PATTERN,
			logicalDiskRaidLevel,
			humanReadableByteCountBin(logicalDiskSize != null ? logicalDiskSize.getValue() : null)
		);
	}

	/**
	 * Builds the LUN name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the LUN device
	 * @return the formatted LUN name
	 */
	public static String buildLunName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			LUN_TRIM_PATTERN,
			attributes.get(LOCAL_DEVICE_NAME),
			attributes.get(REMOTE_DEVICE_NAME)
		);
	}

	/**
	 * Builds the memory name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the memory device
	 * @return the formatted memory name
	 */
	public static String buildMemoryName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Retrieve the size metric
		final String memorySize = getMemorySize(monitor);
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			MEMORY_TRIM_PATTERN,
			attributes.get(VENDOR),
			attributes.get(MEMORY_TYPE),
			memorySize
		);
	}

	/**
	 * Retrieves and formats the memory size from the monitor's metrics.
	 *
	 * @param monitor the {@link Monitor} instance containing memory metrics
	 * @return the formatted memory size in MB, or {@code null} if unavailable or below threshold
	 */
	private static String getMemorySize(final Monitor monitor) {
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		final AbstractMetric memorySizeMetric = metrics.get(MEMORY_SIZE_METRIC);

		// Format the memory size
		String memorySize = "";
		if (memorySizeMetric != null) {
			try {
				double memorySizeDoubleValue = memorySizeMetric.getValue();
				if (memorySizeDoubleValue > 50D) {
					memorySize = String.format("%.0f MB", memorySizeDoubleValue);
				} else {
					memorySize = null;
				}
			} catch (NumberFormatException nfe) {
				memorySize = null;
			}
		}
		return memorySize;
	}

	/**
	 * Builds the network card name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the network card device
	 * @return the formatted network card name
	 */
	public static String buildNetworkCardName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Network card vendor without unwanted words
		String networkCardVendor = attributes.get(VENDOR);
		if (hasMeaningfulContent(networkCardVendor)) {
			networkCardVendor = NETWORK_VENDOR_MODEL_TRIM_PATTERN.matcher(networkCardVendor).replaceAll("");
		}

		// Network card model without unwanted words and up to 30 characters
		String networkCardModel = attributes.get(MODEL);
		if (hasMeaningfulContent(networkCardModel)) {
			networkCardModel = NETWORK_VENDOR_MODEL_TRIM_PATTERN.matcher(networkCardModel).replaceAll("");
			if (networkCardModel.length() > 30) {
				networkCardModel = networkCardModel.substring(0, 30);
			}
		}

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			NETWORK_CARD_TRIM_PATTERN,
			attributes.get(NETWORK_DEVICE_TYPE),
			networkCardVendor,
			networkCardModel
		);
	}

	/**
	 * Builds the other device name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the other device
	 * @return the formatted device name
	 */
	public static String buildOtherDeviceName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			attributes.get(OTHER_DEVICE_TYPE), //#question
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			null,
			attributes.get(ADDITIONAL_LABEL)
		);
	}

	/**
	 * Builds the physical disk name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the physical disk device
	 * @return the formatted physical disk name
	 */
	public static String buildPhysicalDiskName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Retrieve the size metric
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		final AbstractMetric diskSize = metrics.get(PHYSICAL_DISK_SIZE_METRIC);

		// Build the name
		return buildName(
			attributes.get(PHYSICAL_DISK_DEVICE_TYPE),
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			PHYSICAL_DISK_TRIM_PATTERN,
			attributes.get(VENDOR),
			humanReadableByteCountSI(diskSize != null ? diskSize.getValue() : null)
		);
	}

	/**
	 * Builds the power supply name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the power supply device
	 * @return the formatted power supply name
	 */
	public static String buildPowerSupplyName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Retrieve the power supply limit metric
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		final AbstractMetric powerSupplyPowerLimit = metrics.get(POWER_SUPPLY_LIMIT_METRIC);
		final Double powerSupplyPowerMetricValue = powerSupplyPowerLimit != null ? powerSupplyPowerLimit.getValue() : null;
		String powerSupplyPower = powerSupplyPowerMetricValue == null ? "" : String.valueOf(powerSupplyPowerMetricValue);

		// Format the power
		if (hasMeaningfulContent(powerSupplyPower)) {
			powerSupplyPower = "%.0f W".formatted(powerSupplyPowerMetricValue);
		}

		//Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			POWER_SUPPLY_TRIM_PATTERN,
			attributes.get(POWER_SUPPLY_TYPE),
			powerSupplyPower
		);
	}

	/**
	 * Builds the robotics name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the robotics device
	 * @return the formatted robotics name
	 */
	public static String buildRoboticsName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			ROBOTICS_TRIM_PATTERN,
			joinVendorAndModel(attributes),
			attributes.get(ROBOTIC_TYPE)
		);
	}

	/**
	 * Builds the tape drive name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the tape drive device
	 * @return the formatted tape drive name
	 */
	public static String buildTapeDriveName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			TAPE_DRIVE_TRIM_PATTERN,
			joinVendorAndModel(attributes)
		);
	}

	/**
	 * Builds the temperature sensor name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the temperature sensor device
	 * @return the formatted temperature sensor name
	 */
	public static String buildTemperatureName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			TEMPERATURE_TRIM_PATTERN,
			attributes.get(TEMPERATURE_SENSOR_LOCATION)
		);
	}

	/**
	 * Builds the voltage sensor name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the voltage sensor device
	 * @return the formatted voltage sensor name
	 */
	public static String buildVoltageName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the name
		return buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			VOLTAGE_TRIM_PATTERN,
			attributes.get(VOLTAGE_SENSOR_LOCATION)
		);
	}

	/**
	 * Builds the virtual machine name based on the current implementation.
	 *
	 * @param monitor the {@link Monitor} instance representing the VM device
	 * @return the formatted VM name
	 */
	public static String buildVmName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Format the display ID
		String displayId = attributes.get(DISPLAY_ID);
		final String hostname = attributes.get(HOSTNAME);
		if ((displayId == null || displayId.isBlank())) {
			displayId = hostname;
		}

		// Build the name
		return buildName(null, displayId, attributes.get(DEVICE_ID), attributes.get(ID_COUNT), VM_TRIM_PATTERN, hostname);
	}

	/**
	 * Builds the GPU name based on the current implementation.
	 * <p>
	 * In addition to building the base name, this method appends vendor, model, and (if available)
	 * the formatted size (in GB) as additional information – provided they are not already present in the base name.
	 * </p>
	 *
	 * @param monitor the {@link Monitor} instance representing the GPU device
	 * @return the formatted GPU name
	 */
	public static String buildGpuName(final Monitor monitor) {
		// Retrieve the attributes
		final Map<String, String> attributes = monitor.getAttributes();

		// Build the base name using the GPU-specific trim pattern
		final String name = buildName(
			null,
			attributes.get(DISPLAY_ID),
			attributes.get(DEVICE_ID),
			attributes.get(ID_COUNT),
			GPU_TRIM_PATTERN
		);

		// Retrieve vendor and model, ensuring they are non-blank and not already included in the base name
		String vendor = attributes.get(VENDOR);
		if (vendor != null && (vendor.isBlank() || name.toLowerCase().contains(vendor.toLowerCase()))) {
			vendor = null;
		}
		String model = attributes.get(MODEL);
		if (model != null && (model.isBlank() || name.toLowerCase().contains(model.toLowerCase()))) {
			model = null;
		}

		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		final AbstractMetric gpuMemoryLimit = metrics.get(GPU_MEMORY_LIMIT_METRIC);

		final Double size = gpuMemoryLimit != null ? gpuMemoryLimit.getValue() : 0.0;
		final String formattedSize = size > 0.0 ? String.format(" - %.2f GB", size / 1024.0) : "";

		// Append additional information based on vendor/model availability
		if (vendor != null && model != null) {
			final String additionalInformation = model.toLowerCase().contains(vendor.toLowerCase())
				? String.format(" (%s%s)", model, formattedSize)
				: String.format(" (%s - %s%s)", vendor, model, formattedSize);
			return trimUnwantedCharacters(String.format("%s%s", name, additionalInformation));
		}
		if (vendor != null) {
			return trimUnwantedCharacters(String.format("%s (%s%s)", name, vendor, formattedSize));
		}
		if (model != null) {
			return trimUnwantedCharacters(String.format("%s (%s%s)", name, model, formattedSize));
		}
		return trimUnwantedCharacters(String.format("%s%s", name, formattedSize));
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
	public String buildMonitorNameUsingType(@NonNull final Monitor monitor, final TelemetryManager telemetryManager) {
		String type = monitor.getType();
		if (type == null) {
			return null;
		}
		// Normalize type to lowercase
		BiFunction<Monitor, TelemetryManager, String> builder = MONITOR_NAME_BUILDERS.get(type.toLowerCase());
		return builder != null ? builder.apply(monitor, telemetryManager) : type;
	}
}
