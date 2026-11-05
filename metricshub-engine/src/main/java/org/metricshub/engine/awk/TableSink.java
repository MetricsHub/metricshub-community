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
import java.util.List;
import lombok.Getter;

/**
 * Collects the output of an AWK script as a table: each <code>print a, b, c</code> appends the row
 * <code>[a, b, c]</code>, each <code>printf</code> appends a row holding the formatted text as its single cell.
 * <code>OFS</code> and <code>ORS</code> play no part: the values are the cells, nothing is serialized.
 * <p>
 * The values are rendered as <code>print</code> renders them: a string as is, an integral number without decimals,
 * any other number through <code>OFMT</code>.
 */
public class TableSink extends AwkSink {

	@Getter
	private final List<List<String>> rows = new ArrayList<>();

	@Override
	public void print(final String ofs, final String ors, final String ofmt, final Object... values) {
		final List<String> row = new ArrayList<>(values == null ? 0 : values.length);
		if (values != null) {
			for (final Object value : values) {
				row.add(formatPrintArgument(value, ofmt));
			}
		}
		rows.add(row);
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
		final List<String> row = new ArrayList<>(1);
		row.add(sprintf(convfmt, format, values));
		rows.add(row);
	}
}
