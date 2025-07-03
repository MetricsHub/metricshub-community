package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enumeration representing known monitor types.
 * Each enum constant corresponds to a specific monitor type with a unique key.
 */
@Getter
@AllArgsConstructor
public enum KnownMonitorType {
	/**
	 * Connector monitor type.
	 */
	CONNECTOR("connector"),
	/**
	 * Host monitor type.
	 */
	HOST("host"),
	/**
	 * Battery monitor type.
	 */
	BATTERY("battery"),
	/**
	 * Blade monitor type.
	 */
	BLADE("blade"),
	/**
	 * CPU monitor type.
	 */
	CPU("cpu"),
	/**
	 * Disk controller monitor type.
	 */
	DISK_CONTROLLER("disk_controller"),
	/**
	 * Enclosure monitor type.
	 */
	ENCLOSURE("enclosure"),
	/**
	 * Fan monitor type.
	 */
	FAN("fan"),
	/**
	 * GPU monitor type.
	 */
	GPU("gpu"),
	/**
	 * LED monitor type.
	 */
	LED("led"),
	/**
	 * Logical disk monitor type.
	 */
	LOGICAL_DISK("logical_disk"),
	/**
	 * LUN monitor type.
	 */
	LUN("lun"),
	/**
	 * Memory monitor type.
	 */
	MEMORY("memory"),
	/**
	 * Network monitor type.
	 */
	NETWORK("network"),
	/**
	 * Other device monitor type.
	 */
	OTHER_DEVICE("other_device"),
	/**
	 * Physical disk monitor type.
	 */
	PHYSICAL_DISK("physical_disk"),
	/**
	 * Power supply monitor type.
	 */
	POWER_SUPPLY("power_supply"),
	/**
	 * Robotics monitor type.
	 */
	ROBOTICS("robotics"),
	/**
	 * Tape drive monitor type.
	 */
	TAPE_DRIVE("tape_drive"),
	/**
	 * Temperature monitor type.
	 */
	TEMPERATURE("temperature"),
	/**
	 * Virtual machine monitor type.
	 */
	VM("vm"),
	/**
	 * Voltage monitor type.
	 */
	VOLTAGE("voltage");

	private String key;

	/**
	 * Set of all known monitor type keys.
	 */
	public static final Set<String> KEYS = Stream
		.of(KnownMonitorType.values())
		.map(KnownMonitorType::getKey)
		.collect(Collectors.toSet());

	/**
	 * Retrieves the {@code KnownMonitorType} enum constant that matches the given string representation,
	 * ignoring case.
	 *
	 * @param monitorType the string representation to match against enum constants.
	 * @return the matching value which is an Optional of {@code KnownMonitorType}.
	 */
	public static Optional<KnownMonitorType> fromString(final String monitorType) {
		return Stream.of(KnownMonitorType.values()).filter(type -> type.key.equalsIgnoreCase(monitorType)).findFirst();
	}

	/**
	 * Checks if the given monitor type is a connector type.
	 *
	 * @param monitorType the monitor type to check
	 * @return true if the monitor type is a connector, false otherwise
	 */
	public static boolean isConnector(final String monitorType) {
		return CONNECTOR.key.equalsIgnoreCase(monitorType);
	}
}
