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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.metricshub.engine.connector.model.common.SqlColumn;
import org.metricshub.engine.connector.model.common.SqlTable;

/**
 * Custom deserializer for a List of {@link SqlTable} objects.
 */
public class SqlTableDeserializer extends JsonDeserializer<List<SqlTable>> {

	@Override
	public List<SqlTable> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return new ArrayList<>();
		}

		final JsonNode node = parser.getCodec().readTree(parser);

		final List<SqlTable> sqlTables = new ArrayList<>();

		// Iterate through the fields of the JSON object
		if (node != null) {
			node
				.elements()
				.forEachRemaining(tableNode -> {
					createSqlTable(tableNode, sqlTables);
				});
		}

		return sqlTables;
	}

	/**
	 * Parse a {@link JsonNode} and try to create a {@link SqlTable} object from it and add it to the sqlTables list.
	 * @param tableNode The JSON node to parse.
	 * @param sqlTables The list where to add the SQL tables.
	 */
	private void createSqlTable(final JsonNode tableNode, final List<SqlTable> sqlTables) {
		final SqlTable sqlTable = new SqlTable();
		sqlTable.setSource(tableNode.get("source").asText());
		sqlTable.setAlias(tableNode.get("alias").asText());

		final JsonNode columnsNode = tableNode.get("columns");
		final List<SqlColumn> columns = new ArrayList<>();
		columnsNode
			.elements()
			.forEachRemaining(columnNode -> {
				columns.add(SqlColumn.detect(columnNode));
			});
		sqlTable.setColumns(columns);

		sqlTables.add(sqlTable);
	}
}
