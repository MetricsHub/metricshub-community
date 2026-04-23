package org.metricshub.extension.emulation.wbem;

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
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemRequestExecutor;

/**
 * WBEM request executor that replays query results from recorded emulation files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationWbemRequestExecutor extends WbemRequestExecutor {

	private static final String WBEM_EMULATION_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;

	@Override
	public List<List<String>> executeWbem(
		final String hostname,
		final WbemConfiguration wbemConfig,
		final String query,
		final String namespace,
		final TelemetryManager telemetryManager,
		final String resourceHostname
	) throws ClientException {
		if (query == null || namespace == null || telemetryManager == null) {
			return List.of();
		}

		final EmulationConfiguration emulationConfiguration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);

		final String emulationInputDirectory = emulationConfiguration != null && emulationConfiguration.getWbem() != null
			? emulationConfiguration.getWbem().getDirectory()
			: null;

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - WBEM emulation input directory is not configured.", hostname);
			return List.of();
		}

		final Path wbemDir = Path.of(emulationInputDirectory);
		final Path indexFile = wbemDir.resolve(WBEM_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - WBEM emulation index file not found: {}", hostname, indexFile);
			return List.of();
		}

		final WbemEmulationImage emulationImage;
		try {
			emulationImage = JsonHelper.buildYamlMapper().readValue(indexFile.toFile(), WbemEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse WBEM emulation index file {}. Error: {}",
				hostname,
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - WBEM emulation index parse error:", hostname, e);
			return List.of();
		}

		final List<WbemEmulationEntry> entries = emulationImage != null
			? emulationImage.getImage()
			: Collections.emptyList();

		final List<WbemEmulationEntry> matchingEntries = findMatchingEntries(entries, query, namespace);
		if (matchingEntries.isEmpty()) {
			log.warn(
				"Hostname {} - No matching WBEM emulation entry found for query '{}' and namespace '{}'.",
				hostname,
				query,
				namespace
			);
			return List.of();
		}

		final String requestKey = query + "|" + namespace;
		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			requestKey,
			matchingEntries.size()
		);

		final String responseFileName = matchingEntries.get(index).getResponse();
		if (responseFileName == null || responseFileName.isBlank()) {
			log.warn("Hostname {} - Matched WBEM emulation entry has no response file.", hostname);
			return List.of();
		}

		final Path responseFile = EmulationPathHelper.resolveSecurely(wbemDir, responseFileName);
		if (responseFile == null) {
			return List.of();
		}
		try {
			final String content = Files.readString(responseFile, StandardCharsets.UTF_8);
			return SourceTable.csvToTable(content, MetricsHubConstants.TABLE_SEP);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to read WBEM emulation response file {}. Error: {}",
				hostname,
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - WBEM emulation response read error:", hostname, e);
			return List.of();
		}
	}

	List<WbemEmulationEntry> findMatchingEntries(
		final List<WbemEmulationEntry> entries,
		final String query,
		final String namespace
	) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}

		return entries
			.stream()
			.filter(entry -> {
				if (entry == null || entry.getRequest() == null) {
					return false;
				}

				final String requestWql = entry.getRequest().getWql();
				final String requestNamespace = entry.getRequest().getNamespace();
				return query.equals(requestWql) && namespace.equalsIgnoreCase(requestNamespace);
			})
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
