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
 * Deserializes <code>static.value</code>: a scalar, returned as a {@link String} holding its text (legacy form), or
 * a YAML list, returned as the rows of the table, a {@link List} of {@link List} of {@link String}. The list holds
 * either cells, which are the single row (<code>[ a, b ]</code>), or rows, each a list of cells
 * (<code>[ [ a, 1 ], [ b, 2 ] ]</code>), never both. An empty list is an empty table. A row needs one cell at
 * least; a cell is a scalar other than null, and may hold a line break.
 */
public class StaticValueDeserializer extends JsonDeserializer<Object> {

	@Override
	public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
		if (!parser.isExpectedStartArrayToken()) {
			return parser.currentToken().isScalarValue()
				? parser.getText()
				: context.handleUnexpectedToken(Object.class, parser);
		}

		final List<List<String>> rows = new ArrayList<>();
		// The single row, when the list holds cells
		List<String> cells = null;
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			final boolean isRow = parser.isExpectedStartArrayToken();
			if (isRow ? cells != null : !rows.isEmpty()) {
				return context.reportInputMismatch(List.class, "A list holds either cells or rows, not both");
			}
			if (isRow) {
				final List<String> row = new ArrayList<>();
				while (parser.nextToken() != JsonToken.END_ARRAY) {
					row.add(readCell(parser, context));
				}
				if (row.isEmpty()) {
					return context.reportInputMismatch(List.class, "A row needs at least one cell");
				}
				rows.add(row);
			} else {
				if (cells == null) {
					cells = new ArrayList<>();
				}
				cells.add(readCell(parser, context));
			}
		}
		if (cells != null) {
			rows.add(cells);
		}
		return rows;
	}

	/**
	 * Reads a cell, the parser being on its token.
	 *
	 * @param parser  The parser
	 * @param context The deserialization context
	 * @return the text of the cell
	 * @throws IOException when the token is no scalar, or is null
	 */
	private static String readCell(final JsonParser parser, final DeserializationContext context) throws IOException {
		// A null cell (~) is rejected: the string form can never produce one
		if (!parser.currentToken().isScalarValue() || parser.hasToken(JsonToken.VALUE_NULL)) {
			return context.reportInputMismatch(String.class, "A cell must be a scalar other than null");
		}
		return parser.getText();
	}
}
