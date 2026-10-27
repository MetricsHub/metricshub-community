package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub IPMI Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.Fru;
import org.metricshub.ipmi.client.Sensor;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.PiromInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.SpdInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;

/**
 * Converts the results of ipmi-java into the semicolon-separated text that the IPMI connectors parse (see the
 * README of this module for the format).
 */
public final class IpmiResultConverter {

	private static final int NO_READING = 255;

	private static final String EMPTY = "";

	private static final Set<EntityId> PANELS = EnumSet.of(
		EntityId.FrontPanelBoard,
		EntityId.BackPanelBoard,
		EntityId.ChassisBackPanelBoard
	);

	private static final DoubleUnaryOperator IDENTITY = v -> v;
	private static final DoubleUnaryOperator VOLTS_TO_MILLIVOLTS = v -> v * 1000;
	private static final DoubleUnaryOperator FAHRENHEIT_TO_CELSIUS = v -> (v - 32.0) * 0.5556;
	private static final DoubleUnaryOperator KELVIN_TO_CELSIUS = v -> v - 273.15;

	/**
	 * A FRU as the text output lists it: {@code FRU;$vendor;$model;$serialNumber}. The details are the trailing
	 * columns of the device state line of its entity: {@code $memorySize;$memoryType;$cpuSpeed;$cpuInfo}, filled for a
	 * memory module (SPD data) or a processor (Intel PIROM), empty otherwise.
	 */
	private record FruDevice(String vendor, String model, String serialNumber, String details) {
		@Override
		public String toString() {
			return "FRU;" + vendor + ";" + model + ";" + serialNumber;
		}
	}

	private static final String NO_DETAILS = ";;;";

	private static final FruDevice NO_FRU = new FruDevice(EMPTY, EMPTY, EMPTY, NO_DETAILS);

	private IpmiResultConverter() {}

