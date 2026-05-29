package org.metricshub.extension.emulation.jdbc;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2026 MetricsHub
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.AbstractEmulationExecutor;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.SqlRequestExecutor;

/**
 * SQL request executor that replays query results from recorded emulation files.
 */
@Slf4j
public class EmulationSqlRequestExecutor extends SqlRequestExecutor {

	record SqlContext(String sqlQuery) {}

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Constructs an EmulationSqlRequestExecutor with the given round-robin manager and image cache manager.
	 *
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public EmulationSqlRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

	@Override
	public List<List<String>> executeSql(
		final String hostname,
		final JdbcConfiguration jdbcConfig,
		final String sqlQuery,
		final boolean showWarnings,
		final TelemetryManager telemetryManager
	) throws ClientException {
		if (sqlQuery == null || telemetryManager == null) {
			return List.of();
		}

		final EmulationConfiguration emulationConfiguration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);

		final String emulationInputDirectory =
			emulationConfiguration != null && emulationConfiguration.getJdbc() != null
				? emulationConfiguration.getJdbc().getDirectory()
				: null;

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - JDBC emulation input directory is not configured.", hostname);
			return List.of();
		}

		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			JdbcEmulationImage.class,
			new SqlContext(sqlQuery),
			new AbstractEmulationExecutor.ReplayOperations<
				JdbcEmulationImage,
				JdbcEmulationEntry,
				SqlContext,
				List<List<String>>
			>() {
				@Override
				public String protocolName() {
					return "JDBC";
				}

				@Override
				public String describeRequest(final SqlContext context) {
					return "query '" + context.sqlQuery() + "'";
				}

				@Override
				public List<JdbcEmulationEntry> extractEntries(final JdbcEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<JdbcEmulationEntry> findMatchingEntries(
					final List<JdbcEmulationEntry> entries,
					final SqlContext context
				) {
					return EmulationSqlRequestExecutor.this.findMatchingEntries(entries, context.sqlQuery());
				}

				@Override
				public String buildRequestKey(final SqlContext context) {
					return context.sqlQuery();
				}

				@Override
				public String extractResponseFileName(final JdbcEmulationEntry entry) {
					return entry.getResponse();
				}

				@Override
				public List<List<String>> mapResponse(final String content) {
					return SourceTable.csvToTable(content, MetricsHubConstants.TABLE_SEP);
				}

				@Override
				public List<List<String>> emptyResult() {
					return List.of();
				}
			}
		);
	}

	/**
	 * Finds emulation entries that match the given SQL query.
	 *
	 * @param entries   list of emulation entries to search
	 * @param sqlQuery  SQL query to match against the entries
	 * @return list of matching emulation entries, or an empty list if no matches are found
	 */
	List<JdbcEmulationEntry> findMatchingEntries(final List<JdbcEmulationEntry> entries, final String sqlQuery) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}

		return entries
			.stream()
			.filter(entry -> entry != null && sqlQuery.equals(entry.getQuery()))
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
