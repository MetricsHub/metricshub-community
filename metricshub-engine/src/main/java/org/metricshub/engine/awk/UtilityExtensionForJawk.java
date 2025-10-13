package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.ext.annotations.JawkFunction;
import org.metricshub.jawk.jrt.JRT;

/**
 * This class implements the {@link JawkExtension} contract
 */
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class UtilityExtensionForJawk extends AbstractExtension implements JawkExtension {

	public static final UtilityExtensionForJawk INSTANCE = new UtilityExtensionForJawk();

	@Override
	public String getExtensionName() {
		return "MetricsHub";
	}

	/**
	 * Converts a byte count (base-2) given as a string to a human-readable form using
	 * IEC units (B, KiB, MiB, GiB, TiB, PiB, EiB).
	 * <p>
	 * If {@code o} cannot be parsed as a number: returns zero.
	 *
	 * @param o byte count as a String or Number
	 * @return human-readable string (e.g., {@code "1.00 KiB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("bytes2HumanFormatBase2")
	public String bytes2HumanFormatBase2(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB" };
		return humanFormat(value, 1024.0, units);
	}

	/**
	 * Converts a byte count (base-10) given as a string to a human-readable form using
	 * SI units (B, KB, MB, GB, TB, PB, EB).
	 *
	 * @param o byte count as a String or Number
	 * @return human-readable string (e.g., {@code "1.00 MB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("bytes2HumanFormatBase10")
	public String bytes2HumanFormatBase10(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
		return humanFormat(value, 1000.0, units);
	}

	/**
	 * Converts a quantity in mebibytes (MiB) given as a string to a human-readable form
	 * using IEC units (MiB, GiB, TiB, PiB, EiB).
	 *
	 * @param o size in MiB as a String or Number
	 * @return human-readable string (e.g., {@code "2.00 GiB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("mebiBytes2HumanFormat")
	public String mebiBytes2HumanFormat(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "MiB", "GiB", "TiB", "PiB", "EiB" };
		return humanFormat(value, 1024.0, units);
	}

	/**
	 * Converts a frequency in megahertz (MHz) given as a string to a human-readable form
	 * using SI units (MHz, GHz, THz, PHz, EHz).
	 *
	 * @param o frequency in MHz as a String or Number
	 * @return human-readable string (e.g., {@code "3.20 GHz"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("megaHertz2HumanFormat")
	public String megaHertz2HumanFormat(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "MHz", "GHz", "THz", "PHz", "EHz" };
		return humanFormat(value, 1000.0, units);
	}

	/**
	 * Formats {@code value} with the best-fitting unit from {@code units}, dividing by {@code base}
	 * while {@code value >= base}. Uses two decimals.
	 *
	 * @param value numeric value to scale
	 * @param base 1000 (SI) or 1024 (IEC)
	 * @param units ordered units from smallest to largest
	 * @return formatted value with unit (e.g., "1.23 GiB")
	 */
	private String humanFormat(double value, double base, String[] units) {
		int i = 0;
		while (value >= base && i < units.length - 1) {
			value /= base;
			i++;
		}
		return String.format(getSettings().getLocale(), "%.2f %s", value, units[i]);
	}

	/**
	 * Joins strings with a separator.
	 *
	 * @param sep separator to place between parts
	 * @param parts String elements to be joined
	 * @return All parts joined with separator
	 */
	@JawkFunction("join")
	public String join(String sep, String... parts) {
		if (parts == null || parts.length == 0) {
			return "";
		}
		String actualSep = sep == null ? "" : sep;
		StringBuilder result = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			result.append(actualSep).append(parts[i] == null ? "" : parts[i]);
		}
		return result.toString();
	}
}
