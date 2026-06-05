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
import org.metricshub.engine.connector.model.identity.DriverInfo;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.SqlResult;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistryHolder;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;

/**
 * This class provides a tool for executing SQL queries.
 */
public class SqlTool {

	private static final int DEFAULT_SQL_TIMEOUT = 120;

	/**
	 * Executes an SQL query using the default timeout.
	 *
	 * <p>The JDBC driver is inferred from {@code url} by walking every registered
	 * {@code IJdbcDriverProvider} and asking each loadable
	 * driver whether it accepts the URL (see
	 * {@link JdbcDriverRegistryHolder#findSelectionForUrl(String)}). When the URL prefix matches
	 * no resolvable driver, an {@link SQLException} is raised; templates that need an explicit
	 * driver should call {@link #query(String, String, String, String, int, String, String)}.
	 *
	 * @param query    the SQL query to execute (read-only)
	 * @param url      the JDBC connection URL
	 * @param username the username used for the database connection
	 * @param password the password used for the database connection
	 * @return a list of rows.
	 * @throws SQLException when the URL cannot be resolved to a known driver or query execution fails.
	 */
	public List<List<String>> query(final String query, final String url, final String username, final String password)
		throws SQLException {
		return query(query, url, username, password, DEFAULT_SQL_TIMEOUT);
	}

	/**
	 * Executes a read-only SQL query with an explicit timeout.
	 *
	 * <p>The JDBC driver is inferred from {@code url} by walking every registered
	 * {@code IJdbcDriverProvider} and asking each loadable
	 * driver whether it accepts the URL (see
	 * {@link JdbcDriverRegistryHolder#findSelectionForUrl(String)}). When the URL prefix matches
	 * no resolvable driver, an {@link SQLException} is raised; templates that need an explicit
	 * driver should call {@link #query(String, String, String, String, int, String, String)}.
	 *
	 * @param query    SQL query to execute
	 * @param url      JDBC connection URL
	 * @param username JDBC username
	 * @param password JDBC password
	 * @param timeout  timeout in seconds
	 * @return rows as list-of-lists.
	 * @throws SQLException when the URL cannot be resolved to a known driver or query execution fails.
	 */
	public List<List<String>> query(
		final String query,
		final String url,
		final String username,
		final String password,
		final int timeout
	) throws SQLException {
		final JdbcDriverSelection selection = JdbcDriverRegistryHolder.findSelectionForUrl(url);
		if (selection == null) {
			throw new SQLException(
				"No JDBC driver registered accepts URL " +
					url +
					"; declare an explicit driver via query(query, url, user, password, timeout, className, jarPath) " +
					"or install the driver JAR in the operator-default drivers directory."
			);
		}
		// Show SQL warning = false
		final SqlResult res = JdbcClient.execute(url, username, password.toCharArray(), query, false, timeout, selection);
		return res.getResults();
	}

	/**
	 * Executes a read-only SQL query against {@code url} using the JDBC driver class declared by
	 * the caller.
	 *
	 * <p>This overload is intended for programmable templates that target databases whose driver
	 * is not registered through any
	 * {@code IJdbcDriverProvider} (or whose URL is ambiguous).
	 * The driver class is loaded from the operator-default drivers directory unless an explicit
	 * {@code jarPath} is supplied.
	 *
	 * <p>The {@code jarPath} value is parsed by
	 * {@link JdbcDriverRegistryHolder#resolveSelection(DriverInfo)}, so it accepts the same
	 * placeholders ({@code $APP_DIR}, {@code $USER_HOME}, ...) and rejects {@code ..} traversal.
	 * A malformed {@code jarPath} is logged at WARN and ignored, but {@code className} is always
	 * preserved so resolution still goes through the registered driver descriptor / parent
	 * classloader.
	 *
	 * @param query     SQL query to execute.
	 * @param url       JDBC connection URL.
	 * @param username  JDBC username.
	 * @param password  JDBC password.
	 * @param timeout   timeout in seconds.
	 * @param className fully-qualified {@link java.sql.Driver} implementation class; required.
	 * @param jarPath   optional path expression pointing at a driver JAR; may be {@code null} or
	 *                  blank to fall back to the registered descriptor / scanned drivers
	 *                  directory.
	 * @return rows as list-of-lists.
	 * @throws SQLException when {@code className} is blank or query execution fails.
	 */
	public List<List<String>> query(
		final String query,
		final String url,
		final String username,
		final String password,
		final int timeout,
		final String className,
		final String jarPath
	) throws SQLException {
		if (className == null || className.isBlank()) {
			throw new SQLException("className is required for SqlTool.query(..., className, jarPath)");
		}
		final String normalizedJarPath = (jarPath == null || jarPath.isBlank()) ? null : jarPath.trim();
		final DriverInfo driverInfo = DriverInfo.builder().className(className.trim()).jarPath(normalizedJarPath).build();
		final JdbcDriverSelection selection = JdbcDriverRegistryHolder.resolveSelection(driverInfo);
		if (selection == null) {
			// Defensive: resolveSelection only returns null when className is blank, which we
			// already rejected above. Surface a clear error rather than NPE.
			throw new SQLException("Failed to resolve JDBC driver selection for className=" + className);
		}
		final SqlResult res = JdbcClient.execute(url, username, password.toCharArray(), query, false, timeout, selection);
		return res.getResults();
	}
}
