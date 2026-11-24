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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.JdbcClient.RequestNotSentException;
import org.metricshub.extension.jdbc.client.SqlResult;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;

/**
 * Provides functionality to execute SQL queries via JDBC.
 */
@Slf4j
public class SqlRequestExecutor {

	/**
	 * The {@code operation_type} of the JDBC requests in {@code metricshub.host.requests}.
	 */
	public static final String OPERATION_TYPE = JdbcExtension.IDENTIFIER;

	/**
	 * The SQLState of PostgreSQL {@code query_canceled}, how pgjdbc reports the expiry of a query timeout.
	 */
	static final String QUERY_CANCELED_SQL_STATE = "57014";

	/**
	 * Maximum number of causes visited, a guard against cause chains that loop.
	 */
	private static final int MAX_CAUSE_DEPTH = 32;

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
			log.trace("""
				Hostname {} - Executing SQL query:
				- Username: {}
				- Query: {}
				- Timeout: {}
				""", hostname, jdbcConfig.getUsername(), sqlQuery, jdbcConfig.getTimeout());

			// Execute the SQL query: one attempt (connect, execute, fetch, close) for the monitored resource, if any
			final RequestStats requestStats = telemetryManager == null ? null : telemetryManager.getRequestStats();
			final SqlResult sqlResult;
			try {
				sqlResult = JdbcClient.execute(
					url,
					jdbcConfig.getUsername(),
					jdbcConfig.getPassword(),
					sqlQuery,
					showWarnings,
					jdbcConfig.getTimeout().intValue(),
					driverSelection
				);
			} catch (SQLException e) {
				recordFailure(requestStats, e);
				throw e;
			}
			recordOutcome(requestStats, RequestOutcome.COMPLETED);

			final List<List<String>> results = sqlResult.getResults();

			log.trace("""
				Hostname {} - Executing SQL query:
				- Username: {}
				- Query: {}
				- Timeout: {}
				- Result: {}
				""", hostname, jdbcConfig.getUsername(), sqlQuery, jdbcConfig.getTimeout(), results);

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

	/**
	 * Records the outcome of a request attempt, if there is a monitored resource.
	 *
	 * @param requestStats The request counters of the monitored resource, {@code null} when there is none.
	 * @param outcome      The outcome of the attempt.
	 */
	static void recordOutcome(final RequestStats requestStats, final RequestOutcome outcome) {
		if (requestStats != null) {
			requestStats.record(OPERATION_TYPE, outcome);
		}
	}

	/**
	 * Records the outcome of a request attempt that threw, unless nothing was sent (see
	 * {@link RequestNotSentException}).
	 *
	 * @param requestStats The request counters of the monitored resource, {@code null} when there is none.
	 * @param exception    The exception thrown by the attempt.
	 */
	static void recordFailure(final RequestStats requestStats, final SQLException exception) {
		if (!hasCause(exception, RequestNotSentException.class)) {
			recordOutcome(requestStats, classify(exception));
		}
	}

	/**
	 * Classifies the exception of a request that was sent: {@link RequestOutcome#TIMEOUT} for a timeout or an
	 * interruption ({@link RequestOutcome#of(Throwable)}) and for SQLState {@value #QUERY_CANCELED_SQL_STATE}, which
	 * is how PostgreSQL reports the expiry of {@link java.sql.Statement#setQueryTimeout(int)} (a cancellation by an
	 * administrator looks the same, but MetricsHub never cancels a query itself), {@link RequestOutcome#FAILED}
	 * otherwise. Both the causes and the {@link SQLException#getNextException()} chain are searched.
	 *
	 * @param exception The exception thrown by the request.
	 * @return {@link RequestOutcome#TIMEOUT} or {@link RequestOutcome#FAILED}.
	 */
	static RequestOutcome classify(final SQLException exception) {
		// JDBC keeps the getNextException() chain apart from the causes, and a wrapper can hold either: walk both
		final Deque<Throwable> pending = new ArrayDeque<>(List.of(exception));
		for (int visited = 0; !pending.isEmpty() && visited < MAX_CAUSE_DEPTH; visited++) {
			final Throwable current = pending.pop();
			if (isTimeout(current)) {
				return RequestOutcome.TIMEOUT;
			}
			if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
				pending.push(sqlException.getNextException());
			}
			if (current.getCause() != null) {
				pending.push(current.getCause());
			}
		}
		return RequestOutcome.FAILED;
	}

	/**
	 * Whether the given exception itself reports a timeout.
	 *
	 * @param exception The exception.
	 * @return {@code true} for a timeout type or SQLState {@value #QUERY_CANCELED_SQL_STATE}.
	 */
	private static boolean isTimeout(final Throwable exception) {
		if (exception instanceof SQLException sqlException && QUERY_CANCELED_SQL_STATE.equals(sqlException.getSQLState())) {
			return true;
		}
		return RequestOutcome.of(exception) == RequestOutcome.TIMEOUT;
	}

	/**
	 * Whether the given exception or one of its causes is of the given type.
	 *
	 * @param exception The exception.
	 * @param type      The type to look for.
	 * @return {@code true} when found.
	 */
	private static boolean hasCause(final Throwable exception, final Class<? extends Throwable> type) {
		Throwable current = exception;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
