package org.metricshub.engine.tool;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One argument of a {@link ToolDefinition}, as declared by a connector or a protocol extension.
 *
 * @param type         the JSON schema type of the value: {@code string}, {@code integer}, {@code number} or
 *                     {@code boolean}. {@code string} when omitted.
 * @param description  what the value is for, as read by the LLM
 * @param required     whether an invocation must supply the value
 * @param defaultValue the value used when an invocation does not supply one
 * @param pattern      a regular expression the whole value must match, {@code null} for any value
 * @param enumValues   the only values accepted, {@code null} or empty for any value
 */
public record ToolParameter(
	String type,
	String description,
	boolean required,
	@JsonProperty("default") String defaultValue,
	String pattern,
	@JsonProperty("enum") List<String> enumValues
) implements Serializable {
	/**
	 * The type used when a parameter declares none.
	 */
	public static final String STRING = "string";

	/**
	 * The JSON schema types a parameter may declare.
	 */
	public static final List<String> TYPES = List.of(STRING, "integer", "number", "boolean");

	private static final Pattern INTEGER = Pattern.compile("-?\\d+");
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");

	/**
	 * How long a value may take to match its pattern.
	 */
	private static final long MATCH_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

	/**
	 * The characters that quote, expand, chain, redirect, glob or comment in a shell (POSIX, PowerShell, cmd) or
	 * a query: a value inserted unescaped may contain none of them.
	 */
	private static final String METACHARACTERS = " \t\r\n'\"`$;|&<>(){}[]*?!#~%^\\";

	/**
	 * Matches a value holding none of the {@link #METACHARACTERS}, each written {@code \x{hex}}, and not starting with
	 * {@code -}, which a command would read as an option, as lookaheads: {@code \z} rather than {@code $}, which would
	 * also match before a final line break.
	 */
	private static final String WITHOUT_METACHARACTERS = METACHARACTERS.chars()
		.mapToObj("\\x{%x}"::formatted)
		.collect(Collectors.joining("", "(?!-)(?=[^", "]*\\z)"));

	/**
	 * Normalizes the type, a missing or blank type being {@value #STRING}, and checks the pattern compiles and the
	 * default value is valid.
	 *
	 * @param type         the JSON schema type of the value
	 * @param description  what the value is for
	 * @param required     whether an invocation must supply the value
	 * @param defaultValue the value used when an invocation does not supply one
	 * @param pattern      a regular expression the whole value must match
	 * @param enumValues   the only values accepted
	 */
	public ToolParameter {
		if (type == null || type.isBlank()) {
			type = STRING;
		}
		type = type.trim().toLowerCase(Locale.ROOT);
		if (!TYPES.contains(type)) {
			// Copied verbatim into the advertised JSON schema, where an unknown type would fail the whole tool list
			throw new IllegalArgumentException("Unknown tool parameter type '%s': expected one of %s".formatted(type, TYPES));
		}
		if (pattern != null) {
			// A PatternSyntaxException is an IllegalArgumentException
			Pattern.compile(pattern);
		}
		enumValues = enumValues == null || enumValues.isEmpty() ? null : List.copyOf(enumValues);
		if (enumValues != null) {
			// Advertised as values of the type, each valid for the pattern too, or the assistant could pick one refused
			for (final String value : enumValues) {
				check("enum", value, type, pattern, null);
			}
		}
		if (defaultValue != null) {
			check("default", defaultValue, type, pattern, enumValues);
		}
	}

	/**
	 * A parameter of the given type taking any value.
	 *
	 * @param type         the JSON schema type of the value
	 * @param description  what the value is for
	 * @param required     whether an invocation must supply the value
	 * @param defaultValue the value used when an invocation does not supply one
	 */
	public ToolParameter(final String type, final String description, final boolean required, final String defaultValue) {
		this(type, description, required, defaultValue, null, null);
	}

	/**
	 * @return whether the connector constrained the value: a number or a boolean, one of an enum, or a string
	 *         matching a pattern
	 */
	@JsonIgnore
	public boolean isConstrained() {
		return !STRING.equals(type) || enumValues != null || pattern != null;
	}

	/**
	 * A string parameter escaped into a command line stays one word, but a command would still read a value starting
	 * with {@code -} as an option ({@code find ... -delete}): such a value is refused on top of the pattern.
	 *
	 * @return this parameter, whose pattern also refuses a leading {@code -} if it is a string without enum
	 * @throws IllegalArgumentException if the default value starts with {@code -}
	 */
	public ToolParameter withoutLeadingDash() {
		if (!STRING.equals(type) || enumValues != null) {
			return this;
		}
		final String constrained = "(?!-)" + (pattern == null ? "[\\s\\S]*" : "(?:" + pattern + ")");
		return new ToolParameter(type, description, required, defaultValue, constrained, null);
	}

	/**
	 * A string parameter in the values of a compute ({@code append}, {@code replace}, a {@code valueList}) is data,
	 * but the table around reads {@code ;} as a column separator, {@code ,} as an entry separator, {@code $n} as
	 * a column and a line break as a row separator: such characters are refused on top of the pattern.
	 *
	 * @return this parameter, whose pattern also refuses {@code ; , $} and line breaks if it is a string without enum
	 * @throws IllegalArgumentException if the default value holds one of them
	 */
	public ToolParameter withoutTableSyntax() {
		if (!STRING.equals(type) || enumValues != null) {
			return this;
		}
		final String constrained = "(?=[^;,$\\r\\n]*\\z)" + (pattern == null ? "[\\s\\S]*" : "(?:" + pattern + ")");
		return new ToolParameter(type, description, required, defaultValue, constrained, null);
	}

	/**
	 * A string parameter inserted unescaped is only safe without the shell and query metacharacters: whether its
	 * pattern lets them through cannot be told in general, so they are refused on top of it.
	 *
	 * @return this parameter, whose pattern also refuses the {@link #METACHARACTERS} if it is a string without enum
	 * @throws IllegalArgumentException if the default value holds a metacharacter
	 */
	public ToolParameter withoutMetacharacters() {
		if (!STRING.equals(type) || enumValues != null) {
			return this;
		}
		final String constrained = WITHOUT_METACHARACTERS + (pattern == null ? "" : "(?:" + pattern + ")");
		return new ToolParameter(type, description, required, defaultValue, constrained, null);
	}

	/**
	 * Checks an argument against the type, the enum and the pattern of the parameter. Whatever the parameter, the
	 * value may not contain <code>%{</code> or <code>${</code>: the macros (credentials, sources, files) are expanded after
	 * the arguments are inserted, and would be expanded inside them.
	 *
	 * @param name  the parameter name, for the error message
	 * @param value the argument
	 * @throws IllegalArgumentException if the argument is not valid, the value itself not being repeated
	 */
	public void validate(final String name, final String value) {
		check(name, value, type, pattern, enumValues);
	}

	private static void check(
		final String name,
		final String value,
		final String type,
		final String pattern,
		final List<String> enumValues
	) {
		if (value.contains("%{") || value.contains("${")) {
			throw new IllegalArgumentException(
				"The argument %s may not contain %%{ or ${ (write $VAR rather than ${VAR})".formatted(name)
			);
		}
		// No source takes them, and XML 1.0 forbids them even escaped. An unpaired surrogate reads as one code point
		if (value.codePoints().anyMatch(ToolParameter::isForbiddenCharacter)) {
			throw new IllegalArgumentException(
				"The argument %s may not contain control characters or unpaired surrogates".formatted(name)
			);
		}
		final boolean typed = switch (type) {
			case "integer" -> INTEGER.matcher(value).matches();
			case "number" -> NUMBER.matcher(value).matches();
			case "boolean" -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
			default -> true;
		};
		if (!typed) {
			throw new IllegalArgumentException("The argument %s must be of type %s".formatted(name, type));
		}
		if (enumValues != null && enumValues.stream().noneMatch(entry -> sameValue(type, entry, value))) {
			throw new IllegalArgumentException("The argument %s must be one of %s".formatted(name, enumValues));
		}
		if (pattern != null && !matches(pattern, value, name)) {
			throw new IllegalArgumentException("The argument %s must match the pattern %s".formatted(name, pattern));
		}
	}

	/**
	 * @param pattern a regular expression
	 * @param value   the argument
	 * @param name    the parameter name
	 * @return whether the argument matches the whole pattern
	 * @throws IllegalArgumentException if the match takes too long or too deep a recursion
	 */
	private static boolean matches(final String pattern, final String value, final String name) {
		try {
			return Pattern.compile(pattern).matcher(TimedValue.of(value, name)).matches();
		} catch (StackOverflowError e) {
			// A repeated group, (x|y)*, recurses once per repetition: a long value exhausts the stack
			throw new IllegalArgumentException("The argument %s is too long to match its pattern".formatted(name));
		}
	}

	/**
	 * @param c a code point
	 * @return whether it is a control character other than a tab or a line break, a noncharacter, or a surrogate,
	 *         which a code point is only when unpaired
	 */
	private static boolean isForbiddenCharacter(final int c) {
		final boolean surrogate = c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE;
		return (c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0xFFFE || c == 0xFFFF || surrogate;
	}

	/**
	 * A value a regular expression may only read until a deadline: a pattern backtracking without end on a crafted
	 * value fails instead of holding the request.
	 *
	 * @param value    the value
	 * @param deadline the {@link System#nanoTime()} past which reading fails
	 * @param name     the parameter name, for the error message
	 */
	private record TimedValue(String value, long deadline, String name) implements CharSequence {
		/**
		 * @param value the value
		 * @param name  the parameter name
		 * @return the value, readable for {@link #MATCH_TIMEOUT_NANOS} from now
		 */
		static TimedValue of(final String value, final String name) {
			return new TimedValue(value, System.nanoTime() + MATCH_TIMEOUT_NANOS, name);
		}

		@Override
		public char charAt(final int index) {
			if (System.nanoTime() > deadline) {
				throw new IllegalArgumentException("The argument %s takes too long to match its pattern".formatted(name));
			}
			return value.charAt(index);
		}

		@Override
		public int length() {
			return value.length();
		}

		@Override
		public CharSequence subSequence(final int start, final int end) {
			return new TimedValue(value.substring(start, end), deadline, name);
		}

		@Override
		public String toString() {
			return value;
		}
	}

	/**
	 * @param type  the parameter type
	 * @param entry an enum entry
	 * @param value a value already valid for the type
	 * @return whether they are the same value: numbers by value ({@code 01} is {@code 1}), booleans ignoring case
	 */
	private static boolean sameValue(final String type, final String entry, final String value) {
		return switch (type) {
			case "integer", "number" -> new BigDecimal(entry).compareTo(new BigDecimal(value)) == 0;
			case "boolean" -> entry.equalsIgnoreCase(value);
			default -> entry.equals(value);
		};
	}
}
