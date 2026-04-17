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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing SQL criteria to evaluate SQL queries.
 * and generate criterion test results accordingly.
 */
@Slf4j
public class SqlCriterionProcessor {

	private static final String SQL_TEST_SUCCESS = "Hostname %s - SQL test succeeded. Returned result: %s.";

	private SqlRequestExecutor sqlRequestExecutor;
	private boolean logMode;
	private Function<TelemetryManager, JdbcConfiguration> jdbcConfigurationProvider;

	private static final Function<TelemetryManager, JdbcConfiguration> DEFAULT_JDBC_CONFIGURATION_PROVIDER =
		telemetryManager ->
			(JdbcConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(JdbcConfiguration.class);

	/**
	 * Creates a new {@link SqlCriterionProcessor} with the given executor and log mode,
	 * using the default JDBC configuration provider.
	 *
	 * @param sqlRequestExecutor The executor to perform SQL requests.
	 * @param logMode            Whether to enable logging mode.
	 */
	public SqlCriterionProcessor(final SqlRequestExecutor sqlRequestExecutor, final boolean logMode) {
		this(sqlRequestExecutor, logMode, DEFAULT_JDBC_CONFIGURATION_PROVIDER);
	}

	/**
	 * Creates a new {@link SqlCriterionProcessor} with the given executor, log mode,
	 * and a custom JDBC configuration provider.
	 *
	 * @param sqlRequestExecutor        The executor to perform SQL requests.
	 * @param logMode                   Whether to enable logging mode.
	 * @param jdbcConfigurationProvider A function that retrieves the {@link JdbcConfiguration}
	 *                                  from the given {@link TelemetryManager}.
	 */
	public SqlCriterionProcessor(
		final SqlRequestExecutor sqlRequestExecutor,
		final boolean logMode,
		final Function<TelemetryManager, JdbcConfiguration> jdbcConfigurationProvider
	) {
		this.sqlRequestExecutor = sqlRequestExecutor;
		this.logMode = logMode;
		this.jdbcConfigurationProvider = jdbcConfigurationProvider;
	}

	/**
	 * Processes a SQL criterion by executing an SQL query.
	 *
	 * @param sqlCriterion     The criterion including the SQL query.
	 * @param telemetryManager The telemetry manager providing access to host configuration
	 * @return {@link CriterionTestResult} instance.
	 */
	public CriterionTestResult process(final SqlCriterion sqlCriterion, final TelemetryManager telemetryManager) {
		if (sqlCriterion == null) {
			return CriterionTestResult.error(sqlCriterion, "Malformed criterion. Cannot perform detection.");
		}

		final JdbcConfiguration jdbcConfiguration = jdbcConfigurationProvider.apply(telemetryManager);

		if (jdbcConfiguration == null) {
			return CriterionTestResult.error(sqlCriterion, "The SQL database credentials are not configured for this host.");
		}

		final String hostname = jdbcConfiguration.getHostname();

		final JdbcConfiguration cfg = (JdbcConfiguration) jdbcConfiguration.copy();

		if ((cfg.getDatabase() == null) && sqlCriterion.getDatabase() != null) {
			cfg.setDatabase(sqlCriterion.getDatabase());
			cfg.setUrl(cfg.generateUrl());
		}

		final List<List<String>> queryResult;
		try {
			queryResult = sqlRequestExecutor.executeSql(hostname, cfg, sqlCriterion.getQuery(), false, telemetryManager);
		} catch (Exception e) {
			if (logMode) {
				log.error("Hostname {} - Error executing SQL criterion: {}", hostname, e.getMessage());
				log.debug("Hostname {} - An exception occurred while executing SQL criterion.", hostname, e);
			}
			return CriterionTestResult.error(sqlCriterion, e.getMessage());
		}

		// Serialize the result as a CSV
		final String result = SourceTable.tableToCsv(queryResult, TABLE_SEP, true);

		return checkSqlResult(hostname, result, sqlCriterion.getExpectedResult());
	}

	/**
	 * Checks the result of an SQL test against the expected result.
	 *
	 * @param hostname       The hostname against which the SQL test has been carried out.
	 * @param result         The actual result of the SQL test.
	 * @param expectedResult The expected result of the SQL test.
	 * @return A {@link CriterionTestResult} summarizing the outcome of the SQL test.
	 */
	private CriterionTestResult checkSqlResult(final String hostname, final String result, final String expectedResult) {
		String message;
		boolean success = false;

		if (expectedResult == null) {
			if (result == null || result.isEmpty()) {
				message = String.format("Hostname %s - SQL test failed - The SQL test did not return any result.", hostname);
			} else {
				message = String.format(SQL_TEST_SUCCESS, hostname, result);
				success = true;
			}
		} else {
			// We convert the PSL regex from the expected result into a Java regex to be able to compile and test it
			final Pattern pattern = Pattern.compile(PslUtils.psl2JavaRegex(expectedResult), Pattern.CASE_INSENSITIVE);
			if (result != null && pattern.matcher(result).find()) {
				message = String.format(SQL_TEST_SUCCESS, hostname, result);
				success = true;
			} else {
				message =
					String.format(
						"Hostname %s - SQL test failed - The result (%s) returned by the SQL test did not match the expected result (%s).",
						hostname,
						result,
						expectedResult
					);
				message += String.format("Expected value: %s - returned value %s.", expectedResult, result);
			}
		}

		if (logMode) {
			log.debug(message);
		}

		return CriterionTestResult.builder().result(result).message(message).success(success).build();
	}
}
