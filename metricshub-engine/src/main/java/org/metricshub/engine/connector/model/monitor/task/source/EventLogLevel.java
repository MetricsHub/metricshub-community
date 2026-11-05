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

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents Windows Event Log levels.
 */
@AllArgsConstructor
public enum EventLogLevel {
	/** Error level (code 1). */
	ERROR(1, "Error"),
	/** Warning level (code 2). */
	WARNING(2, "Warning"),
	/** Information level (code 3). */
	INFORMATION(3, "Information"),
	/** Audit Success level (code 4). */
	AUDIT_SUCCESS(4, "Audit Success"),
	/** Audit Failure level (code 5). */
	AUDIT_FAILURE(5, "Audit Failure");

	@Getter
	private final int code;

	@Getter
	private final String alias;

	/**
	 * Map each EventLogLevel with a regular expression that detects it.
	 */
	private static final Map<EventLogLevel, Pattern> DETECTORS = Map.ofEntries(
		new SimpleEntry<>(ERROR, Pattern.compile("^err$|^error$")),
		new SimpleEntry<>(WARNING, Pattern.compile("^warn$|^warning$")),
		new SimpleEntry<>(INFORMATION, Pattern.compile("^info$|^information$")),
		new SimpleEntry<>(AUDIT_SUCCESS, Pattern.compile("^success$|^(audit\\s*)?success$")),
		new SimpleEntry<>(AUDIT_FAILURE, Pattern.compile("^(audit\\s*)?failure$|^fail$|^failure$"))
	);

	/**
	 * Detects an EventLogLevel from a string value (case-insensitive).
	 * Accepts numeric codes (1-5) or string aliases (e.g. "error", "warn", "info").
	 *
	 * @param value the string value to parse
	 * @return the corresponding EventLogLevel, or null if value is null
	 * @throws IllegalArgumentException if the value is not a supported level
	 */
	public static EventLogLevel detectFromString(final String value) {
		// Null and blank return null
		if (value == null || value.isBlank()) {
			return null;
		}

		final String lCaseValue = value.trim().toLowerCase();

		// If it's a number, treat it as a code
		if (lCaseValue.matches("\\d+")) {
			final EventLogLevel level = detectFromCode(Integer.parseInt(lCaseValue));
			if (level != null) {
				return level;
			}
			throw new IllegalArgumentException("'" + value + "' is not a supported Event Log level.");
		}

		// Check all regex in DETECTORS to see which one matches
		for (Map.Entry<EventLogLevel, Pattern> detector : DETECTORS.entrySet()) {
			if (detector.getValue().matcher(lCaseValue).find()) {
				return detector.getKey();
			}
		}

		// No match => Exception
		throw new IllegalArgumentException("'" + value + "' is not a supported Event Log level.");
	}

	/**
	 * Detects an EventLogLevel from its numeric code.
	 *
	 * @param code the numeric code (1-5)
	 * @return the corresponding EventLogLevel, or null if not found
	 */
	public static EventLogLevel detectFromCode(final int code) {
		return Stream.of(values()).filter(element -> element.getCode() == code).findFirst().orElse(null);
	}
}
