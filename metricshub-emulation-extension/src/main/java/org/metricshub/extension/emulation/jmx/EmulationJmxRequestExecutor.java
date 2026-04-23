package org.metricshub.extension.emulation.jmx;

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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationPathHelper;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.jmx.JmxConfiguration;
import org.metricshub.extension.jmx.JmxRequestExecutor;

/**
 * JMX request executor that replays query results from recorded emulation files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationJmxRequestExecutor extends JmxRequestExecutor {

	private static final String JMX_EMULATION_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;
	private final EmulationImageCacheManager imageCacheManager;

	@Override
	public List<List<String>> fetchMBean(
		final JmxConfiguration jmxConfiguration,
		final String objectNamePattern,
		final Iterable<String> attributes,
		final Collection<String> keyProperties,
		final String resourceHostname,
		final String recordOutputDirectory
	) throws Exception {
		final String hostname = jmxConfiguration != null ? jmxConfiguration.getHostname() : resourceHostname;

		if (objectNamePattern == null) {
			return List.of();
		}

		// Determine emulation directory from JmxEmulationConfig
		final String emulationInputDirectory;
		if (jmxConfiguration instanceof org.metricshub.extension.emulation.JmxEmulationConfig jmxEmulationConfig) {
			emulationInputDirectory = jmxEmulationConfig.getDirectory();
		} else {
			log.warn("Hostname {} - JMX emulation configuration is not an emulation config.", hostname);
			return List.of();
		}

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - JMX emulation input directory is not configured.", hostname);
			return List.of();
		}

		final Path jmxDir = Path.of(emulationInputDirectory);
		final Path indexFile = jmxDir.resolve(JMX_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - JMX emulation index file not found: {}", hostname, indexFile);
			return List.of();
		}

		final JmxEmulationImage emulationImage;
		try {
			emulationImage = imageCacheManager.getImage(indexFile, JmxEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse JMX emulation index file {}. Error: {}",
				hostname,
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - JMX emulation index parse error:", hostname, e);
			return List.of();
		}

		final List<JmxEmulationEntry> entries = emulationImage != null
			? emulationImage.getImage()
			: Collections.emptyList();

		// Convert Iterable to List for matching
		final List<String> attributesList = new ArrayList<>();
		if (attributes != null) {
			attributes.forEach(attributesList::add);
		}
		final List<String> keyPropertiesList = keyProperties != null ? new ArrayList<>(keyProperties) : List.of();

		final List<JmxEmulationEntry> matchingEntries = findMatchingEntries(
			entries,
			objectNamePattern,
			attributesList,
			keyPropertiesList
		);
		if (matchingEntries.isEmpty()) {
			log.warn("Hostname {} - No matching JMX emulation entry found for objectName '{}'.", hostname, objectNamePattern);
			return List.of();
		}

		final String requestKey = objectNamePattern + "|" + attributesList + "|" + keyPropertiesList;
		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			requestKey,
			matchingEntries.size()
		);

		final String responseFileName = matchingEntries.get(index).getResponse();
		if (responseFileName == null || responseFileName.isBlank()) {
			log.warn("Hostname {} - Matched JMX emulation entry has no response file.", hostname);
			return List.of();
		}

		final Path responseFile = EmulationPathHelper.resolveSecurely(jmxDir, responseFileName);
		if (responseFile == null) {
			return List.of();
		}
		try {
			final String content = Files.readString(responseFile, StandardCharsets.UTF_8);
			return SourceTable.csvToTable(content, MetricsHubConstants.TABLE_SEP);
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to read JMX emulation response file {}. Error: {}",
				hostname,
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - JMX emulation response read error:", hostname, e);
			return List.of();
		}
	}

	@Override
	public boolean checkConnection(final JmxConfiguration configuration, final String resourceHostname) {
		return true;
	}

	/**
	 * Returns the given list if non-null, or an empty list otherwise.
	 *
	 * @param list the list to normalize
	 * @return the original list or {@link List#of()} when {@code null}
	 */
	static List<String> normalizeList(final List<String> list) {
		return list != null ? list : List.of();
	}

	/**
	 * Checks whether the given request matches the specified objectName, attributes, and keyProperties.
	 *
	 * @param request       the emulation request to check
	 * @param objectName    the expected object name
	 * @param attributes    the expected attributes
	 * @param keyProperties the expected key properties
	 * @return {@code true} if all fields match
	 */
	static boolean matchesRequest(
		final JmxEmulationRequest request,
		final String objectName,
		final List<String> attributes,
		final List<String> keyProperties
	) {
		//CHECKSTYLE:OFF
		return (
			Objects.equals(objectName, request.getObjectName()) &&
			Objects.equals(normalizeList(attributes), normalizeList(request.getAttributes())) &&
			Objects.equals(normalizeList(keyProperties), normalizeList(request.getKeyProperties()))
		);
		//CHECKSTYLE:ON
	}

	/**
	 * Finds all emulation entries matching the specified objectName, attributes, and keyProperties.
	 *
	 * @param entries       The list of emulation entries to search through.
	 * @param objectName    The object name to match.
	 * @param attributes    The list of attributes to match.
	 * @param keyProperties The list of key properties to match.
	 * @return A list of matching emulation entries.
	 */
	List<JmxEmulationEntry> findMatchingEntries(
		final List<JmxEmulationEntry> entries,
		final String objectName,
		final List<String> attributes,
		final List<String> keyProperties
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
				return matchesRequest(entry.getRequest(), objectName, attributes, keyProperties);
			})
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
