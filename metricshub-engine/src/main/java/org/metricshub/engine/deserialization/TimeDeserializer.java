package org.metricshub.engine.deserialization;

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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.NumberHelper;

/**
 * Custom JSON deserializer for converting timeout values to seconds.
 * Extends {@link JsonDeserializer} and handles deserialization of timeout values in various formats.
 */
public class TimeDeserializer extends JsonDeserializer<Long> {

	/**
	 * Deserialize the JSON value to a {@code Long} representing the timeout in seconds.
	 *
	 * @param parser The JSON parser.
	 * @param ctxt   The deserialization context.
	 * @return The timeout value in seconds.
	 * @throws IOException If an I/O error occurs.
	 */
	@Override
	public Long deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return null;
		}

		try {
			return interpretValueOf(parser.getValueAsString());
		} catch (IllegalArgumentException e) {
			var location = parser.currentLocation();
			throw new IOException(
				"line %s, column %s: %s".formatted(location.getLineNr(), location.getColumnNr(), e.getMessage())
			);
		}
	}

	/**
	 * Interpret the timeout value to have it in seconds.
	 * The timeout is expected to be ordered from the biggest unit to the smallest : y > w > d > h > m > s > ms.
	 *
	 * @param value The value we wish to parse and interpret
	 * @return long value
	 */
	public static long interpretValueOf(@NonNull final String value) {
		final long longValue = NumberHelper.parseInt(value, -1);
		if (longValue != -1) {
			return longValue;
		}

		final Matcher matcher = Pattern
			.compile(
				"\\s*(?:(\\d+)\\s*(?:years?|yrs?|y))?" +
				"\\s*(?:(\\d+)\\s*(?:weeks?|wks?|w))?" +
				"\\s*(?:(\\d+)\\s*(?:days?|d))?" +
				"\\s*(?:(\\d+)\\s*(?:hours?|hrs?|h))?" +
				"\\s*(?:(\\d+)\\s*(?:minutes?|mins?|m))?" +
				"\\s*(?:(\\d+)\\s*(?:seconds?|secs?|s))?" +
				"\\s*(?:(\\d+)\\s*(?:milliseconds?|millisecs?|ms))?" +
				"\\s*",
				Pattern.CASE_INSENSITIVE
			)
			.matcher(value);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Not valid duration: " + value);
		}

		final long years = (matcher.start(1) == -1 ? 0 : Integer.parseInt(matcher.group(1)));
		final long weeks = (matcher.start(2) == -1 ? 0 : Integer.parseInt(matcher.group(2)));
		final long days = (matcher.start(3) == -1 ? 0 : Integer.parseInt(matcher.group(3)));
		final long hours = (matcher.start(4) == -1 ? 0 : Integer.parseInt(matcher.group(4)));
		final long minutes = (matcher.start(5) == -1 ? 0 : Integer.parseInt(matcher.group(5)));
		final long seconds = (matcher.start(6) == -1 ? 0 : Integer.parseInt(matcher.group(6)));
		final long milliseconds = (matcher.start(7) == -1 ? 0 : Integer.parseInt(matcher.group(7)));

		// @formatter:off
		// CHECKSTYLE:OFF
		return (
			years * 60 * 60 * 24 * 365 +
			weeks * 60 * 60 * 24 * 7 +
			days * 60 * 60 * 24 +
			hours * 60 * 60 +
			minutes * 60 +
			seconds +
			milliseconds / 1000
		);
		// CHECKSTYLE:ON
		// @formatter:on
	}
}
