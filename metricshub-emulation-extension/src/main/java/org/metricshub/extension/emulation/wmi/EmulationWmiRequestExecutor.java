package org.metricshub.extension.emulation.wmi;

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
import org.metricshub.extension.emulation.AbstractEmulationExecutor;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;

/**
 * WMI request executor that replays query results from recorded emulation files.
 */
@Slf4j
public class EmulationWmiRequestExecutor implements IWinRequestExecutor {

	record WmiContext(String query, String namespace) {}

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Constructs an EmulationWmiRequestExecutor with the given round-robin manager and image cache manager.
	 *
	 * @param roundRobinManager Manager for round-robin selection of emulation images
	 * @param imageCacheManager Cache manager for emulation images to optimize performance
	 */
	public EmulationWmiRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

	@Override
	public List<List<String>> executeWmi(
		final String hostname,
		final IWinConfiguration winConfiguration,
		final String query,
		final String namespace,
		final String recordOutputDirectory
	) throws ClientException {
		if (query == null || namespace == null) {
			return List.of();
		}

		final String emulationInputDirectory;
		if (winConfiguration instanceof WmiEmulationConfig wmiEmulConfig) {
			emulationInputDirectory = wmiEmulConfig.getDirectory();
		} else {
			emulationInputDirectory = null;
		}

		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - WMI emulation input directory is not configured.", hostname);
			return List.of();
		}

		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			WmiEmulationImage.class,
			new WmiContext(query, namespace),
			new AbstractEmulationExecutor.ReplayOperations<
				WmiEmulationImage,
				WmiEmulationEntry,
				WmiContext,
				List<List<String>>
			>() {
				@Override
				public String protocolName() {
					return "WMI";
				}

				@Override
				public String describeRequest(final WmiContext context) {
					return "query '" + context.query() + "' and namespace '" + context.namespace() + "'";
				}

				@Override
				public List<WmiEmulationEntry> extractEntries(final WmiEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<WmiEmulationEntry> findMatchingEntries(
					final List<WmiEmulationEntry> entries,
					final WmiContext context
				) {
					return EmulationWmiRequestExecutor.this.findMatchingEntries(entries, context.query(), context.namespace());
				}

				@Override
				public String buildRequestKey(final WmiContext context) {
					return context.query() + "|" + context.namespace();
				}

				@Override
				public String extractResponseFileName(final WmiEmulationEntry entry) {
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
	public boolean isAcceptableException(final Throwable t) {
		return false;
	}

	@Override
	public String executeWinRemoteCommand(
		final String hostname,
		final IWinConfiguration winConfiguration,
		final String command,
		final List<String> embeddedFiles
	) throws ClientException {
		return "";
	}

	/**
	 * Finds emulation entries matching the given query and namespace.
	 *
	 * @param entries    list of emulation entries to search
	 * @param query      WQL query to match
	 * @param namespace  namespace to match
	 * @return list of matching emulation entries, or empty list if no matches found
	 */
	List<WmiEmulationEntry> findMatchingEntries(
		final List<WmiEmulationEntry> entries,
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
