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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.COMMA;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.springframework.util.Assert.isTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;

/**
 * Helper class for working with strings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class StringHelper {

	/**
	 * The regular expression pattern used to extract the column names from a SELECT
	 */
	private static final Pattern SQL_PATTERN = Pattern.compile("(?i)select\\s+(.*?)\\s+from", Pattern.CASE_INSENSITIVE);

	/**
	 * Execute the given callable to get the resulting Object as String value.
	 *
	 * @param call         Callable providing a value.
	 * @param defaultValue The default value to return if the callable returns null or empty.
	 * @return String value.
	 */
	public static String getValue(final Callable<Object> call, final String defaultValue) {
		final Object result = callIfPossible(call);
		final String value = (result != null) ? result.toString() : null;
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	/**
	 * Call the callable and return the result. Return <code>null</code> if an exception occurs
	 *
	 * @param call callback to run
	 * @return Object value
	 */
	private static Object callIfPossible(final Callable<Object> call) {
		try {
			return call.call();
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Iterates over all the throwable causes and extract all the messages.
	 *
	 * @param throwable The {@link Throwable} instance we wish to process
	 * @return String value
	 */
	public static String getStackMessages(final Throwable throwable) {
		if (throwable == null) {
			return EMPTY;
		}

		return new StringBuilder(throwable.getClass().getSimpleName())
			.append(": ")
			.append(throwable.getMessage())
			.append("\n")
			.append(
				Stream
					.iterate(throwable, Objects::nonNull, Throwable::getCause)
					.filter(th -> th != throwable)
					.map(th -> String.format("Caused by %s: %s", th.getClass().getSimpleName(), th.getMessage()))
					.collect(Collectors.joining("\n"))
			)
			.toString();
	}

	/**
	 * Format the given HTTP headers map as the following example:<br>
	 * <code>
	 * Content-Type: application/json<br>
	 * Connection: keep-alive
	 * </code>
	 *
	 * @param headers Key-Value collection
	 * @return String value
	 */
	public static String prettyHttpHeaders(final Map<String, String> headers) {
		if (headers == null) {
			return EMPTY;
		}

		return headers
			.entrySet()
			.stream()
			.map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue()))
			.sorted()
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Add the given prefix and value to the {@link StringJoiner} instance. <code>null</code> value is not added.
	 *
	 * @param <T>            the type of the value to be added
	 * @param stringJoiner   {@link StringJoiner} instance used to append the prefix and the value.
	 * @param prefix         The value prefix.
	 * @param value          The value to add.
	 */
	public static <T> void addNonNull(
		@NonNull final StringJoiner stringJoiner,
		@NonNull final String prefix,
		final T value
	) {
		if (value != null) {
			stringJoiner.add(new StringBuilder(prefix).append(value));
		}
	}

	/**
	 * Replace each substring of the <code>template</code> that matches the literal
	 * <code>macro</code> sequence with the value specified by the <code>replacementSupplier</code>.
	 *
	 * @param macro               The sequence of char values to be replaced
	 * @param replacementSupplier The supplier of the replacement sequence
	 * @param template            The template to replace
	 * @return String value
	 */
	public static String replace(
		@NonNull final String macro,
		@NonNull final Supplier<String> replacementSupplier,
		@NonNull final String template
	) {
		if (template.contains(macro)) {
			return template.replace(macro, getValue(replacementSupplier::get, macro));
		}

		return template;
	}

	/**
	 * Replace each substring of the <code>template</code> that matches the
	 * literal <code>macro</code> sequence with the specified literal
	 * <code>replacement</code> sequence.
	 *
	 * @param macro       The sequence of char values to be replaced
	 * @param replacement The replacement sequence
	 * @param template    The template to replace
	 * @return String value
	 */
	public static String replace(@NonNull final String macro, final String replacement, @NonNull final String template) {
		if (template.contains(macro) && replacement != null) {
			return template.replace(macro, replacement);
		}

		return template;
	}

	/**
	 * Convert the given value to a string representation. If the value is a collection or an array,
	 * it is transformed into a CSV (Comma-Separated Values) string.
	 *
	 * @param value The value to be converted to a string.
	 * @return The string representation of the value, or a CSV string if the value is a collection or an array.
	 */
	public static String stringify(final Object value) {
		return stringify(value, COMMA);
	}

	/**
	 * Convert the given value to a string representation. If the value is a collection or an array, it is transformed into a string
	 * using the specified separator.
	 *
	 * @param value     The value to be converted to a string.
	 * @param separator The separator to be used when converting a collection or an array to a string.
	 * @return The string representation of the value, or a string using the specified separator if the value is a collection
	 */
	public static String stringify(final Object value, final String separator) {
		if (value == null) {
			// Handle null input
			return EMPTY;
		} else if (value instanceof Collection<?> collection) {
			// If the input is a List, convert it to a CSV string
			return collection
				.stream()
				.map(item -> item != null ? item.toString() : EMPTY)
				.collect(Collectors.joining(separator));
		} else if (value.getClass().isArray()) {
			// If the input is an array, convert it to a CSV string
			Object[] array = (Object[]) value;
			return Arrays
				.stream(array)
				.map(item -> item != null ? item.toString() : EMPTY)
				.collect(Collectors.joining(separator));
		} else {
			// For any other type of value, simply convert it to a string
			return value.toString();
		}
	}

	/**
	 * Checks if the specified string is non-null and non-blank.
	 *
	 * @param value The string to be checked.
	 * @return {@code true} if the string is non-null and contains at least one non-whitespace character, {@code false} otherwise.
	 */
	public static boolean nonNullNonBlank(final String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Validate the attribute against a predetermined test
	 *
	 * @param attribute       Value getting compared
	 * @param errorChecker    Logic test comparing our value
	 * @param messageSupplier error message supplier
	 * @throws InvalidConfigurationException in case the validation fail
	 */
	public static <T> void validateConfigurationAttribute(
		final T attribute,
		final Predicate<T> errorChecker,
		final Supplier<String> messageSupplier
	) throws InvalidConfigurationException {
		if (errorChecker.test(attribute)) {
			log.error(messageSupplier.get());
			throw new InvalidConfigurationException(messageSupplier.get());
		}
	}

	/**
	 * Convert a string to be searched in a case-insensitive regex.
	 *
	 * @param value The string to search. (mandatory)
	 * @return The case-insensitive regex for the given string.
	 */
	public static String protectCaseInsensitiveRegex(final String value) {
		isTrue(value != null && !value.isEmpty(), "Input string must not be null or empty.");
		return value.isBlank() ? value : "(?i)" + Pattern.quote(value);
	}

	/**
	 * Extracts column names from a SELECT query.
	 *
	 * @param query the SQL query string
	 * @return an array of column names, or an empty array if none are found
	 */
	public static String[] extractColumns(String query) {
		// Normalize the query by ignoring case for SELECT and FROM
		final Matcher matcher = SQL_PATTERN.matcher(query.trim());

		if (matcher.find()) {
			return Stream.of(matcher.group(1).trim().split(",")).map(String::trim).toArray(String[]::new);
		}
		return new String[] {}; // Return empty if no match is found
	}
}
