package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;

/**
 * Records OS command executions to an emulation image ({@code image.yaml})
 * and individual result files under a {@code command/} subdirectory. The recorded
 * files are compatible with the emulation extension's playback format.
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class OsCommandRecorder {

	static final String COMMAND_SUBDIR = "command";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, OsCommandRecorder> RECORDERS = new ConcurrentHashMap<>();

	private final Path commandDir;
	private final ObjectMapper yamlMapper;

	/**
	 * Creates a new {@link OsCommandRecorder} writing to the given output directory.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 */
	OsCommandRecorder(final String recordOutputDirectory) {
		this.commandDir = Path.of(recordOutputDirectory, COMMAND_SUBDIR);
		this.yamlMapper = JsonHelper.buildYamlMapper();
	}

	/**
	 * Returns the shared {@link OsCommandRecorder} for the given output directory,
	 * creating one if it does not already exist.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 * @return The shared recorder instance.
	 */
	public static OsCommandRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, OsCommandRecorder::new);
	}

	/**
	 * Clears all cached recorder instances.
	 * Intended for testing only.
	 */
	static void clearInstances() {
		RECORDERS.clear();
	}

	/**
	 * Removes the cached recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 */
	public static void removeInstance(final String recordOutputDirectory) {
		RECORDERS.remove(recordOutputDirectory);
	}

	/**
	 * Records an OS command execution. Duplicate entries are allowed
	 * so that the emulation extension can serve them in round-robin order.
	 *
	 * @param commandLine The raw OS command line (before any interpolation).
	 * @param result      The output returned by the command execution.
	 */
	public synchronized void record(final String commandLine, final String result) {
		try {
			Files.createDirectories(commandDir);

			final Path indexFile = commandDir.resolve(IMAGE_YAML);

			// Load existing entries or start fresh
			final List<Map<String, Object>> entries = loadExistingEntries(indexFile);

			// Generate a unique result filename
			final String resultFileName = UUID.randomUUID().toString() + ".txt";

			// Write result file
			Files.writeString(commandDir.resolve(resultFileName), result != null ? result : "", StandardCharsets.UTF_8);

			// Build and append entry
			final Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("command", commandLine);
			entry.put("result", resultFileName);
			entries.add(entry);

			// Write image.yaml
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);

			log.debug("OS command recording - Recorded command -> {}", resultFileName);
		} catch (IOException e) {
			log.error("OS command recording - Failed to record command: {}", e.getMessage());
			log.debug("OS command recording - Error details:", e);
		}
	}

	/**
	 * Loads existing entries from the image.yaml file, or returns an empty list
	 * if the file does not exist.
	 *
	 * @param indexFile The path to the image.yaml file.
	 * @return A mutable list of existing entries.
	 * @throws IOException If the file cannot be read or parsed.
	 */
	List<Map<String, Object>> loadExistingEntries(final Path indexFile) throws IOException {
		if (Files.isRegularFile(indexFile)) {
			final TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {};
			final Map<String, List<Map<String, Object>>> existing = yamlMapper.readValue(indexFile.toFile(), typeRef);
			final List<Map<String, Object>> imageList = existing.get("image");
			if (imageList != null) {
				return new ArrayList<>(imageList);
			}
		}
		return new ArrayList<>();
	}
}
