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
 * Deserializes the members of a <code>tableUnion</code>. A list holds non-blank scalars, each returned as a
 * {@link String} (a source reference, or a legacy table literal written as <code>;</code> CSV text), and table
 * literals written as lists of rows (see {@link TableLiteralDeserializer}), each returned as a {@link List} of rows. A
 * scalar is the legacy comma-separated list of strings.
 */
public class TablesDeserializer extends JsonDeserializer<List<Object>> {

	private static final String BLANK_MEMBER = "The table referenced by 'TableUnionSource' cannot be empty.";

	@Override
	public List<Object> deserialize(final JsonParser parser, final DeserializationContext context) throws IOException {
		final List<Object> tables = new ArrayList<>();
		if (parser.isExpectedStartArrayToken()) {
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				if (parser.isExpectedStartArrayToken()) {
					tables.add(TableLiteralDeserializer.readRows(parser, context));
				} else if (parser.currentToken().isScalarValue() && !parser.hasToken(JsonToken.VALUE_NULL)) {
					tables.add(nonBlank(parser.getText(), context));
				} else {
					return context.reportInputMismatch(List.class, BLANK_MEMBER);
				}
			}
			return tables;
		}
		if (!parser.currentToken().isScalarValue()) {
			return context.reportInputMismatch(List.class, "Expected a list of tables, got %s", parser.currentToken());
		}
		for (final String table : parser.getText().split(",")) {
			tables.add(nonBlank(table.trim(), context));
		}
		return tables;
	}

	private static String nonBlank(final String table, final DeserializationContext context) throws IOException {
		if (table.isBlank()) {
			return context.reportInputMismatch(List.class, BLANK_MEMBER);
		}
		return table;
	}
}
