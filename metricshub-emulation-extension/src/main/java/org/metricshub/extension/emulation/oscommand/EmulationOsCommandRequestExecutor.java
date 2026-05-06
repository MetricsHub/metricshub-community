package org.metricshub.extension.emulation.oscommand;

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
import org.metricshub.extension.emulation.AbstractEmulationExecutor;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;

/**
 * OS command replay executor that delegates common image replay behavior to {@link AbstractEmulationExecutor}.
 */
@Slf4j
public class EmulationOsCommandRequestExecutor {

	private final AbstractEmulationExecutor executorHelper;

	/**
	 * Creates the OS command replay executor.
	 *
	 * @param roundRobinManager round-robin manager shared across emulation executors
	 * @param imageCacheManager cache manager used to reuse parsed image files
	 */
	public EmulationOsCommandRequestExecutor(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		this.executorHelper = new AbstractEmulationExecutor(roundRobinManager, imageCacheManager);
	}

	/**
	 * Replays an OS command output from emulation files.
	 *
	 * @param hostname hostname used for logging
	 * @param emulationConfiguration emulation configuration of the host
	 * @param commandLine command line to replay
	 * @return emulated command output, or empty string when no replay is available
	 */
	public String execute(
		final String hostname,
		final EmulationConfiguration emulationConfiguration,
		final String commandLine
	) {
		final String emulationInputDirectory = resolveEmulationInputDirectory(emulationConfiguration);
		return executorHelper.replayFromImage(
			hostname,
			emulationInputDirectory,
			OsCommandEmulationImage.class,
			commandLine,
			new AbstractEmulationExecutor.ReplayOperations<
				OsCommandEmulationImage,
				OsCommandEmulationEntry,
				String,
				String
			>() {
				@Override
				public String protocolName() {
					return "OS command";
				}

				@Override
				public String describeRequest(final String context) {
					return "command '" + context + "'";
				}

				@Override
				public List<OsCommandEmulationEntry> extractEntries(final OsCommandEmulationImage image) {
					return image != null ? image.getImage() : Collections.emptyList();
				}

				@Override
				public List<OsCommandEmulationEntry> findMatchingEntries(
					final List<OsCommandEmulationEntry> entries,
					final String context
				) {
					return EmulationOsCommandRequestExecutor.this.findMatchingEntries(entries, context);
				}

				@Override
				public String buildRequestKey(final String context) {
					return context;
				}

				@Override
				public String extractResponseFileName(final OsCommandEmulationEntry entry) {
					return entry.getResult();
				}

				@Override
				public String mapResponse(final String content) {
					return content;
				}

				@Override
				public String emptyResult() {
					return "";
				}
			}
		);
	}

	/**
	 * Finds emulation entries that match the given command line.
	 *
	 * @param entries list of emulation entries to search
	 * @param commandLine command line to match
	 * @return list of matching emulation entries, or empty list if no matches found
	 */
	List<OsCommandEmulationEntry> findMatchingEntries(
		final List<OsCommandEmulationEntry> entries,
		final String commandLine
	) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}
		return entries
			.stream()
			.filter(entry -> entry != null && commandLine.equals(entry.getCommand()))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Resolves the emulation input directory for OS command replay.
	 *
	 * <p>Priority order:
	 * <ol>
	 * <li>OS command emulation directory</li>
	 * <li>SSH emulation directory (fallback)</li>
	 * </ol>
	 *
	 * @param configuration emulation configuration for the current host
	 * @return resolved directory path, or {@code null} when not configured
	 */
	private String resolveEmulationInputDirectory(final EmulationConfiguration configuration) {
		if (configuration == null) {
			return null;
		}
		if (configuration.getOscommand() != null && configuration.getOscommand().getDirectory() != null) {
			return configuration.getOscommand().getDirectory();
		}
		if (configuration.getSsh() != null) {
			return configuration.getSsh().getDirectory();
		}
		return null;
	}
}
