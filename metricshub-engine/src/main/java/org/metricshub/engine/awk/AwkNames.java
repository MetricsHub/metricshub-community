package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The rules of the names that connector values (constants, connector variables, text sources) take in AWK: in mapping
 * expressions, in awk sources and computes, and in the load-time checks.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AwkNames {

	/**
	 * The AWK keywords
	 */
	public static final Set<String> KEYWORDS = Set.of(
		"function",
		"func",
		"BEGIN",
		"END",
		"BEGINFILE",
		"ENDFILE",
		"in",
		"if",
		"else",
		"while",
		"for",
		"do",
		"return",
		"exit",
		"next",
		"nextfile",
		"continue",
		"delete",
		"break",
		"print",
		"printf",
		"getline"
	);

	/**
	 * The AWK built-in functions
	 */
	public static final Set<String> BUILTIN_FUNCTIONS = Set.of(
		"atan2",
		"close",
		"cos",
		"exp",
		"fflush",
		"gsub",
		"index",
		"int",
		"length",
		"log",
		"match",
		"rand",
		"sin",
		"split",
		"sprintf",
		"sqrt",
		"srand",
		"sub",
		"substr",
		"system",
		"tolower",
		"toupper"
	);

	/**
	 * The AWK special variables, gawk's included
	 */
	public static final Set<String> SPECIAL_VARIABLES = Set.of(
		"NR",
		"FNR",
		"NF",
		"FS",
		"RS",
		"OFS",
		"ORS",
		"RSTART",
		"RLENGTH",
		"FILENAME",
		"SUBSEP",
		"CONVFMT",
		"OFMT",
		"ENVIRON",
		"ARGC",
		"ARGV",
		"IGNORECASE",
		"ERRNO",
		"ARGIND",
		"RT",
		"PROCINFO",
		"SYMTAB",
		"FUNCTAB",
		"FPAT",
		"FIELDWIDTHS",
		"BINMODE",
		"LINT",
		"TEXTDOMAIN"
	);

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private static final Pattern REFERENCE = Pattern.compile("\\$\\{[A-Za-z]+::");

	private static final String SOURCE_PREFIX = "${source::";

	private static final String BEFORE_ALL = "beforeAll.";

	private static final String AFTER_ALL = "afterAll.";

	private static final String SOURCES = ".sources.";

	/**
	 * Whether the name is an AWK identifier: <code>[A-Za-z_][A-Za-z0-9_]*</code>.
	 *
	 * @param name The name
	 * @return whether it is an identifier
	 */
	public static boolean isIdentifier(final String name) {
		return name != null && IDENTIFIER.matcher(name).matches();
	}

	/**
	 * Whether the name is an AWK keyword, built-in function or special variable (case-sensitive).
	 *
	 * @param name The name
	 * @return whether AWK reserves it
	 */
	public static boolean isReserved(final String name) {
		return KEYWORDS.contains(name) || BUILTIN_FUNCTIONS.contains(name) || SPECIAL_VARIABLES.contains(name);
	}

	/**
	 * Whether the name can be an AWK variable: an identifier that is not reserved.
	 *
	 * @param name The name
	 * @return whether it can be a variable
	 */
	public static boolean isNameable(final String name) {
		return isIdentifier(name) && !isReserved(name);
	}

	/**
	 * Whether the value holds a reference: <code>${source::</code>, <code>${file::</code>,
	 * <code>${attribute::</code>... A shell <code>${HOME}</code> is not one.
	 *
	 * @param value The value
	 * @return whether it holds a reference
	 */
	public static boolean holdsReference(final String value) {
		return value != null && REFERENCE.matcher(value).find();
	}

	/**
	 * The name of a source in AWK: the local name of its key (<code>${source::monitors.m.job.sources.NAME}</code>,
	 * <code>${source::tools.t.sources.NAME}</code>, <code>${source::beforeAll.NAME}</code>,
	 * <code>${source::afterAll.NAME}</code>) when it can be a variable.
	 *
	 * @param sourceKey The key of the source
	 * @return the name, or <code>null</code>
	 */
	public static String textSourceName(final String sourceKey) {
		if (sourceKey == null || !sourceKey.startsWith(SOURCE_PREFIX) || !sourceKey.endsWith("}")) {
			return null;
		}
		final String path = sourceKey.substring(SOURCE_PREFIX.length(), sourceKey.length() - 1);
		final String name;
		if (path.startsWith(BEFORE_ALL)) {
			name = path.substring(BEFORE_ALL.length());
		} else if (path.startsWith(AFTER_ALL)) {
			name = path.substring(AFTER_ALL.length());
		} else {
			final int index = path.indexOf(SOURCES);
			if (index < 0) {
				return null;
			}
			name = path.substring(index + SOURCES.length());
		}
		return isNameable(name) ? name : null;
	}

	/**
	 * The value of a text source in AWK: the text without its final line feeds, as a <code>${source::}</code>
	 * interpolation gives it.
	 *
	 * @param text The text of the source, or <code>null</code>
	 * @return the value, empty for <code>null</code>
	 */
	public static String textValue(final String text) {
		return text == null ? "" : String.join(NEW_LINE, text.split(NEW_LINE));
	}

	/**
	 * The connector values by name: the nameable constants that hold no reference, the nameable connector variables
	 * and the text sources. On a shared name, a text source wins over a connector variable, which wins over a
	 * constant.
	 *
	 * @param constants      The constants of the connector, or <code>null</code>
	 * @param variableValues The values of the connector variables, or <code>null</code>
	 * @param textSources    The text sources, by name, or <code>null</code>
	 * @return a new map of the values by name
	 */
	public static Map<String, String> values(
		final Map<String, String> constants,
		final Map<String, String> variableValues,
		final Map<String, String> textSources
	) {
		final Map<String, String> values = new HashMap<>();
		if (constants != null) {
			constants.forEach((name, value) -> {
				if (value != null && isNameable(name) && !holdsReference(value)) {
					values.put(name, value);
				}
			});
		}
		putNameable(values, variableValues);
		putNameable(values, textSources);
		return values;
	}

	private static void putNameable(final Map<String, String> values, final Map<String, String> source) {
		if (source != null) {
			source.forEach((name, value) -> {
				if (value != null && isNameable(name)) {
					values.put(name, value);
				}
			});
		}
	}
}
