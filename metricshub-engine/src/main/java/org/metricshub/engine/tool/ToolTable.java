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

import java.util.List;
import org.metricshub.engine.common.helpers.StringHelper;

/**
 * What a tool returns when its result is a table.
 *
 * @param columns the column names, empty when the tool has none
 * @param rows    the rows
 */
public record ToolTable(List<String> columns, List<List<String>> rows) implements ToolResult {
	@Override
	public String type() {
		return "table";
	}

	/**
	 * @param query a SELECT query (SQL, WQL)
	 * @param rows  its result
	 * @return the table, named after the columns the query selects; no name for a {@code SELECT *}
	 */
	public static ToolTable ofQuery(final String query, final List<List<String>> rows) {
		final List<String> columns = List.of(StringHelper.extractColumns(query));
		return new ToolTable(columns.equals(List.of("*")) ? List.of() : columns, rows);
	}

	/**
	 * @return the table in GitHub-flavored Markdown, as wide as its widest row or as its columns: a column
	 *         without a name is headed {@code Column N}, a shorter row ends with empty cells, and there is no
	 *         alignment padding; {@code (empty)} for a table of no column and no cell
	 */
	public String toMarkdown() {
		final int width = Math.max(columns.size(), rows.stream().mapToInt(List::size).max().orElse(0));
		if (width == 0) {
			return "(empty)";
		}
		final StringBuilder markdown = new StringBuilder();
		for (int i = 0; i < width; i++) {
			markdown.append("| ").append(i < columns.size() ? cell(columns.get(i)) : "Column " + (i + 1)).append(' ');
		}
		markdown.append("|\n").append("| --- ".repeat(width)).append('|');
		for (final List<String> row : rows) {
			markdown.append('\n');
			for (int i = 0; i < width; i++) {
				markdown.append("| ").append(i < row.size() ? cell(row.get(i)) : "").append(' ');
			}
			markdown.append('|');
		}
		return markdown.toString();
	}

	/**
	 * @param value the value of a cell, {@code null} for none
	 * @return the value on one line of a Markdown table: a pipe is escaped, with the backslashes right before
	 *         it, which would otherwise escape the escape and leave the pipe to end the cell
	 */
	private static String cell(final String value) {
		return value == null
			? ""
			: value.replaceAll("(\\\\*)\\|", "$1$1\\\\|").replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
	}
}
