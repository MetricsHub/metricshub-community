package org.metricshub.extension.emulation.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.oscommand.OsCommandService;

/**
 * Emulated OS command service that replays command outputs from recorded files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationOsCommandService extends OsCommandService {

	private static final String COMMAND_EMULATION_YAML = "image.yaml";

	private final EmulationRoundRobinManager roundRobinManager;

	@Override
	public OsCommandResult runOsCommand(
		final String commandLine,
		final TelemetryManager telemetryManager,
		final Long commandTimeout,
		final boolean isExecuteLocally,
		final boolean isLocalhost,
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles
	)
		throws IOException, ClientException, InterruptedException, java.util.concurrent.TimeoutException, NoCredentialProvidedException, ControlledSshException {
		if (commandLine == null || telemetryManager == null) {
			throw new IllegalArgumentException("commandLine and telemetryManager cannot be null.");
		}

		final EmulationConfiguration emulationConfiguration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);
		final String emulationInputDirectory = resolveEmulationInputDirectory(emulationConfiguration);
		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - Emulation input directory is not configured.", telemetryManager.getHostname());
			return new OsCommandResult("", commandLine);
		}

		final Path commandDir = Path.of(emulationInputDirectory);
		final Path indexFile = commandDir.resolve(COMMAND_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn(
				"Hostname {} - OS command emulation index file not found: {}",
				telemetryManager.getHostname(),
				indexFile
			);
			return new OsCommandResult("", commandLine);
		}

		final OsCommandEmulationImage emulationImage;
		try {
			emulationImage = JsonHelper.buildYamlMapper().readValue(indexFile.toFile(), OsCommandEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse OS command emulation index file {}. Error: {}",
				telemetryManager.getHostname(),
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - OS command emulation index parse error:", telemetryManager.getHostname(), e);
			return new OsCommandResult("", commandLine);
		}

		final List<OsCommandEmulationEntry> entries = emulationImage != null
			? emulationImage.getImage()
			: Collections.emptyList();
		if (entries == null || entries.isEmpty()) {
			log.warn("Hostname {} - OS command emulation index is empty: {}", telemetryManager.getHostname(), indexFile);
			return new OsCommandResult("", commandLine);
		}

		final List<OsCommandEmulationEntry> matchingEntries = findMatchingEntries(entries, commandLine);
		if (matchingEntries.isEmpty()) {
			log.warn(
				"Hostname {} - No matching OS command emulation entry found for command: {}",
				telemetryManager.getHostname(),
				commandLine
			);
			return new OsCommandResult("", commandLine);
		}

		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			commandLine,
			matchingEntries.size()
		);
		final OsCommandEmulationEntry matchingEntry = matchingEntries.get(index);
		final String resultFileName = matchingEntry.getResult();
		if (resultFileName == null || resultFileName.isBlank()) {
			log.warn("Hostname {} - Matched OS command emulation entry has no result file.", telemetryManager.getHostname());
			return new OsCommandResult("", commandLine);
		}

		final Path responseFile = commandDir.resolve(resultFileName);
		try {
			final String result = Files.readString(responseFile, StandardCharsets.UTF_8);
			return new OsCommandResult(result, commandLine);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to read OS command emulation response file {}. Error: {}",
				telemetryManager.getHostname(),
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - OS command emulation response file read error:", telemetryManager.getHostname(), e);
			return new OsCommandResult("", commandLine);
		}
	}

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
