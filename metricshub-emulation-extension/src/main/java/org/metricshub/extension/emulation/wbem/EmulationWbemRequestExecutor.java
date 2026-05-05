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
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemRequestExecutor;

/**
 * WBEM request executor that replays query results from recorded emulation files.
 */
@Slf4j
public class EmulationWbemRequestExecutor extends WbemRequestExecutor {

	record WbemContext(String query, String namespace) {}

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Constructs an EmulationWbemRequestExecutor with the given round-robin manager and image cache manager.
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public EmulationWbemRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

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

		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			WbemEmulationImage.class,
			new WbemContext(query, namespace),
			new AbstractEmulationExecutor.ReplayOperations<
				WbemEmulationImage,
				WbemEmulationEntry,
				WbemContext,
				List<List<String>>
			>() {
				@Override
				public String protocolName() {
					return "WBEM";
				}

				@Override
				public String describeRequest(final WbemContext context) {
					return "query '" + context.query() + "' and namespace '" + context.namespace() + "'";
				}

				@Override
				public List<WbemEmulationEntry> extractEntries(final WbemEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<WbemEmulationEntry> findMatchingEntries(
					final List<WbemEmulationEntry> entries,
					final WbemContext context
				) {
					return EmulationWbemRequestExecutor.this.findMatchingEntries(entries, context.query(), context.namespace());
				}

				@Override
				public String buildRequestKey(final WbemContext context) {
					return context.query() + "|" + context.namespace();
				}

				@Override
				public String extractResponseFileName(final WbemEmulationEntry entry) {
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
	 * Finds emulation entries that match the given query and namespace.
	 *
	 * @param entries    list of emulation entries to search
	 * @param query      WQL query to match
	 * @param namespace  namespace to match
	 * @return list of matching emulation entries, or empty list if no matches found
	 */
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
				return query.equals(requestWql) && namespace.equals(requestNamespace);
			})
			.collect(Collectors.toCollection(ArrayList::new));
	}
}
