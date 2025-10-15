package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
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

import java.sql.SQLException;
import java.util.List;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.SqlResult;

/**
 * This class provides a tool for executing SQL queries.
 */
public class SqlTool {

	private static final int DEFAULT_SQL_TIMEOUT = 120;

	/**
	 * Executes an SQL query using the default timeout.
	 *
	 * @param query    the SQL query to execute (read-only)
	 * @param url      the JDBC connection URL
	 * @param username the username used for the database connection
	 * @param password the password used for the database connection
	 * @return a list of rows.
	 * @throws RuntimeException if any SQL error occurs during query execution
	 */
	public List<List<String>> query(final String query, final String url, final String username, final char[] password) {
		return query(query, url, username, password, DEFAULT_SQL_TIMEOUT);
	}

	/**
	 * Executes a read-only SQL query with an explicit timeout.
	 *
	 * @param query    SQL query to execute
	 * @param url      JDBC connection URL
	 * @param username JDBC username
	 * @param password JDBC password
	 * @param timeout  timeout in seconds
	 * @return rows as list-of-lists.
	 * @throws RuntimeException
	 *
	 */
	public List<List<String>> query(
		final String query,
		final String url,
		final String username,
		final char[] password,
		final int timeout
	) {
		try {
			// Show SQL warning = false
			final SqlResult res = JdbcClient.execute(url, username, password, query, false, timeout);
			return res.getResults();
		} catch (SQLException e) {
			throw new RuntimeException("SQL query failed: " + e.getMessage(), e);
		}
	}
}
