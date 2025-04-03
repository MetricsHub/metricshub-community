package org.metricshub.extension.win.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.common.helpers.ArrayHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.ListHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.NumberHelper;

/**
 * Utility class for IPMI (Intelligent Platform Management Interface) operations and data processing.
 * This class provides methods for cleaning, processing, and translating IPMI-related data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IpmiThroughWmiHelper {

	private static final String EQUALS_0 = "=0";

	private static final String EQUALS_1 = "=1";

	private static final String DEASSERTED = "=Deasserted";

	private static final String STATE_DEASSERTED = "=State Deasserted";

	private static final String STATE_ASSERTED = "=State Asserted";

	/**
	 * Process what we got from the IPMI WMI provider and return a pretty table.
	 *
	 * @param wmiComputerSystem  The WMI computer system result table
	 * @param wmiNumericSensors  The WMI numeric sensors result table
	 * @param wmiDiscreteSensors The WMI discrete sensors result table
	 * @return List of List (Table)
	 */
	public static List<List<String>> ipmiTranslateFromWmi(
		@NonNull List<List<String>> wmiComputerSystem,
		@NonNull List<List<String>> wmiNumericSensors,
		@NonNull List<List<String>> wmiDiscreteSensors
	) {
		List<List<String>> ipmiTable = new ArrayList<>();

		// Process compute system data
		List<String> wmiComputerSystemTranslated = translateWmiComputerSystem(wmiComputerSystem);
		if (!wmiComputerSystemTranslated.isEmpty()) {
			ipmiTable.add(wmiComputerSystemTranslated);
		}

		// Process numeric sensors
		List<List<String>> wmiNumericSensorsTranslated = translateWmiNumericSensors(wmiNumericSensors);
		if (!wmiNumericSensorsTranslated.isEmpty()) {
			ipmiTable.addAll(wmiNumericSensorsTranslated);
		}

		// Process discrete sensors
		Collection<List<String>> wmiDiscreteSensorsTranslated = translateWmiDiscreteSensors(wmiDiscreteSensors);
		if (!wmiDiscreteSensorsTranslated.isEmpty()) {
			ipmiTable.addAll(wmiDiscreteSensorsTranslated);
		}

		return ipmiTable;
	}

	/**
	 * Process the wmiComputerSystem request result into a pretty table.
	 *
	 * @param wmiComputerSystem The WMI computer system result table
	 * @return Single row as a {@link List} for the computer system
	 */
	private static List<String> translateWmiComputerSystem(final List<List<String>> wmiComputerSystem) {
		if (!wmiComputerSystem.isEmpty()) {
			List<String> wmiComputerSystemLine = wmiComputerSystem.get(0);

			if (wmiComputerSystemLine.size() > 2) {
				return Arrays.asList(
					"FRU",
					wmiComputerSystemLine.get(2),
					wmiComputerSystemLine.get(1),
					wmiComputerSystemLine.get(0)
				);
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Process the wmiNumericSensors request result into a pretty table.
	 *
	 * @param wmiNumericSensors  The WMI numeric sensors result table
	 * @return List of List (Table)
	 */
	private static List<List<String>> translateWmiNumericSensors(final List<List<String>> wmiNumericSensors) {
		List<List<String>> result = new ArrayList<>();

		// BaseUnits,CurrentReading,Description,LowerThresholdCritical,LowerThresholdNonCritical,SensorType,UnitModifier,UpperThresholdCritical,UpperThresholdNonCritical
		for (List<String> line : wmiNumericSensors) {
			// Process the 'Description' field which contains everything about how to identify the sensor
			String description = ListHelper.getValueAtIndex(line, 2, "");
			if (description.isEmpty()) {
				continue;
			}

			String[] sensorSplit = description.split("\\(");
			String sensorId = ArrayHelper.getValueAtIndex(sensorSplit, 1, "").split("\\)")[0];
			String sensorName = sensorSplit[0];
			description = ArrayHelper.getValueAtIndex(description.split(":"), 1, "");

			String deviceId;

			int lookupIndex = description.indexOf(" for ");
			if (lookupIndex != -1) {
				deviceId = description.substring(lookupIndex + 5);
			} else {
				deviceId = "";
			}

			String baseUnit = ListHelper.getValueAtIndex(line, 0, "0");
			int unitModifier;

			unitModifier = NumberHelper.parseInt(ListHelper.getValueAtIndex(line, 6, "0"), 0);

			String sensorType = ListHelper.getValueAtIndex(line, 5, "0");

			double currentValue = NumberHelper.parseDouble(ListHelper.getValueAtIndex(line, 1, "0D"), 0D);

			// Temperature
			// Check the unit (must be Celsius degrees)
			// 2 -> Celsius, 3 -> Farenheit, 4 -> Kelvin
			if (sensorType.equals("2") && currentValue != 0 && List.of("2", "3", "4").contains(baseUnit)) {
				List<String> temperatureList = temperatureRow(unitModifier, currentValue, baseUnit, line);
				result.add(
					Arrays.asList(
						"Temperature",
						sensorId,
						sensorName,
						deviceId,
						temperatureList.get(0),
						temperatureList.get(1),
						temperatureList.get(2)
					)
				);
			} else if (sensorType.equals("5") && baseUnit.equals("19") && currentValue != 0) { // Fans
				List<String> fanList = fanRow(unitModifier, currentValue, line);

				result.add(
					Arrays.asList("Fan", sensorId, sensorName, deviceId, fanList.get(0), fanList.get(1), fanList.get(2))
				);
			} else if (sensorType.equals("3") && baseUnit.equals("5") && currentValue != 0) { // Voltage
				List<String> voltageList = voltageRow(unitModifier, currentValue, line);

				// Add the sensor to the table
				result.add(
					Arrays.asList(
						"Voltage",
						sensorId,
						sensorName,
						deviceId,
						voltageList.get(0),
						voltageList.get(1),
						voltageList.get(2)
					)
				);
			} else if (sensorType.equals("4") && baseUnit.equals("6") && currentValue != 0) { // Current
				// Convert values based on unitModifier
				currentValue = currentValue * Math.pow(10, unitModifier);
				// Add the sensor to the table
				result.add(Arrays.asList("Current", sensorId, sensorName, deviceId, String.valueOf(currentValue)));
			} else if (sensorType.equals("1") && (baseUnit.equals("7") || (baseUnit.equals("8") && currentValue != 0))) { // Power consumption.
				// Depending on the unit, convert it to watts or not
				if (baseUnit.equals("7")) {
					List<String> powerList = powerRow(unitModifier, currentValue, line);
					result.add(
						Arrays.asList(
							"PowerConsumption",
							sensorId,
							sensorName,
							deviceId,
							powerList.get(0),
							powerList.get(1),
							powerList.get(2)
						)
					);
				} else {
					result.add(
						Arrays.asList("EnergyUsage", sensorId, sensorName, deviceId, energyRow(unitModifier, currentValue))
					);
				}
			}
		}

		return result;
	}

	/**
	 * Process the wmiDiscreteSensors request result into a pretty table.
	 *
	 * @param wmiDiscreteSensors The WMI discrete sensors result table
	 * @return List of List (Table)
	 */
	private static List<List<String>> translateWmiDiscreteSensors(final List<List<String>> wmiDiscreteSensors) {
		Map<String, List<String>> deviceMap = new HashMap<>();

		for (List<String> line : wmiDiscreteSensors) {
			String description = ListHelper.getValueAtIndex(line, 1, "");
			if (description.isEmpty()) {
				continue;
			}

			String sensorName = description.split("\\(")[0];
			description = ArrayHelper.getValueAtIndex(description.split(":"), 1, "");

			String entityId;

			int lookupIndex = description.indexOf(" for ");
			if (lookupIndex == -1) {
				continue;
			}

			entityId = description.substring(lookupIndex + 5);
			lookupIndex = entityId.lastIndexOf(WHITE_SPACE);
			if (lookupIndex == -1) {
				continue;
			}

			String deviceType = entityId.substring(0, lookupIndex);
			String deviceId = entityId.substring(lookupIndex + 1);

			String state = ListHelper.getValueAtIndex(line, 0, "0.0");
			if (state == null || state.isEmpty() || "N/A".equals(state)) {
				continue;
			}

			if (state.length() > 18 && state.startsWith("OEM State,Value=")) {
				// Reverse the bytes of the WORD value
				state = "0x" + state.substring(18, 20) + state.substring(16, 18);
			}

			state = state.replace(".,", "|" + sensorName + "=");

			// Get the line of this device
			List<String> deviceLine = deviceMap.get(entityId);
			if (deviceLine != null) {
				if (deviceLine.size() < 7) {
					continue;
				}

				// Add the list of states of this sensor to the device
				deviceLine.set(6, String.format("%s|%s=%s", deviceLine.get(6), sensorName, state));

				// Re-add that to the device list
				deviceMap.put(entityId, deviceLine);
			} else {
				// This is the first time we find this device ID
				List<String> deviceSensorList = Arrays.asList(
					deviceType,
					deviceId,
					entityId,
					"",
					"",
					"",
					String.format("%s=%s", sensorName, state)
				);
				deviceMap.put(entityId, deviceSensorList);
			}
		}

		return deviceMap
			.values()
			.stream()
			.filter(line ->
				line.size() >= 7 && !ListHelper.getValueAtIndex(line, 6, "").contains("=Device Removed/Device Absent")
			)
			.map(line -> {
				line.set(
					6,
					line
						.get(6)
						.replace(STATE_ASSERTED, EQUALS_1)
						.replace(STATE_DEASSERTED, EQUALS_0)
						.replace(DEASSERTED, EQUALS_0)
				);
				return line;
			})
			.collect(Collectors.toList());
	}

	/**
	 * Convert from Fahrenheit to Celsius, rounded to two decimals.
	 *
	 * @param fahrenheit The value we wish to convert
	 * @return double value
	 */
	public static double convertFromFahrenheitToCelsius(double fahrenheit) {
		return Math.round((fahrenheit - 32) * 55.56) / 100D;
	}

	/**
	 * Convert from Kelvin to Celsius.
	 * @param kelvin The value we wish to convert
	 * @return double value
	 */
	public static double convertFromKelvinToCelsius(double kelvin) {
		return kelvin - 273.15;
	}

	/**
	 * Calculate the current value and thresholds for the temperature row.
	 *
	 * @param unitModifier The modifier used to convert the current value and thresholds
	 * @param currentValue The current reading value
	 * @param baseUnit     The base unit unit to convert the value and the thresholds in case of Kelvin or Fahrenheit units
	 * @param line         The line used to extract the threshold values
	 * @return List of values
	 */
	private static List<String> temperatureRow(
		final int unitModifier,
		double currentValue,
		final String baseUnit,
		final List<String> line
	) {
		// Convert values based on unitModifier
		currentValue = currentValue * Math.pow(10, unitModifier);

		String threshold1 = ListHelper.getValueAtIndex(line, 8, "0.0");
		String threshold2 = ListHelper.getValueAtIndex(line, 7, "0.0");

		if (baseUnit.equals("4")) {
			currentValue = convertFromKelvinToCelsius(currentValue);
		} else if (baseUnit.equals("3")) {
			currentValue = convertFromFahrenheitToCelsius(currentValue);
		}

		Double threshold1Double = NumberHelper.parseDouble(threshold1, null);

		if (threshold1Double != null) {
			threshold1Double = threshold1Double * Math.pow(10, unitModifier);
			if (baseUnit.equals("4")) {
				threshold1Double = convertFromKelvinToCelsius(threshold1Double);
			} else if (baseUnit.equals("3")) {
				threshold1Double = convertFromFahrenheitToCelsius(threshold1Double);
			}

			threshold1 = String.valueOf(threshold1Double);
		} else {
			threshold1 = "";
		}

		Double threshold2Double = NumberHelper.parseDouble(threshold2, null);

		if (threshold2Double != null) {
			threshold2Double = threshold2Double * Math.pow(10, unitModifier);

			if (baseUnit.equals("4")) {
				threshold2Double = convertFromKelvinToCelsius(threshold2Double);
			} else if (baseUnit.equals("3")) {
				threshold2Double = convertFromFahrenheitToCelsius(threshold2Double);
			}

			threshold2 = String.valueOf(threshold2Double);
		} else {
			threshold2 = "";
		}

		return Arrays.asList(String.valueOf(currentValue), threshold1, threshold2);
	}

	/**
	 * Calculate the current value and thresholds for the fan row.
	 *
	 * @param unitModifier The modifier used to convert the current value and thresholds
	 * @param currentValue The current reading value
	 * @param line         The results from which we want to extract the thresholds
	 * @return List of values
	 */
	private static List<String> fanRow(final int unitModifier, double currentValue, final List<String> line) {
		String threshold1 = ListHelper.getValueAtIndex(line, 4, "0.0");
		String threshold2 = ListHelper.getValueAtIndex(line, 3, "0.0");

		// Convert values based on unitModifier
		currentValue = currentValue * Math.pow(10, unitModifier);

		Double threshold1Double = NumberHelper.parseDouble(threshold1, null);
		if (threshold1Double != null) {
			threshold1 = String.valueOf(threshold1Double * Math.pow(10, unitModifier));
		} else {
			threshold1 = "";
		}

		Double threshold2Double = NumberHelper.parseDouble(threshold2, null);
		if (threshold2Double != null) {
			threshold2 = String.valueOf(threshold2Double * Math.pow(10, unitModifier));
		} else {
			threshold2 = "";
		}

		return Arrays.asList(String.valueOf(currentValue), threshold1, threshold2);
	}

	/**
	 * Calculate the current value and thresholds for the voltage row.
	 *
	 * @param unitModifier The modifier used to convert the current value and thresholds
	 * @param currentValue The current reading value
	 * @param line         The results from which we want to extract the thresholds
	 * @return
	 */
	private static List<String> voltageRow(final int unitModifier, double currentValue, final List<String> line) {
		// Convert values based on unitModifier and then from Volts to milliVolts
		currentValue = currentValue * Math.pow(10, unitModifier) * 1000;

		String threshold1 = ListHelper.getValueAtIndex(line, 4, "0.0");
		Double threshold1Double = NumberHelper.parseDouble(threshold1, null);

		if (threshold1Double == null) {
			threshold1 = ListHelper.getValueAtIndex(line, 3, "0.0");
			threshold1Double = NumberHelper.parseDouble(threshold1, null);
		}

		if (threshold1Double != null) {
			threshold1Double = threshold1Double * Math.pow(10, unitModifier) * 1000;
			threshold1 = String.valueOf(threshold1Double);
		} else {
			threshold1 = "";
		}

		String threshold2 = ListHelper.getValueAtIndex(line, 8, "0.0");
		Double threshold2Double = NumberHelper.parseDouble(threshold2, null);

		if (threshold2Double == null) {
			threshold2 = ListHelper.getValueAtIndex(line, 7, "0.0");
			threshold2Double = NumberHelper.parseDouble(threshold2, null);
		}

		if (threshold2Double != null) {
			threshold2Double = threshold2Double * Math.pow(10, unitModifier) * 1000;
			threshold2 = String.valueOf(threshold2Double);
		} else {
			threshold2 = "";
		}

		return Arrays.asList(String.valueOf(currentValue), threshold1, threshold2);
	}

	/**
	 * Calculate the current value and thresholds for the power row.
	 * @param unitModifier The modifier used to convert the current value and thresholds
	 * @param currentValue The current reading value
	 * @param line         The results from which we want to extract the thresholds
	 * @return List of values
	 */
	private static List<String> powerRow(final int unitModifier, double currentValue, final List<String> line) {
		String threshold1 = ListHelper.getValueAtIndex(line, 8, "0.0");
		String threshold2 = ListHelper.getValueAtIndex(line, 7, "0.0");

		Double threshold1Double = NumberHelper.parseDouble(threshold1, null);

		if (threshold1Double != null) {
			threshold1Double = threshold1Double * Math.pow(10, unitModifier);
			threshold1 = String.valueOf(threshold1Double);
		} else {
			threshold1 = "";
		}

		Double threshold2Double = NumberHelper.parseDouble(threshold2, null);

		if (threshold2Double != null) {
			threshold2Double = threshold2Double * Math.pow(10, unitModifier);
			threshold2 = String.valueOf(threshold2Double);
		} else {
			threshold2 = "";
		}

		// Convert values based on unitModifier
		currentValue = currentValue * Math.pow(10, unitModifier);

		return Arrays.asList(String.valueOf(currentValue), threshold1, threshold2);
	}

	/**
	 * Calculate the current value for the energy row.
	 *
	 * @param unitModifier The modifier used to convert the current value
	 * @param currentValue The current reading value
	 * @return String value
	 */
	private static String energyRow(final int unitModifier, double currentValue) {
		// Convert values based on unitModifier
		// Joule conversion.
		return String.valueOf((currentValue * Math.pow(10, unitModifier)) / 3600000);
	}
}