	/**
	 * Converts the chassis status, the FRUs and the sensors of a BMC to the text format: the chassis line, then the
	 * FRU lines, then the device state lines, then the reading lines.
	 *
	 * @param chassisStatus The chassis status read from the BMC, {@code null} to leave the chassis line out
	 * @param frus          The FRUs read from the BMC
	 * @param sensors       The sensors read from the BMC
	 * @return the lines, separated with {@code \n}
	 */
	public static String convertResult(
		final ChassisStatus chassisStatus,
		final List<Fru> frus,
		final List<Sensor> sensors
	) {
		final Map<String, FruDevice> frusByEntity = new HashMap<>();
		final List<String> fruLines = fruLines(frus, frusByEntity);
		final Collection<String> stateLines = stateLines(sensors, frusByEntity);
		final Stream<String> readingLines = sensors.stream().map(IpmiResultConverter::readingLine).filter(Objects::nonNull);

		return Stream.of(chassisLine(chassisStatus), fruLines.stream(), stateLines.stream(), readingLines)
			.flatMap(s -> s)
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Converts the chassis status to the text the IPMI criterion expects.
	 *
	 * @param chassisStatus The chassis status read from the BMC
	 * @return {@code System power state is up} or {@code System power state is down}, {@code null} for a null status
	 */
	public static String convertResult(final ChassisStatus chassisStatus) {
		if (chassisStatus == null) {
			return null;
		}
		return "System power state is " + (chassisStatus.powerOn() ? "up" : "down");
	}

	/**
	 * Builds the chassis line of the source text:
	 * {@code Chassis;$powerOn;$powerFault;$powerOverload;$interlock;$powerControlFault;$coolingFault;$driveFault;
	 * $intrusion;$frontPanelLockout;$powerRestorePolicy;$identifyState;$lastPowerEvents}. The flags are {@code 1} or
	 * {@code 0}, the identify state is empty when the BMC does not support it, and the last power events are
	 * separated with {@code |}.
	 *
	 * @param status The chassis status, {@code null} when it was not read
	 * @return the line, or an empty stream for a null status
	 */
	private static Stream<String> chassisLine(final ChassisStatus status) {
		if (status == null) {
			return Stream.empty();
		}
		return Stream.of(
			String.join(
				";",
				"Chassis",
				flag(status.powerOn()),
				flag(status.powerFault()),
				flag(status.powerOverload()),
				flag(status.interlock()),
				flag(status.powerControlFault()),
				flag(status.coolingFault()),
				flag(status.driveFault()),
				flag(status.intrusion()),
				flag(status.frontPanelLockout()),
				String.valueOf(status.powerRestorePolicy()),
				status.identifyState().map(Enum::name).orElse(EMPTY),
				status.lastPowerEvents().stream().map(Enum::name).collect(Collectors.joining("|"))
			)
		);
	}

	private static String flag(final boolean set) {
		return set ? "1" : "0";
	}

	/**
	 * Builds the FRU lines, sorted by how complete and how central each FRU is: the system chassis and board with a
	 * model and a serial number, then the panels and the other FRUs with a model and a serial number, then the rest.
	 *
	 * @param frus         The FRUs read from the BMC
	 * @param frusByEntity Filled with each listed FRU, by the entity key of its locator, for the device state lines
	 * @return the FRU lines
	 */
	private static List<String> fruLines(final List<Fru> frus, final Map<String, FruDevice> frusByEntity) {
		final List<FruDevice> system = new ArrayList<>();
		final Deque<FruDevice> others = new ArrayDeque<>();
		final List<FruDevice> incomplete = new ArrayList<>();

		for (final Fru fru : frus) {
			// The SPD data of a memory module or the PIROM of a processor; otherwise the product area when it names a
			// manufacturer, the board area otherwise
			final ProductInfo product = fru.product().orElse(null);
			final boolean fromProduct = product != null && !isBlank(product.getManufacturerName());
			final FruDevice device = fru
				.spd()
				.map(IpmiResultConverter::fromSpd)
				.or(() -> fru.pirom().map(IpmiResultConverter::fromPirom))
				.orElseGet(() -> fromProduct ? fromProduct(product) : fromBoard(fru.board().orElse(null)));
			if (device == null) {
				continue;
			}

			frusByEntity.put(entityKey(DeviceDescription.getDeviceType(fru.entity()), fru.entityInstance()), device);

			final EntityId entity = fru.entity();
			if (!fromProduct || isBlank(device.model()) || isBlank(device.serialNumber())) {
				incomplete.add(device);
			} else if (entity == EntityId.SystemChassis || entity == EntityId.SystemBoard) {
				system.add(device);
			} else if (PANELS.contains(entity)) {
				others.addFirst(device);
			} else {
				others.addLast(device);
			}
		}

		return Stream.of(system, others, incomplete).flatMap(Collection::stream).map(FruDevice::toString).toList();
	}

	private static FruDevice fromProduct(final ProductInfo product) {
		return new FruDevice(
			orEmpty(product.getManufacturerName()),
			orEmpty(product.getProductName()),
			serialNumber(product.getProductSerialNumber(), product.getProductModelNumber()),
			NO_DETAILS
		);
	}

	/**
	 * @param spd The SPD data of a memory module
	 * @return the FRU: the module manufacturer, its part number and its serial number, with its capacity in MB and
	 *         its type ({@code DDR4 RDIMM ECC}) as details
	 */
	private static FruDevice fromSpd(final SpdInfo spd) {
		final String type = spd.getMemoryType() + " " + spd.getModuleType() + (spd.isEcc() ? " ECC" : EMPTY);
		return new FruDevice(
			orEmpty(spd.getManufacturer()),
			orEmpty(spd.getPartNumber()),
			orEmpty(spd.getSerialNumber()),
			String.join(";", positive(spd.getCapacityMegabytes()), type.trim(), EMPTY, EMPTY)
		);
	}

	/**
	 * @param pirom The Processor Information ROM of an Intel processor
	 * @return the FRU: Intel, the S-spec and the PPIN of the processor, with its maximum frequency in MHz and its
	 *         characteristics (cores, frequencies, caches, TDP, part number) as details
	 */
	private static FruDevice fromPirom(final PiromInfo pirom) {
		final List<String> info = new ArrayList<>();
		if (pirom.getCores() > 0) {
			info.add(pirom.getCores() + " cores" + (pirom.isHyperThreading() ? " with Hyper-Threading" : EMPTY));
		}
		if (pirom.getBaseFrequencyMhz() > 0) {
			info.add(
				pirom.getMaxFrequencyMhz() > pirom.getBaseFrequencyMhz()
					? pirom.getBaseFrequencyMhz() + "-" + pirom.getMaxFrequencyMhz() + " MHz"
					: pirom.getBaseFrequencyMhz() + " MHz"
			);
		}
		if (pirom.getL2KilobytesPerCore() > 0) {
			info.add("L2 " + pirom.getL2KilobytesPerCore() + " KB per core");
		}
		if (pirom.getL3Kilobytes() > 0) {
			info.add("L3 " + pirom.getL3Kilobytes() + " KB");
		}
		if (pirom.getTdpWatts() > 0) {
			info.add("TDP " + pirom.getTdpWatts() + " W");
		}
		if (!isBlank(pirom.getPartNumber())) {
			info.add("Part number " + pirom.getPartNumber());
		}
		return new FruDevice(
			"Intel",
			orEmpty(pirom.getSSpec()),
			orEmpty(pirom.getPpin()),
			String.join(
				";",
				EMPTY,
				EMPTY,
				positive(Math.max(pirom.getBaseFrequencyMhz(), pirom.getMaxFrequencyMhz())),
				String.join(", ", info)
			)
		);
	}

	/**
	 * @param value A value the ROM gives as 0 when it is not programmed
	 * @return the value, empty for 0
	 */
	private static String positive(final int value) {
		return value > 0 ? String.valueOf(value) : EMPTY;
	}

	/**
	 * @param board The board area, which may be absent
	 * @return the FRU, or {@code null} when the board area names neither a manufacturer nor a product
	 */
	private static FruDevice fromBoard(final BoardInfo board) {
		if (board == null) {
			return null;
		}
		final FruDevice device = new FruDevice(
			orEmpty(board.getBoardManufacturer()),
			orEmpty(board.getBoardProductName()),
			serialNumber(board.getBoardSerialNumber(), board.getBoardPartNumber()),
			NO_DETAILS
		);
		return isBlank(device.vendor()) && isBlank(device.model()) ? null : device;
	}

	/**
	 * @param serial The serial number of the area
	 * @param part   The part or model number of the area
	 * @return the serial number, followed by {@code  - } and the part number when both exist; the part number alone
	 *         when there is no serial number
	 */
	private static String serialNumber(final String serial, final String part) {
		if (isBlank(serial)) {
			return isBlank(part) ? EMPTY : part;
		}
		return isBlank(part) ? serial : serial + " - " + part;
	}

	/**
	 * Builds one device state line per entity that has a sensor with an asserted state:
	 * {@code $deviceType;$deviceId;$deviceUniqueId;$vendor;$model;$serialNumber;$states;$memorySize;$memoryType;$cpuSpeed;$cpuInfo}.
	 *
	 * @param sensors      The sensors read from the BMC
	 * @param frusByEntity The listed FRUs, by entity key
	 * @return the device state lines, in the order the entities were first met
	 */
	private static Collection<String> stateLines(final List<Sensor> sensors, final Map<String, FruDevice> frusByEntity) {
		// The line of each entity up to its vendor, model and serial number, then its states
		final Map<String, String> headsByEntity = new LinkedHashMap<>();
		final Map<String, String> statesByEntity = new HashMap<>();

		for (final Sensor sensor : sensors) {
			final String states = buildStates(sensor);
			final String deviceType = DeviceDescription.getDeviceType(sensor.entity());

			// Nothing asserted, or an absent device
			if (isBlank(states) || states.toLowerCase().contains("=device absent") || isBlank(deviceType)) {
				continue;
			}

			// The entity instance number, as the signed byte of the record
			final byte instance = (byte) sensor.entityInstance();
			final String entity = entityKey(deviceType, instance);

			headsByEntity.computeIfAbsent(entity, key -> {
				final FruDevice fru = frusByEntity.getOrDefault(key, NO_FRU);
				return String.join(
					";",
					deviceType,
					Byte.toString(instance),
					key,
					orEmpty(fru.vendor()),
					orEmpty(fru.model()),
					orEmpty(fru.serialNumber())
				);
			});
			statesByEntity.merge(entity, states, (previous, next) -> previous + "|" + next);
		}

		return headsByEntity
			.entrySet()
			.stream()
			.map(entry ->
				String.join(
					";",
					entry.getValue(),
					statesByEntity.get(entry.getKey()),
					frusByEntity.getOrDefault(entry.getKey(), NO_FRU).details()
				)
			)
			.toList();
	}

	/**
	 * Builds the states of a sensor as {@code $sensorName=$state|$sensorName=$state...}.
	 *
	 * @param sensor The sensor
	 * @return the states, empty when none is asserted
	 */
	static String buildStates(final Sensor sensor) {
		final String name = sensor.name();
		// ponytail: HashSet order kept for byte-identical output; use distinct() once a cross-path fixture pins the format
		return sensor
			.states()
			.stream()
			.map(state -> name + "=" + state)
			.collect(Collectors.toSet())
			.stream()
			.collect(Collectors.joining("|"));
	}

	/**
	 * Builds the reading line of a Full Sensor record with a reading, in one of the units the connectors parse:
	 * {@code $type;$sensorId;$sensorName;$deviceUniqueId;$value[;$threshold1;$threshold2]}.
	 *
	 * @param sensor The sensor
	 * @return the line, or {@code null} when the sensor has no reading to report
	 */
	private static String readingLine(final Sensor sensor) {
		// Only a Full record carries a reading; the BMC says when it is unavailable (reading unavailable, scanning
		// disabled, no analog reading: IPMI 2.0 Table 35-15 and Table 43-1)
		if (
			!(sensor.record() instanceof FullSensorRecord) ||
			sensor.rawReading().isEmpty() ||
			sensor.rawReading().getAsInt() == NO_READING ||
			sensor.reading().isEmpty()
		) {
			return null;
		}

		final String deviceType = DeviceDescription.getDeviceType(sensor.entity());
		final SensorUnit unit = sensor.unit();
		final String name = sensor.name();
		if (deviceType == null || unit == null || name == null) {
			return null;
		}

		final String id = String.format("%04x", sensor.record().getId());
		final String entity = entityKey(deviceType, (byte) sensor.entityInstance());
		final double value = sensor.reading().getAsDouble();

		return switch (unit) {
			case DegreesC -> temperatureLine(sensor, id, name, entity, value, IDENTITY);
			case DegreesF -> temperatureLine(sensor, id, name, entity, value, FAHRENHEIT_TO_CELSIUS);
			case DegreesK -> temperatureLine(sensor, id, name, entity, value, KELVIN_TO_CELSIUS);
			case Volts -> line(
				"Voltage",
				id,
				name,
				entity,
				value * 1000,
				threshold(VOLTS_TO_MILLIVOLTS, sensor.lowerNonCritical(), sensor.lowerCritical(), sensor.lowerNonRecoverable()),
				threshold(VOLTS_TO_MILLIVOLTS, sensor.upperNonCritical(), sensor.upperCritical(), sensor.upperNonRecoverable())
			);
			case Rpm -> line(
				"Fan",
				id,
				name,
				entity,
				value,
				threshold(IDENTITY, sensor.lowerCritical(), sensor.lowerNonRecoverable()),
				threshold(IDENTITY, sensor.lowerNonCritical())
			);
			case Amps -> line("Current", id, name, entity, value);
			case Watts -> line("PowerConsumption", id, name, entity, value);
			case Joules -> line("Energy", id, name, entity, value);
			default -> null;
		};
	}

	/**
	 * @param toCelsius The conversion of the reading and the thresholds to degrees Celsius
	 * @return the temperature line, in degrees Celsius
	 */
	private static String temperatureLine(
		final Sensor sensor,
		final String id,
		final String name,
		final String entity,
		final double value,
		final DoubleUnaryOperator toCelsius
	) {
		return line(
			"Temperature",
			id,
			name,
			entity,
			toCelsius.applyAsDouble(value),
			threshold(toCelsius, sensor.upperNonCritical()),
			threshold(toCelsius, sensor.upperCritical(), sensor.upperNonRecoverable())
		);
	}

	private static String line(
		final String type,
		final String id,
		final String name,
		final String entity,
		final double value,
		final String... thresholds
	) {
		return Stream.concat(Stream.of(type, id, name, entity, String.valueOf(value)), Arrays.stream(thresholds)).collect(
			Collectors.joining(";")
		);
	}

	/**
	 * @param convert    The conversion to the unit of the line
	 * @param candidates The thresholds to try, in order
	 * @return the first defined threshold, converted and rounded; empty when the record defines none of them
	 */
	private static String threshold(final DoubleUnaryOperator convert, final OptionalDouble... candidates) {
		return Arrays.stream(candidates)
			.filter(OptionalDouble::isPresent)
			.findFirst()
			.map(threshold -> String.valueOf(Math.round(convert.applyAsDouble(threshold.getAsDouble()))))
			.orElse(EMPTY);
	}

	/**
	 * @param deviceType The entity type, in the wording of {@link DeviceDescription}
	 * @param instance   The entity instance number
	 * @return the key of the entity, {@code $deviceType $instance}, also its unique id in the output
	 */
	private static String entityKey(final String deviceType, final int instance) {
		return String.format("%s %d", deviceType, instance);
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static String orEmpty(final String value) {
		return isBlank(value) ? EMPTY : value;
	}
}
