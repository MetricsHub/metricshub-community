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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationPathHelper;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.SqlRequestExecutor;

/**
 * SQL request executor that replays query results from recorded emulation files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationSqlRequestExecutor extends SqlRequestExecutor {

	private static final String JDBC_EMULATION_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;

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

		final String emulationInputDirectory = emulationConfiguration != null && emulationConfiguration.getJdbc() != null
			? emulationConfiguration.getJdbc().getDirectory()
			: null;

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - JDBC emulation input directory is not configured.", hostname);
			return List.of();
		}

		final Path jdbcDir = Path.of(emulationInputDirectory);
		final Path indexFile = jdbcDir.resolve(JDBC_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - JDBC emulation index file not found: {}", hostname, indexFile);
			return List.of();
		}

		final JdbcEmulationImage emulationImage;
		try {
			emulationImage = JsonHelper.buildYamlMapper().readValue(indexFile.toFile(), JdbcEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse JDBC emulation index file {}. Error: {}",
				hostname,
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - JDBC emulation index parse error:", hostname, e);
			return List.of();
		}

		final List<JdbcEmulationEntry> entries = emulationImage != null
			? emulationImage.getImage()
			: Collections.emptyList();

		final List<JdbcEmulationEntry> matchingEntries = findMatchingEntries(entries, sqlQuery);
		if (matchingEntries.isEmpty()) {
			log.warn("Hostname {} - No matching JDBC emulation entry found for query '{}'.", hostname, sqlQuery);
			return List.of();
		}

		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			sqlQuery,
			matchingEntries.size()
		);

		final String responseFileName = matchingEntries.get(index).getResponse();
		if (responseFileName == null || responseFileName.isBlank()) {
			log.warn("Hostname {} - Matched JDBC emulation entry has no response file.", hostname);
			return List.of();
		}

		final Path responseFile = EmulationPathHelper.resolveSecurely(jdbcDir, responseFileName);
		if (responseFile == null) {
			return List.of();
		}
		try {
			final String content = Files.readString(responseFile, StandardCharsets.UTF_8);
			return SourceTable.csvToTable(content, MetricsHubConstants.TABLE_SEP);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to read JDBC emulation response file {}. Error: {}",
				hostname,
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - JDBC emulation response read error:", hostname, e);
			return List.of();
		}
	}

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
