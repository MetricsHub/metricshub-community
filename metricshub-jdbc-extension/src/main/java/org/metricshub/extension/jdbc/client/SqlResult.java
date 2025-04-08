package org.metricshub.extension.jdbc.client;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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
import java.util.List;

/**
 * This class represents the result of a SQL query,
 * including data and any associated warnings.
 */
public class SqlResult {

	private List<List<String>> results;
	private StringBuilder warnings;

	/**
	 * Constructs an empty {@code SqlResult}.
	 */
	public SqlResult() {
		this.results = new ArrayList<>();
		this.warnings = new StringBuilder();
	}

	/**
	 * Returns the results.
	 * @return the list of results.
	 */
	public List<List<String>> getResults() {
		return results;
	}

	/**
	 * Returns the warnings.
	 * @return the warnings as a StringBuilder.
	 */
	public StringBuilder getWarnings() {
		return warnings;
	}

	/**
	 * Appends a warning message to the warnings with a "Warning: " prefix.
	 *
	 * @param message the warning message to append.
	 */
	public void appendWarnings(final String message) {
		if (message != null && !message.isEmpty()) {
			warnings.append("Warning: ").append(message).append("\n");
		}
	}

	/**
	 * Checks if there are any warnings.
	 *
	 * @return true if there are warnings, false otherwise.
	 */
	public boolean hasWarnings() {
		return warnings.length() > 0;
	}

	/**
	 * Adds a row of data to the results.
	 *
	 * @param row the list of data to add.
	 */
	public void addRow(final List<String> row) {
		results.add(row);
	}
}
