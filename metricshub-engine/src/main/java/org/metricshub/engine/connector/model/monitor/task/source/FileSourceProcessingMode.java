package org.metricshub.engine.connector.model.monitor.task.source;

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

/**
 * Represents the file source processing mode.
 */
public enum FileSourceProcessingMode {
	/** Flat mode - processes files as flat content. */
	FLAT,
	/** Log mode - processes files as log files. */
	LOG;

	/**
	 * Interprets a string value and returns the corresponding FileSourceMode (case-insensitive).
	 *
	 * @param value The string value to interpret
	 * @return The corresponding FileSourceMode
	 * @throws IllegalArgumentException if the value is not a supported mode
	 */
	public static FileSourceProcessingMode interpretValueOf(final String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("FileSourceMode value cannot be null or blank.");
		}

		final String lowerCaseValue = value.trim().toLowerCase();

		if ("flat".equals(lowerCaseValue)) {
			return FLAT;
		}

		if ("log".equals(lowerCaseValue)) {
			return LOG;
		}

		throw new IllegalArgumentException(
			"'" + value + "' is not a supported FileSourceMode. Supported values: 'Flat', 'Log'."
		);
	}
}
