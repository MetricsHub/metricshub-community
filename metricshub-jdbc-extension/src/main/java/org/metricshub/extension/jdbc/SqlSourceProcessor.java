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
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing SQL sources and returning the result as a {@link SourceTable}.
 */
@Slf4j
public class SqlSourceProcessor {

	private SqlRequestExecutor sqlRequestExecutor;
	private String connectorId;
	private Function<TelemetryManager, JdbcConfiguration> jdbcConfigurationProvider;

	private static final Function<TelemetryManager, JdbcConfiguration> DEFAULT_JDBC_CONFIGURATION_PROVIDER =
		telemetryManager ->
			(JdbcConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(JdbcConfiguration.class);

	/**
	 * Creates a new {@link SqlSourceProcessor} with the given executor and connector ID,
	 * using the default JDBC configuration provider.
	 *
	 * @param sqlRequestExecutor The executor to perform SQL requests.
	 * @param connectorId        The connector identifier.
	 */
	public SqlSourceProcessor(final SqlRequestExecutor sqlRequestExecutor, final String connectorId) {
		this(sqlRequestExecutor, connectorId, DEFAULT_JDBC_CONFIGURATION_PROVIDER);
	}

	/**
	 * Creates a new {@link SqlSourceProcessor} with the given executor, connector ID,
	 * and a custom JDBC configuration provider.
	 *
	 * @param sqlRequestExecutor        The executor to perform SQL requests.
	 * @param connectorId               The connector identifier.
	 * @param jdbcConfigurationProvider A function that retrieves the {@link JdbcConfiguration}
	 *                                  from the given {@link TelemetryManager}.
	 */
	public SqlSourceProcessor(
		final SqlRequestExecutor sqlRequestExecutor,
		final String connectorId,
		final Function<TelemetryManager, JdbcConfiguration> jdbcConfigurationProvider
	) {
		this.sqlRequestExecutor = sqlRequestExecutor;
		this.connectorId = connectorId;
		this.jdbcConfigurationProvider = jdbcConfigurationProvider;
	}

	/**
	 * Processes a SQL source by executing its associated SQL query using the configuration and hostname retrieved
	 * from a given {@link TelemetryManager}. Handles errors, logs warnings or errors, and returns an empty table
	 * in case of failure.
	 *
	 * @param sqlSource        The SQL source containing the query to be executed.
	 * @param telemetryManager The telemetry manager providing host configuration and credentials for the SQL query.
	 * @return A {@link SourceTable} containing the results of the executed query, or an empty table if an error occurs.
	 */
	public SourceTable process(final SqlSource sqlSource, final TelemetryManager telemetryManager) {
		final JdbcConfiguration jdbcConfiguration = jdbcConfigurationProvider.apply(telemetryManager);

		if (jdbcConfiguration == null) {
			log.debug(
				"Hostname {} - The SQL database credentials are not configured. " +
				"Returning an empty table for SQL source {}. ",
				telemetryManager.getHostname(),
				sqlSource.getKey()
			);

			return SourceTable.empty();
		}

		final String hostname = jdbcConfiguration.getHostname();

		final JdbcConfiguration cfg = (JdbcConfiguration) jdbcConfiguration.copy();

		if ((cfg.getDatabase() == null) && sqlSource.getDatabase() != null) {
			cfg.setDatabase(sqlSource.getDatabase());
			cfg.setUrl(cfg.generateUrl());
		}

		try {
			final List<List<String>> results = sqlRequestExecutor.executeSql(
				hostname,
				cfg,
				sqlSource.getQuery(),
				false,
				telemetryManager
			);

			return SourceTable.builder().table(results).rawData(SourceTable.tableToCsv(results, TABLE_SEP, true)).build();
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				sqlSource.getKey(),
				String.format("SQL Query: %s", sqlSource.getQuery()),
				hostname,
				e
			);
			return SourceTable.empty();
		}
	}
}
