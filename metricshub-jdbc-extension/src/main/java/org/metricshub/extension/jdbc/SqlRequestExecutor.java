package org.metricshub.extension.jdbc;

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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.SQLException;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.SqlResult;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;

/**
 * Provides functionality to execute SQL queries via JDBC.
 */
@Slf4j
public class SqlRequestExecutor {

	/**
	 * Execute an SQL query using the provided configuration and return the result.
	 *
	 * @param hostname         The hostname of the database server.
	 * @param jdbcConfig       JDBC configuration including URL, username, password, and timeout.
	 * @param sqlQuery         The SQL query to execute.
	 * @param showWarnings     Whether to show SQL warnings.
	 * @param telemetryManager The telemetry manager providing host properties.
	 * @return A {@link List} of {@link List} of {@link String}s representing the result table.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("JDBC SQL Query")
	public List<List<String>> executeSql(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("jdbc.config") @NonNull final JdbcConfiguration jdbcConfig,
		@SpanAttribute("sql.query") @NonNull final String sqlQuery,
		@SpanAttribute("sql.showWarnings") final boolean showWarnings,
		final TelemetryManager telemetryManager
	) throws ClientException {
		return executeSql(hostname, jdbcConfig, sqlQuery, showWarnings, telemetryManager, null);
	}

	/**
	 * Execute an SQL query using the provided configuration and a per-call driver selection.
	 *
	 * <p>When {@code driverSelection} is non-{@code null}, the call bypasses
	 * {@link java.sql.DriverManager} routing and uses the resolved {@link java.sql.Driver}
	 * instance directly, ensuring strict per-resource jar scoping.
	 *
	 * @param hostname         The hostname of the database server.
	 * @param jdbcConfig       JDBC configuration including URL, username, password, and timeout.
	 * @param sqlQuery         The SQL query to execute.
	 * @param showWarnings     Whether to show SQL warnings.
	 * @param telemetryManager The telemetry manager providing host properties.
	 * @param driverSelection  Pre-resolved selection identifying the driver class and the explicit
	 *                         JAR (when applicable). May be {@code null} for the legacy path.
	 * @return A {@link List} of {@link List} of {@link String}s representing the result table.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("JDBC SQL Query")
	public List<List<String>> executeSql(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("jdbc.config") @NonNull final JdbcConfiguration jdbcConfig,
		@SpanAttribute("sql.query") @NonNull final String sqlQuery,
		@SpanAttribute("sql.showWarnings") final boolean showWarnings,
		final TelemetryManager telemetryManager,
		final JdbcDriverSelection driverSelection
	) throws ClientException {
		try {
			final String url = String.valueOf(jdbcConfig.getUrl());

			// Log the details of the SQL request including the hostname
			log.trace(
				"""
				Hostname {} - Executing SQL query:
				- Username: {}
				- Query: {}
				- Timeout: {}
				""",
				hostname,
				jdbcConfig.getUsername(),
				sqlQuery,
				jdbcConfig.getTimeout()
			);

			// Execute the SQL query
			final SqlResult sqlResult = JdbcClient.execute(
				url,
				jdbcConfig.getUsername(),
				jdbcConfig.getPassword(),
				sqlQuery,
				showWarnings,
				jdbcConfig.getTimeout().intValue(),
				driverSelection
			);

			final List<List<String>> results = sqlResult.getResults();

			log.trace(
				"""
				Hostname {} - Executing SQL query:
				- Username: {}
				- Query: {}
				- Timeout: {}
				- Result: {}
				""",
				hostname,
				jdbcConfig.getUsername(),
				sqlQuery,
				jdbcConfig.getTimeout(),
				results
			);

			// Record the JDBC exchange if recording is enabled
			if (telemetryManager != null) {
				final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
				if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
					JdbcRecorder.getInstance(recordOutputDirectory).record(sqlQuery, results);
				}
			}

			return results;
		} catch (SQLException e) {
			log.debug("Hostname {} - SQL query failed. Stack trace:", hostname, e);
			throw new ClientException("SQL query failed on hostname " + hostname, e);
		}
	}
}
