package org.metricshub.engine.awk;

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

import io.jawk.jrt.InputSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Feeds a table to an AWK script as structured records: each row is one record whose cells are
 * <code>$1..$NF</code>. Nothing is serialized, no field separator is involved, so a <code>;</code> inside a cell
 * cannot move a column.
 * <p>
 * <code>$0</code> is not provided: Jawk synthesizes it from the fields (joined with <code>OFS</code>) only when the
 * script reads it. Use {@link #withRecordText(List, String)} to expose a given text as <code>$0</code> as well, for
 * instance to keep the legacy <code>;</code>-joined row of a mapping expression.
 */
public class TableInputSource implements InputSource {

	private final List<List<String>> rows;
	private final String separator;
	private int index = -1;
	private List<String> fields;
	private String recordText;

	/**
	 * A source over the rows of a table, <code>$0</code> left to Jawk.
	 *
	 * @param rows The rows, null meaning no record
	 */
	public TableInputSource(final List<List<String>> rows) {
		this(rows, null);
	}

	private TableInputSource(final List<List<String>> rows, final String separator) {
		this.rows = rows == null ? List.of() : rows;
		this.separator = separator;
	}

	/**
	 * A source over the rows of a table that also exposes each row joined with the separator as <code>$0</code>,
	 * while <code>$1..$NF</code> are the cells.
	 *
	 * @param rows      The rows, null meaning no record
	 * @param separator The separator joining the cells in <code>$0</code>
	 * @return the source
	 */
	public static TableInputSource withRecordText(final List<List<String>> rows, final String separator) {
		return new TableInputSource(rows, separator);
	}

	/**
	 * A source holding one record: the given row.
	 *
	 * @param row       The cells of the record
	 * @param separator The separator joining the cells in <code>$0</code>
	 * @return the source
	 */
	public static TableInputSource ofRow(final List<String> row, final String separator) {
		return new TableInputSource(List.of(row == null ? List.of() : row), separator);
	}

	@Override
	public boolean nextRecord() {
		final int next = index + 1;
		if (next >= rows.size()) {
			fields = null;
			recordText = null;
			return false;
		}
		index = next;
		final List<String> row = rows.get(index);
		final List<String> cells = new ArrayList<>(row == null ? 0 : row.size());
		if (row != null) {
			for (final String cell : row) {
				cells.add(cell == null ? "" : cell);
			}
		}
		fields = Collections.unmodifiableList(cells);
		recordText = separator == null ? null : String.join(separator, cells);
		return true;
	}

	@Override
	public String getRecordText() {
		return recordText;
	}

	@Override
	public List<String> getFields() {
		return fields;
	}

	@Override
	public boolean isFromFilenameList() {
		return false;
	}
}
