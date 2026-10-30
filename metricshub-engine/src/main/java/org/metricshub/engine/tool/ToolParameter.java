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
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
	 * The characters that quote, expand, chain, redirect, glob or comment in a shell (POSIX, PowerShell, cmd) or
	 * a query: a pattern refusing all of them lets a value be inserted unescaped.
	 */
	private static final String METACHARACTERS = " \t\r\n'\"`$;|&<>(){}[]*?!#~%^\\";

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
			// Advertised as values of the type
			for (final String value : enumValues) {
				check("enum", value, type, null, null);
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
	 * @return whether a source may insert the value without escaping it: a number or a boolean, one of an enum
	 *         chosen by the connector, or a string whose pattern refuses every shell and query metacharacter
	 */
	@JsonIgnore
	public boolean isSafeUnescaped() {
		if (!STRING.equals(type) || enumValues != null) {
			return true;
		}
		if (pattern == null) {
			return false;
		}
		final Pattern compiled = Pattern.compile(pattern);
		return METACHARACTERS.chars()
			.mapToObj(c -> String.valueOf((char) c))
			.noneMatch(c -> compiled.matcher(c).matches() || compiled.matcher("a" + c + "a").matches());
	}

	/**
	 * Checks an argument against the type, the enum and the pattern of the parameter. Whatever the parameter, the
	 * value may not contain {@code %{} or {@code ${}: the macros (credentials, sources, files) are expanded after
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
		final boolean typed = switch (type) {
			case "integer" -> INTEGER.matcher(value).matches();
			case "number" -> NUMBER.matcher(value).matches();
			case "boolean" -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
			default -> true;
		};
		if (!typed) {
			throw new IllegalArgumentException("The argument %s must be of type %s".formatted(name, type));
		}
		if (enumValues != null && !enumValues.contains(value)) {
			throw new IllegalArgumentException("The argument %s must be one of %s".formatted(name, enumValues));
		}
		if (pattern != null && !Pattern.compile(pattern).matcher(value).matches()) {
			throw new IllegalArgumentException("The argument %s must match the pattern %s".formatted(name, pattern));
		}
	}
}
