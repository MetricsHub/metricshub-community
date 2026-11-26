package org.metricshub.extension.internaldb;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Internal DB Extension
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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ISourceComputationExtension;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * This class implements the {@link ISourceComputationExtension} contract, reports the supported features,
 * processes {@link InternalDbQuerySource}.
 */
@Slf4j
public class InternalDbExtension implements ISourceComputationExtension {

	@Override
	public SourceResult processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		final String hostname = telemetryManager.getHostname();

		if (source == null) {
			log.warn(
				"Hostname {} - Internal DB Query Source cannot be null, the SQL operation will return an empty result.",
				hostname
			);
			return SourceResult.empty();
		}

		if (!(source instanceof InternalDbQuerySource internalDbQuery)) {
			log.warn(
				"Hostname {} - Internal DB Query Source is invalid, the SQL operation will return an empty result.",
				hostname
			);
			return SourceResult.empty();
		}

		final List<SqlTable> sqlTables = internalDbQuery.getTables();
		if (sqlTables == null) {
			return SourceResult.failed("Malformed Internal DB Query Source: no tables.");
		}

		final String query = internalDbQuery.getQuery();
		if (query == null || query.isBlank()) {
			return SourceResult.failed("Malformed Internal DB Query Source: no query.");
		}

		final List<List<String>> executeSqlQuery;
		try {
			executeSqlQuery = new SqlClientExecutor(telemetryManager, connectorId).executeQuery(sqlTables, query);
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, internalDbQuery.getKey(), "Internal DB Query: " + query, hostname, e);
			return SourceResult.failed("Internal DB Query: " + query, e);
		}

		final SourceResult sourceResult = SourceResult.ofTable(executeSqlQuery);

		LoggingHelper.debug(
			() -> log.trace(
				"Executed Internal DB Query request:{}\n- Result:\n{}\n",
				query,
				TextTableHelper.generateTextTable(executeSqlQuery)
			)
		);

		return sourceResult;
	}

	@Override
	public boolean isValidSource(Source source) {
		return source instanceof InternalDbQuerySource;
	}
}
