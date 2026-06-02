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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.metricshub.extension.jdbc.driver.DriverResolutionException;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistryHolder;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;
import org.metricshub.extension.jdbc.driver.LoadedDriver;

/**
 * This class provides functionality to execute SQL queries via JDBC and return
 * the results in a structured format.
 */
public class JdbcClient {

	private JdbcClient() {}

	static {
		// Set the default locale to US to ensure that the SQL query results are formatted correctly
		Locale.setDefault(Locale.of("en", "US"));
	}

	/**
	 * Executes a SQL query via JDBC and returns the results and any SQL warnings in
	 * a {@link SqlResult} object.
	 *
	 * @param url          The JDBC URL to connect to the database
	 * @param username     The username for the database connection
	 * @param password     The password as a char array for security
	 * @param sqlQuery     The SQL query to be executed
	 * @param showWarnings If true, SQL warnings are added to the {@link SqlResult}.
	 * @param timeout      The timeout in seconds for the query
	 * @return A {@link SqlResult} object with the query results and warnings.
	 * @throws SQLException If an error occurs with the database
	 */
	public static SqlResult execute(
		String url,
		String username,
		char[] password,
		String sqlQuery,
		boolean showWarnings,
		int timeout
	) throws SQLException {
		return execute(url, username, password, sqlQuery, showWarnings, timeout, null);
	}

	/**
	 * Executes a SQL query via JDBC using a pre-resolved {@link JdbcDriverSelection}.
	 *
	 * <p>When {@code selection} is non-{@code null}, this method bypasses
	 * {@link DriverManager} routing entirely and obtains a {@link Connection} directly from the
	 * resolved {@link Driver} instance, ensuring strict per-resource jar scoping (two resources
	 * referencing the same {@code driverClass} but different {@code driverPath} values will use
	 * their own dedicated driver instances).
	 *
	 * <p>When {@code selection} is {@code null}, the legacy {@link DriverManager}-based path is
	 * used (after pre-registering any matching shielded driver via
	 * {@link JdbcDriverRegistryHolder#ensureDriverForUrl(String)}).
	 *
	 * @param url          The JDBC URL to connect to the database.
	 * @param username     The username for the database connection.
	 * @param password     The password as a char array for security.
	 * @param sqlQuery     The SQL query to be executed.
	 * @param showWarnings If true, SQL warnings are added to the {@link SqlResult}.
	 * @param timeout      The timeout in seconds for the query.
	 * @param selection    Pre-resolved driver selection; may be {@code null}.
	 * @return A {@link SqlResult} object with the query results and warnings.
	 * @throws SQLException If an error occurs with the database.
	 */
	public static SqlResult execute(
		String url,
		String username,
		char[] password,
		String sqlQuery,
		boolean showWarnings,
		int timeout,
		JdbcDriverSelection selection
	) throws SQLException {
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("JDBC URL cannot be null or empty");
		}

		if (sqlQuery == null || sqlQuery.isEmpty()) {
			throw new IllegalArgumentException("SQL query cannot be null or empty");
		}

		try (
			Connection databaseConnection = openConnection(url, username, password, selection);
			Statement queryStatement = databaseConnection.createStatement()
		) {
			queryStatement.setQueryTimeout(timeout);
			boolean isResultSet = queryStatement.execute(sqlQuery);

			final SqlResult sqlResult = new SqlResult();

			// Process the (maybe many) result sets
			do {
				if (isResultSet) {
					processResultSet(queryStatement, sqlResult);
				} else {
					// If the result is an update count (e.g., after an UPDATE or DELETE query).
					if (queryStatement.getUpdateCount() == -1) {
						// Exit if there are no more results
						break;
					}
				}
				// Now, up to the next result set! (if any)
				isResultSet = queryStatement.getMoreResults();
			} while (isResultSet || queryStatement.getUpdateCount() != -1);

			// Now, read the PRINT statements!
			appendWarnings(showWarnings, queryStatement, sqlResult);

			return sqlResult;
		} catch (SQLException e) {
			throw new SQLException("Error executing query: " + e.getMessage(), e);
		}
	}

	/**
	 * Opens a {@link Connection} either via the resolved {@link Driver} (when {@code selection}
	 * is non-{@code null}) or via {@link DriverManager} (legacy path).
	 *
	 * @param url        The JDBC URL.
	 * @param username   The username; may be {@code null}.
	 * @param password   The password; may be {@code null}.
	 * @param selection  Pre-resolved driver selection; may be {@code null}.
	 * @return The opened {@link Connection}.
	 * @throws SQLException If the driver cannot be resolved or refuses the URL.
	 */
	private static Connection openConnection(
		final String url,
		final String username,
		final char[] password,
		final JdbcDriverSelection selection
	) throws SQLException {
		if (selection == null) {
			// Ensure the appropriate driver is registered with DriverManager before connecting.
			JdbcDriverRegistryHolder.ensureDriverForUrl(url);
			return username == null || password == null
				? DriverManager.getConnection(url)
				: DriverManager.getConnection(url, username, new String(password));
		}

		final LoadedDriver loaded;
		try {
			loaded = JdbcDriverRegistryHolder.get().resolve(selection.driverClass(), selection.explicitJarPath());
		} catch (DriverResolutionException e) {
			throw new SQLException("Failed to resolve JDBC driver " + selection.driverClass() + ": " + e.getMessage(), e);
		}

		final Properties props = new Properties();
		if (username != null) {
			props.setProperty("user", username);
		}
		if (password != null) {
			props.setProperty("password", new String(password));
		}

		final Connection connection = loaded.driver().connect(url, props);
		if (connection == null) {
			throw new SQLException("Driver " + selection.driverClass() + " did not accept JDBC URL " + url);
		}
		return connection;
	}

	/**
	 * Processes the SQL warnings and appends them to the {@link SqlResult}.
	 *
	 * @param showWarnings   If true, SQL warnings are added to the {@link SqlResult}.
	 * @param queryStatement The statement to process the warnings.
	 * @param sqlResult      The {@link SqlResult} to add the warnings to.
	 * @throws SQLException If an error occurs with the database.
	 */
	private static void appendWarnings(boolean showWarnings, Statement queryStatement, SqlResult sqlResult)
		throws SQLException {
		if (showWarnings) {
			SQLWarning warning = queryStatement.getWarnings();
			while (warning != null) {
				sqlResult.appendWarnings(warning.getMessage());
				warning = warning.getNextWarning();
			}
		}
	}

	/**
	 * Processes the result set and adds the results to the {@link SqlResult}.
	 *
	 * @param queryStatement The statement to process the result set.
	 * @param sqlResult      The {@link SqlResult} to add the results to.
	 * @throws SQLException If an error occurs with the database.
	 */
	private static void processResultSet(final Statement queryStatement, final SqlResult sqlResult) throws SQLException {
		try (ResultSet queryRecordSet = queryStatement.getResultSet()) {
			// Get the number of columns in the query output
			ResultSetMetaData metadata = queryRecordSet.getMetaData();
			int numberOfColumns = metadata.getColumnCount();

			// Build the result by parsing the rows
			while (queryRecordSet.next()) {
				final List<String> row = new ArrayList<>();
				for (int i = 1; i < numberOfColumns + 1; i++) {
					row.add(queryRecordSet.getString(i));
				}
				sqlResult.addRow(row);
			}
		}
	}
}
