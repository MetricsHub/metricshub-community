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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Holds file capture as a path-to-content map in {@link #logs}; {@link #getRawData()} exposes that for Awk. After a
 * compute updates {@link #setTable} or {@link #setRawData}, the superclass fields hold the working result (first
 * compute on file sources is expected to be Awk or similar).
 */
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LogTable extends SourceTable {

	/**
	 * Opening delimiter for one file segment in {@link #getRawData()}; includes the file path for Awk scripts.
	 */
	private static final String FILE_START_MARKER = "<<<FILE:path=\"%s\">>>";

	/**
	 * Closing delimiter for one file segment in {@link #getRawData()}, paired with {@link #FILE_START_MARKER}.
	 */
	private static final String FILE_END_MARKER = "<<<END_FILE>>>";

	/**
	 * Map containing structured data where the keys are the file names and the values are the lines that were read.
	 */
	@Default
	private Map<String, String> logs = new HashMap<>();

	public Map<String, String> getLogs() {
		return logs;
	}

	public void setLogs(final Map<String, String> logs) {
		this.logs = logs != null ? new HashMap<>(logs) : new HashMap<>();
	}

	@Override
	public String getRawData() {
		if (logs == null || logs.isEmpty()) {
			return super.getRawData();
		}

		final StringBuilder rawData = new StringBuilder();

		logs.forEach((path, content) -> {
			rawData.append(FILE_START_MARKER.formatted(path));
			rawData.append("\n");
			// To avoid having a new line when there is no content.
			rawData.append(!content.isEmpty() ? content + "\n" : content);
			rawData.append(FILE_END_MARKER);
			rawData.append("\n\n");
		});

		return rawData.toString();
	}

	@Override
	public boolean isEmpty() {
		return (logs == null || logs.isEmpty()) && super.isEmpty();
	}

	@Override
	public void setTable(final List<List<String>> table) {
		super.setTable(table);
		// Once a compute (e.g. Awk) writes the superclass table, the active result is tabular. If we kept the
		// captured file map, getRawData() would still synthesize from logs and would ignore the new rawData/table
		// written by computes. Clearing logs makes super the single source of truth after this point.
		logs = new HashMap<>();
	}

	@Override
	public void setRawData(final String rawData) {
		super.setRawData(rawData);
		// Once a compute (e.g. Awk) writes the superclass table, the active result is tabular. If we kept the
		// captured file map, getRawData() would still synthesize from logs and would ignore the new rawData/table
		// written by computes. Clearing logs makes super the single source of truth after this point.
		logs = new HashMap<>();
	}
}
