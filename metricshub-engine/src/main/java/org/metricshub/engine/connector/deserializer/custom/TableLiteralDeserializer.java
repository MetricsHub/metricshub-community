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
 * Deserializes a <code>tableJoin</code> input (<code>leftTable</code>, <code>rightTable</code>): a non-blank scalar,
 * returned as a {@link String} (a source reference, or a legacy table literal written as <code>;</code> CSV text), or
 * a table literal written as a YAML list of rows, each row a list of cells, returned as a {@link List} of
 * {@link List} of {@link String}: exactly these rows and cells.
 */
public class TableLiteralDeserializer extends JsonDeserializer<Object> {

	@Override
	public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
		if (parser.isExpectedStartArrayToken()) {
			return readRows(parser, context);
		}
		if (!parser.currentToken().isScalarValue()) {
			return context.handleUnexpectedToken(Object.class, parser);
		}
		final String value = parser.getText();
		if (value.isBlank()) {
			return context.reportInputMismatch(
				String.class,
				"Invalid blank value encountered for property '%s'.",
				parser.currentName()
			);
		}
		return value;
	}

	/**
	 * Reads a table literal, the parser being on the start of its list: at least one row, each row a list of at least
	 * one cell, each cell a scalar other than null.
	 *
	 * @param parser  The parser, on the start of the list
	 * @param context The deserialization context
	 * @return the rows
	 * @throws IOException when the list is no such table literal
	 */
	static List<List<String>> readRows(final JsonParser parser, final DeserializationContext context) throws IOException {
		final List<List<String>> rows = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			if (!parser.isExpectedStartArrayToken()) {
				return context.reportInputMismatch(
					List.class,
					"A table literal is a list of rows, each row a list of cells: got %s for a row",
					parser.currentToken()
				);
			}
			final List<String> row = new ArrayList<>();
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				// A null cell (~) is rejected: the string form can never produce one
				if (!parser.currentToken().isScalarValue() || parser.hasToken(JsonToken.VALUE_NULL)) {
					return context.reportInputMismatch(
						String.class,
						"A cell of a table literal must be a scalar other than null: got %s",
						parser.currentToken()
					);
				}
				// A line break would split the row once the table is read as text, as in the list form of static.value
				if (parser.getText().indexOf('\n') >= 0 || parser.getText().indexOf('\r') >= 0) {
					return context.reportInputMismatch(String.class, "A cell of a table literal cannot hold a line break");
				}
				row.add(parser.getText());
			}
			if (row.isEmpty()) {
				return context.reportInputMismatch(List.class, "A row of a table literal needs at least one cell");
			}
			rows.add(row);
		}
		if (rows.isEmpty()) {
			return context.reportInputMismatch(List.class, "A table literal needs at least one row");
		}
		return rows;
	}
}
