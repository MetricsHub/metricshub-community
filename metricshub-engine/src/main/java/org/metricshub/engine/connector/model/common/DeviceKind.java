package org.metricshub.engine.connector.model.common;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing different kinds of devices that connectors can handle.
 */
@AllArgsConstructor
public enum DeviceKind {
	/**
	 * HP Open VMS.
	 */
	VMS("HP Open VMS"),
	/**
	 * HP Tru64.
	 */
	TRU64("HP Tru64"),
	/**
	 * HP-UX.
	 */
	HPUX("HP-UX"),
	/**
	 * IBM AIX.
	 */
	AIX("IBM AIX"),
	/**
	 * Linux.
	 */
	LINUX("Linux"),
	/**
	 * Management.
	 */
	OOB("Management"),
	/**
	 * Microsoft Windows.
	 */
	WINDOWS("Microsoft Windows"),
	/**
	 * Network.
	 */
	NETWORK("Network"),
	/**
	 * Storage.
	 */
	STORAGE("Storage"),
	/**
	 * Oracle Solaris.
	 */
	SOLARIS("Oracle Solaris"),
	/**
	 * Other.
	 */
	OTHER("Other");

	@Getter
	private String displayName;

	/**
	 * A set containing all the enumerated device kinds.
	 */
	public static final Set<DeviceKind> DEVICE_KINDS = Set.of(DeviceKind.values());

	/**
	 * Map each OsType with a regular expression that detects it
	 */
	private static final Map<DeviceKind, Pattern> DETECTORS = Map.ofEntries(
		new SimpleEntry<>(LINUX, Pattern.compile("^lin$|^linux$")),
		new SimpleEntry<>(WINDOWS, Pattern.compile("^(microsoft\\s*)?windows$|^win$|^nt$")),
		new SimpleEntry<>(
			OOB,
			Pattern.compile("^management$|^mgmt$|^management\\s*card$|^out-of-band$|^out\\s*of\\s*band$|^oob$")
		),
		new SimpleEntry<>(NETWORK, Pattern.compile("^network$|^switch$")),
		new SimpleEntry<>(STORAGE, Pattern.compile("^storage$|^san$|^library$|^array$")),
		new SimpleEntry<>(VMS, Pattern.compile("^vms$|^(hp\\s*)?open\\s*vms$")),
		new SimpleEntry<>(TRU64, Pattern.compile("^tru64$|^osf1$|^hp\\s*tru64\\s*unix$")),
		new SimpleEntry<>(HPUX, Pattern.compile("^hp-ux$|^hpux$|^hp$")),
		new SimpleEntry<>(AIX, Pattern.compile("^ibm(\\s*|-)aix$|^aix$|^rs6000$")),
		new SimpleEntry<>(SOLARIS, Pattern.compile("^((sun|oracle)\\s*)?solaris$|^sunos$")),
		new SimpleEntry<>(OTHER, Pattern.compile("^other$"))
	);

	/**
	 * Detects the {@link DeviceKind} using the value defined in the connector code.
	 *
	 * @param value The value to detect.
	 * @return The corresponding {@link DeviceKind} instance.
	 * @throws IllegalArgumentException If the provided value is not a supported device kind.
	 */
	public static DeviceKind detect(final String value) {
		// Null returns null
		if (value == null) {
			return null;
		}

		// Check all regex in DETECTORS to see which one matches
		final String lCaseValue = value.trim().toLowerCase();
		for (Map.Entry<DeviceKind, Pattern> detector : DETECTORS.entrySet()) {
			if (detector.getValue().matcher(lCaseValue).find()) {
				return detector.getKey();
			}
		}

		// No match => Exception
		throw new IllegalArgumentException("'" + value + "' is not a supported device kind.");
	}
}
