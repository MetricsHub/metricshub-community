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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.extension.emulation.AbstractEmulationExecutor;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.jmx.JmxConfiguration;
import org.metricshub.extension.jmx.JmxRequestExecutor;

/**
 * JMX request executor that replays query results from recorded emulation files.
 */
@Slf4j
public class EmulationJmxRequestExecutor extends JmxRequestExecutor {

	record JmxContext(String objectName, List<String> attributes, List<String> keyProperties) {}

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Constructs an EmulationJmxRequestExecutor with the given round-robin manager and image cache manager.
	 *
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public EmulationJmxRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

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
		if (jmxConfiguration instanceof JmxEmulationConfig jmxEmulationConfig) {
			emulationInputDirectory = jmxEmulationConfig.getDirectory();
		} else {
			log.warn("Hostname {} - JMX emulation configuration is not an emulation config.", hostname);
			return List.of();
		}

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - JMX emulation input directory is not configured.", hostname);
			return List.of();
		}

		// Convert Iterable to List for matching
		final List<String> attributesList = new ArrayList<>();
		if (attributes != null) {
			attributes.forEach(attributesList::add);
		}
		final List<String> keyPropertiesList = keyProperties != null ? new ArrayList<>(keyProperties) : List.of();
		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			JmxEmulationImage.class,
			new JmxContext(objectNamePattern, attributesList, keyPropertiesList),
			new AbstractEmulationExecutor.ReplayOperations<
				JmxEmulationImage,
				JmxEmulationEntry,
				JmxContext,
				List<List<String>>
			>() {
				@Override
				public String protocolName() {
					return "JMX";
				}

				@Override
				public String describeRequest(final JmxContext context) {
					return "objectName '" + context.objectName() + "'";
				}

				@Override
				public List<JmxEmulationEntry> extractEntries(final JmxEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<JmxEmulationEntry> findMatchingEntries(
					final List<JmxEmulationEntry> entries,
					final JmxContext context
				) {
					return EmulationJmxRequestExecutor.this.findMatchingEntries(
							entries,
							context.objectName(),
							context.attributes(),
							context.keyProperties()
						);
				}

				@Override
				public String buildRequestKey(final JmxContext context) {
					return context.objectName() + "|" + context.attributes() + "|" + context.keyProperties();
				}

				@Override
				public String extractResponseFileName(final JmxEmulationEntry entry) {
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
