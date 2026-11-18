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
 * Deserializes the cells of one hard-coded row, <code>tableJoin.defaultRightLine</code>: a YAML list of one cell at
 * least, each a scalar other than null. A scalar is rejected: a row is never written as a string.
 */
public class CellsDeserializer extends JsonDeserializer<List<String>> {

	@Override
	public List<String> deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
		if (!parser.isExpectedStartArrayToken()) {
			return context.reportInputMismatch(
				List.class,
				"Expected a list of cells, such as [ \"a\", \"\", \"c\" ], got %s",
				parser.currentToken()
			);
		}
		final List<String> cells = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			// A null item (~) is rejected: a cell is a text, possibly empty
			if (!parser.currentToken().isScalarValue() || parser.hasToken(JsonToken.VALUE_NULL)) {
				return context.reportInputMismatch(List.class, "Expected a scalar as a cell, got %s", parser.currentToken());
			}
			cells.add(parser.getText());
		}
		if (cells.isEmpty()) {
			// An empty list is no row at all: the default right line of a join needs at least one cell
			return context.reportInputMismatch(List.class, "Expected a list of cells, got an empty list");
		}
		return cells;
	}
}
