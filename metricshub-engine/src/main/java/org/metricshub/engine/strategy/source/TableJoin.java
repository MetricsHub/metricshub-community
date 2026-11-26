package org.metricshub.engine.strategy.source;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.monitor.task.source.JoinType;

/**
 * Joins two tables on a key column of each, as a <code>tableJoin</code> source does.
 * <ul>
 * <li>A row whose key cell is missing or empty is dropped, on both sides.</li>
 * <li>Keys are compared case-insensitively; WBEM keys are object paths whose key properties may come in any order.</li>
 * <li>When a key appears in several right rows, a left row matches the first of them only: one row per left row.</li>
 * <li>A row of the result is the cells of the left row followed by the cells of the right row. The right side of an
 * unmatched left row is the default right line, or the given number of empty cells, or as many empty cells as the
 * widest right row.</li>
 * </ul>
 */
public final class TableJoin {

	/**
	 * The comma that ends a key property of a WBEM object path: the one after the closing double quote of its value.
	 */
	private static final Pattern WBEM_PROPERTY_SEPARATOR = Pattern.compile("(?<=\"),");

	private TableJoin() {}

	/**
	 * Join two tables.
	 *
	 * @param leftTable        The rows of the left table, each a list of cells
	 * @param rightTable       The rows of the right table
	 * @param leftKeyColumn    The column of the key in the left table, starting at 1
	 * @param rightKeyColumn   The column of the key in the right table, starting at 1
	 * @param joinType         The rows to keep
	 * @param rightColumns    The number of empty cells on the right side of an unmatched left row; null for the
	 *                         default right line or the width of the widest right row
	 * @param defaultRightLine The cells of the right side of an unmatched left row; null for empty cells
	 * @param wbemKeyType      Whether the keys are WBEM object paths
	 * @return the new rows of the joined table
	 */
	public static List<List<String>> join(
		final List<List<String>> leftTable,
		final List<List<String>> rightTable,
		final int leftKeyColumn,
		final int rightKeyColumn,
		final JoinType joinType,
		final Integer rightColumns,
		final List<String> defaultRightLine,
		final boolean wbemKeyType
	) {
		final Map<String, List<String>> rightLookup = lookup(
			keyedRows(rightTable, rightKeyColumn),
			rightKeyColumn,
			wbemKeyType
		);
		final List<String> missingRight = defaultRightLine != null
			? defaultRightLine
			: emptyCells(rightColumns, rightTable);
		final List<List<String>> result = new ArrayList<>();
		for (final List<String> leftRow : keyedRows(leftTable, leftKeyColumn)) {
			final List<String> rightRow = rightLookup.get(key(leftRow, leftKeyColumn, wbemKeyType));
			if (rightRow != null) {
				result.add(concat(leftRow, rightRow));
			} else if (joinType == JoinType.LEFT) {
				result.add(concat(leftRow, missingRight));
			}
		}
		return result;
	}

	/**
	 * The rows of a table that have a key: the key cell exists and is not empty.
	 *
	 * @param table     The rows, possibly null
	 * @param keyColumn The column of the key, starting at 1
	 * @return the rows that have a key, in the order of the table
	 */
	private static List<List<String>> keyedRows(final List<List<String>> table, final int keyColumn) {
		if (table == null) {
			return Collections.emptyList();
		}
		return table.stream().filter(row -> keyColumn <= row.size() && !row.get(keyColumn - 1).isEmpty()).toList();
	}

	/**
	 * The first row of each key.
	 *
	 * @param rows        The rows that have a key
	 * @param keyColumn   The column of the key, starting at 1
	 * @param wbemKeyType Whether the keys are WBEM object paths
	 * @return the rows by key
	 */
	private static Map<String, List<String>> lookup(
		final List<List<String>> rows,
		final int keyColumn,
		final boolean wbemKeyType
	) {
		final Map<String, List<String>> lookup = new HashMap<>();
		rows.forEach(row -> lookup.putIfAbsent(key(row, keyColumn, wbemKeyType), row));
		return lookup;
	}

	/**
	 * The key of a row, as compared with the keys of the other table.
	 *
	 * @param row         A row that has a key
	 * @param keyColumn   The column of the key, starting at 1
	 * @param wbemKeyType Whether the key is a WBEM object path
	 * @return the key, in lower case, with its WBEM key properties sorted
	 */
	private static String key(final List<String> row, final int keyColumn, final boolean wbemKeyType) {
		final String key = row.get(keyColumn - 1).toLowerCase(Locale.ROOT);
		return wbemKeyType ? normalizeWbemPath(key) : key;
	}

	/**
	 * Sort the key properties of a WBEM object path, <code>class.key1="value1",key2="value2"</code>, so that two paths
	 * that list the same properties in a different order give the same key. A value is not parsed: a comma after a
	 * double quote ends a property.
	 *
	 * @param path The WBEM object path
	 * @return the class and the sorted properties, or the path itself when it has no class
	 */
	private static String normalizeWbemPath(final String path) {
		final int dot = path.indexOf('.');
		if (dot < 1) {
			return path;
		}
		final String[] properties = WBEM_PROPERTY_SEPARATOR.split(path.substring(dot + 1));
		Arrays.sort(properties);
		return path.substring(0, dot + 1) + String.join(".", properties);
	}

	/**
	 * The empty cells of the missing side of an unmatched row.
	 *
	 * @param columns The number of empty cells, null for the width of the widest row of the table
	 * @param table   The rows of the table of the missing side, possibly null
	 * @return the empty cells, none when no number is given and the table has no row
	 */
	private static List<String> emptyCells(final Integer columns, final List<List<String>> table) {
		if (columns != null) {
			return Collections.nCopies(columns, "");
		}
		return Collections.nCopies(table == null ? 0 : table.stream().mapToInt(List::size).max().orElse(0), "");
	}

	/**
	 * A new row: the cells of the left side followed by the cells of the right side.
	 *
	 * @param left  The cells of the left side
	 * @param right The cells of the right side
	 * @return the new row, which computes may change
	 */
	private static List<String> concat(final List<String> left, final List<String> right) {
		final List<String> row = new ArrayList<>(left.size() + right.size());
		row.addAll(left);
		row.addAll(right);
		return row;
	}
}
