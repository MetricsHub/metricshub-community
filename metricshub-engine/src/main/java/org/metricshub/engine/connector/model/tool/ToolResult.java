package org.metricshub.engine.connector.model.tool;

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

import java.io.Serializable;
import java.util.List;

/**
 * What a connector tool returns: the table of one of its sources, with optional column names so that the rows read
 * as records rather than positional lists.
 *
 * @param source  the reference of the source whose table is the result, e.g. {@code ${source::processes}}
 * @param columns the names of the table columns, in order, empty when the rows are returned as positional lists
 */
public record ToolResult(String source, List<String> columns) implements Serializable {
	/**
	 * Freezes the columns, {@code null} meaning none.
	 *
	 * @param source  the reference of the source whose table is the result
	 * @param columns the names of the table columns, {@code null} for none
	 */
	public ToolResult {
		columns = columns == null ? List.of() : List.copyOf(columns);
	}
}
