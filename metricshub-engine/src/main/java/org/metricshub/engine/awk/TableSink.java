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

import io.jawk.jrt.AwkSink;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Collects the output of an AWK script as a table: each <code>print a, b, c</code> appends the row
 * <code>[a, b, c]</code> without serializing anything, and a bare <code>print</code> appends the current record's
 * fields <code>[$1, ..., $NF]</code>, exactly as <code>print $1, ..., $NF</code> would (<code>print $0</code> still
 * gives one cell). <code>printf</code> writes text, so its output is split the way the script serialized it:
 * <code>ORS</code> ends a row and <code>OFS</code> separates its cells, exactly as the text would be read back. Text
 * written after the last <code>ORS</code> stays pending until the next <code>print</code> or <code>printf</code>
 * completes it; {@link #getRows()} turns whatever is still pending into the last row.
 * <p>
 * The values are rendered as <code>print</code> renders them: a string as is, an integral number without decimals,
 * any other number through <code>OFMT</code>.
 */
public class TableSink extends AwkSink {

	private final List<List<String>> rows = new ArrayList<>();

	/** Text written by <code>printf</code> since the last <code>ORS</code>. */
	private final StringBuilder pending = new StringBuilder();

	/** The <code>OFS</code> in force when the pending text began. */
	private String pendingOfs = " ";

	@Override
	public void print(final String ofs, final String ors, final String ofmt, final Object... values) {
		final List<String> row = new ArrayList<>(values == null ? 0 : values.length);
		if (values != null) {
			for (final Object value : values) {
				row.add(formatPrintArgument(value, ofmt));
			}
		}
		if (pending.length() == 0) {
			rows.add(row);
			return;
		}
		// The record started by a printf ends with this print: its text completes the last pending cell, the other
		// values stay cells of their own, whatever they contain
		final List<String> cells = split(pending.toString(), pendingOfs);
		pending.setLength(0);
		if (!row.isEmpty()) {
			final int last = cells.size() - 1;
			cells.set(last, cells.get(last) + row.get(0));
			cells.addAll(row.subList(1, row.size()));
		}
		rows.add(cells);
	}

	/**
	 * A bare <code>print</code>: the current record's fields <code>$1..$NF</code> are the printed values, so each one
	 * is a cell, rendered and completing a pending <code>printf</code> as {@link #print} does. A record without fields
	 * (<code>NF == 0</code>) prints <code>$0</code> as before: one empty cell, never a row without cells.
	 */
	@Override
	public void printRecord(
		final String ofs,
		final String ors,
		final String ofmt,
		final List<Object> fields,
		final Object record
	) {
		print(ofs, ors, ofmt, fields.isEmpty() ? new Object[] { record } : fields.toArray());
	}

	@Override
	public void printf(
		final String ofs,
		final String ors,
		final String ofmt,
		final String convfmt,
		final String format,
		final Object... values
	) {
		append(sprintf(convfmt, format, values), ofs, ors);
	}

	/**
	 * @return the rows collected so far, the pending text (if any) completing the last one
	 */
	public List<List<String>> getRows() {
		if (pending.length() > 0) {
			rows.add(split(pending.toString(), pendingOfs));
			pending.setLength(0);
		}
		return rows;
	}

	private void append(final String text, final String ofs, final String ors) {
		if (pending.length() == 0) {
			pendingOfs = ofs;
		}
		if (ors == null || ors.isEmpty()) {
			pending.append(text);
			return;
		}
		// The text already scanned holds no ORS: resume just before its end, where an ORS may straddle the two
		int from = Math.max(0, pending.length() - ors.length() + 1);
		pending.append(text);
		int start = 0;
		int end;
		while ((end = pending.indexOf(ors, from)) >= 0) {
			rows.add(split(pending.substring(start, end), pendingOfs));
			start = end + ors.length();
			from = start;
			// Whatever follows began in this append
			pendingOfs = ofs;
		}
		pending.delete(0, start);
	}

	private static List<String> split(final String record, final String ofs) {
		if (ofs == null || ofs.isEmpty()) {
			return new ArrayList<>(List.of(record));
		}
		return new ArrayList<>(Arrays.asList(record.split(Pattern.quote(ofs), -1)));
	}
}
