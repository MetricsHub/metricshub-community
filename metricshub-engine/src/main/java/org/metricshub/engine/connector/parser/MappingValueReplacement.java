package org.metricshub.engine.connector.parser;

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

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.awk.MappingExpression;

/**
 * The first-character rule of mapping values for the load-time replacements (<code>${constant::}</code>,
 * <code>${var::}</code>, <code>${env::}</code>): a mapping value that starts with <code>=</code> is an expression
 * and is never replaced, and a mapping text that a replacement turns into <code>=...</code> stays a text, written
 * as the AWK string literal of its text.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MappingValueReplacement {

	/**
	 * The path of a mapping value, in a connector or in the <code>monitors</code> of <code>metricshub.yaml</code>.
	 */
	private static final Pattern MAPPING_VALUE_PATH = Pattern.compile(
		"^(?:.*\\.)?monitors\\..+?\\.(?:discovery|collect|simple)\\.mapping\\." +
			"(?:attributes|metrics|conditionalCollection|legacyTextParameters)\\..+$"
	);

	/**
	 * Whether the path of a {@link JsonNodeContextUpdater} is that of a mapping value.
	 *
	 * @param path The path of the value
	 * @return true for a value of the attributes, metrics, conditionalCollection or legacyTextParameters of a mapping
	 */
	static boolean isMappingValue(final String path) {
		return path != null && MAPPING_VALUE_PATH.matcher(path).matches();
	}

	/**
	 * Replaces the references of a mapping value: an expression is returned unchanged, a text that the replacement
	 * turns into <code>=...</code> is returned as <code>= "&lt;literal&gt;"</code>, so it stays a text.
	 *
	 * @param value       The mapping value
	 * @param replacement The replacement of the references
	 * @return The replaced value
	 */
	static String replace(final String value, final UnaryOperator<String> replacement) {
		if (MappingExpression.isExpression(value)) {
			return value;
		}
		final String replaced = replacement.apply(value);
		return MappingExpression.isExpression(replaced) ? "= " + awkStringLiteral(replaced) : replaced;
	}

	/**
	 * The AWK string literal of a text.
	 *
	 * @param text The text
	 * @return The text between double quotes, with its backslashes, double quotes, line feeds, carriage returns and
	 *         tabs escaped
	 */
	static String awkStringLiteral(final String text) {
		final StringBuilder literal = new StringBuilder(text.length() + 2).append('"');
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			switch (c) {
				case '\\' -> literal.append("\\\\");
				case '"' -> literal.append("\\\"");
				case '\n' -> literal.append("\\n");
				case '\r' -> literal.append("\\r");
				case '\t' -> literal.append("\\t");
				default -> literal.append(c);
			}
		}
		return literal.append('"').toString();
	}
}
