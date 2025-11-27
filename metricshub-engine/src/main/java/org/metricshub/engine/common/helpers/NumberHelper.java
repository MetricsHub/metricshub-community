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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;

/**
 * Helper class for number-related operations.
 */
public class NumberHelper {

	/**
	 * Pattern to detect integers with trailing zeros.
	 */
	public static final Pattern INTEGER_DETECT_PATTERN = Pattern.compile("^(-?\\d+)(\\.0*)$");

	private static final DecimalFormat DECIMAL_FORMAT;

	static {
		// By default we use the US Locale.
		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);

		// The decimal separator is '.'
		symbols.setDecimalSeparator('.');

		// Create the decimalFormat
		DECIMAL_FORMAT = new DecimalFormat("#########.###", symbols);
	}

	private NumberHelper() {}

	/**
	 * Parse the given double value, if the parsing fails return the default value
	 *
	 * @param value        The value we wish to parse
	 * @param defaultValue The default value to return if the parsing fails
	 * @return {@link Double} value
	 */
	public static Double parseDouble(String value, Double defaultValue) {
		try {
			return Double.parseDouble(value);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/**
	 * Parse the given integer value, if the parsing fails return the default value
	 *
	 * @param value        The value we wish to parse
	 * @param defaultValue The default value to return if the parsing fails
	 * @return {@link Integer} value
	 */
	public static Integer parseInt(String value, Integer defaultValue) {
		try {
			return Integer.parseInt(value);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/**
	 * Round the given double value using the specified decimal places
	 *
	 * @param value        The value we wish to round
	 * @param places       The required decimal places expected as positive
	 * @param roundingMode The rounding behavior used by the {@link BigDecimal} object for the numerical operations
	 * @return double value
	 */
	public static double round(final double value, final int places, final RoundingMode roundingMode) {
		if (places < 0) {
			throw new IllegalArgumentException();
		}

		return BigDecimal.valueOf(value).setScale(places, roundingMode).doubleValue();
	}

	/**
	 * Removes the fractional part and the decimal point of the given state if the
	 * fractional part contains only 0 after the decimal point. The state is trimmed
	 * and converted to lower case
	 *
	 * @param state the value we wish to process
	 * @return String value
	 */
	public static String cleanUpEnumInput(String state) {
		if (state == null) {
			return null;
		}

		state = state.trim().toLowerCase();

		final Matcher matcher = INTEGER_DETECT_PATTERN.matcher(state);

		if (matcher.find()) {
			return state.substring(0, state.indexOf(matcher.group(2)));
		}

		return state;
	}

	/**
	 * Format the given value as String
	 *
	 * @param n      numeric value to format
	 * @param format string format used to format decimal parts
	 * @return {@link String} value
	 */
	public static String formatNumber(final Number n, @NonNull final String format) {
		final String stringValue = DECIMAL_FORMAT.format(n);
		final String[] valueParts = stringValue.split("\\.");
		final String leftPart = valueParts[0];
		final String rightPart = valueParts.length == 2 ? "." + valueParts[1] : "";
		return String.format(format, leftPart, rightPart);
	}

	/**
	 * Format the given value as String
	 *
	 * @param n numeric value to format
	 * @return {@link String} value
	 */
	public static String formatNumber(final Number n) {
		return DECIMAL_FORMAT.format(n);
	}

	/**
	 * Returns the given number if it is positive; otherwise returns the default value.
	 *
	 * @param n             the number to check
	 * @param defaultValue  the fallback value to return
	 * @return {@code n} if positive, otherwise {@code defaultValue}
	 */
	public static Number getPositiveOrDefault(final Number n, @NonNull final Number defaultValue) {
		return n != null && n.doubleValue() > 0.0 ? n : defaultValue;
	}

	/**
	 * Checks if a given string is numeric.
	 *
	 * @param strNum the string to check
	 * @return {@code true} if the string is numeric, {@code false} otherwise
	 */
	public static boolean isNumeric(final String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			Double.parseDouble(strNum);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
