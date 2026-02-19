package org.metricshub.engine.connector.deserializer.custom;

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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deserializes a size value to bytes. Accepts a plain number (interpreted as megabytes) or a
 * size string with unit (e.g. {@code 5Mb}, {@code 1gb}, {@code 512kb}, {@code 100byte}).
 * The result is always in bytes; no further conversion is needed by callers.
 * <p>
 * Examples: plain {@code 5} → {@code 5 * 1024 * 1024} bytes; {@code "5Mb"} → {@code 5 * 1024 * 1024};
 * {@code "1gb"} → {@code 1073741824}; {@code -1} or {@code "unlimited"} → {@code -1L}.
 * Can be used for any connector field that represents a size in bytes (e.g. {@code maxSizePerPoll}).
 */
public class SizeDeserializer extends JsonDeserializer<Long> {

	/** Sentinel for "unlimited" size. */
	public static final long UNLIMITED = -1L;

	private static final long KIB = 1024L;
	private static final long MIB = KIB * 1024L;
	private static final long GIB = MIB * 1024L;
	private static final long DEFAULT_BYTES = 5 * MIB;

	// Optional minus, digits, optional decimal part, optional whitespace, optional unit (letters)
	private static final Pattern SIZE_PATTERN = Pattern.compile(
		"\\s*(-?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)\\s*",
		Pattern.CASE_INSENSITIVE
	);

	@Override
	public Long deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return null;
		}

		JsonToken token = parser.currentToken();
		if (token == null) {
			return DEFAULT_BYTES;
		}

		if (token == JsonToken.VALUE_NUMBER_INT) {
			long value = parser.getLongValue();
			if (value < 0) {
				return UNLIMITED;
			}
			if (value == 0) {
				return DEFAULT_BYTES;
			}
			// Plain numeric value is interpreted as megabytes (legacy)
			return value * MIB;
		}

		if (token == JsonToken.VALUE_NUMBER_FLOAT) {
			double value = parser.getDoubleValue();
			if (value < 0) {
				return UNLIMITED;
			}
			if (value == 0) {
				return DEFAULT_BYTES;
			}
			// Plain numeric value is interpreted as megabytes (legacy)
			return (long) (value * MIB);
		}

		if (token == JsonToken.VALUE_STRING) {
			String raw = parser.getValueAsString();
			if (raw == null || raw.isBlank()) {
				return DEFAULT_BYTES;
			}
			String trimmed = raw.trim();
			if ("-1".equals(trimmed) || "unlimited".equalsIgnoreCase(trimmed)) {
				return UNLIMITED;
			}
			Matcher matcher = SIZE_PATTERN.matcher(trimmed);
			if (!matcher.matches()) {
				throw new InvalidFormatException(
					parser,
					"Invalid size: expected a number or a size string (e.g. 1mb, 1gb, 512kb). Got: " + raw,
					raw,
					Long.class
				);
			}
			double magnitude = Double.parseDouble(matcher.group(1));
			String unit = matcher.group(2).trim().toLowerCase();

			if (magnitude < 0) {
				return UNLIMITED;
			}
			if (magnitude == 0) {
				return DEFAULT_BYTES;
			}

			long multiplier = multiplierFromUnit(unit);
			long result = (long) (magnitude * multiplier);
			if (result < 0) {
				return UNLIMITED;
			}
			return result;
		}

		throw new InvalidFormatException(
			parser,
			"Invalid size: expected a number or a size string (e.g. 1mb, 1gb).",
			parser.getCurrentToken(),
			Long.class
		);
	}

	private static long multiplierFromUnit(String unit) {
		if (unit == null || unit.isEmpty()) {
			return MIB;
		}
		switch (unit) {
			case "b":
			case "byte":
			case "bytes":
				return 1L;
			case "k":
			case "kb":
			case "ko":
			case "kib":
			case "kilo":
			case "kilobyte":
			case "kilobytes":
				return KIB;
			case "m":
			case "mb":
			case "mo":
			case "mib":
			case "mega":
			case "megabyte":
			case "megabytes":
				return MIB;
			case "g":
			case "gb":
			case "go":
			case "gib":
			case "gbyte":
			case "gbytes":
			case "giga":
			case "gigabyte":
			case "gigabytes":
				return GIB;
			default:
				return MIB;
		}
	}
}
