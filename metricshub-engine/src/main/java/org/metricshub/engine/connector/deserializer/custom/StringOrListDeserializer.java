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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes a property written either as a scalar, returned as a {@link String} holding its text, or as a
 * YAML list, returned as a {@link List} of strings: the string (legacy) and list forms of
 * <code>tableJoin.defaultRightLine</code> and <code>static.value</code>. A list item must be a scalar other than
 * null.
 */
public class StringOrListDeserializer extends JsonDeserializer<Object> {

	@Override
	public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
		if (parser.isExpectedStartArrayToken()) {
			final List<String> cells = new ArrayList<>();
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				// A null item (~) is rejected: the string form can never produce a null cell
				if (!parser.currentToken().isScalarValue() || parser.hasToken(JsonToken.VALUE_NULL)) {
					return context.handleUnexpectedToken(String.class, parser);
				}
				// A line break would split the single row into several records of the raw text
				if (parser.getText().indexOf('\n') >= 0 || parser.getText().indexOf('\r') >= 0) {
					return context.reportInputMismatch(String.class, "A cell of the list form cannot hold a line break");
				}
				cells.add(parser.getText());
			}
			if (cells.isEmpty()) {
				// An empty list is no row at all: a static source needs at least one cell
				return context.reportInputMismatch(List.class, "Expected at least one cell, got an empty list");
			}
			return cells;
		}
		if (parser.currentToken().isScalarValue()) {
			return parser.getText();
		}
		return context.handleUnexpectedToken(Object.class, parser);
	}
}
