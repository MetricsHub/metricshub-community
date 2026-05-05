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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.extension.recorder.AbstractRecorder;

/**
 * Records OS command executions to an emulation image ({@code image.yaml})
 * and individual result files under a {@code command/} subdirectory. The recorded
 * files are compatible with the emulation extension's playback format.
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class OsCommandRecorder extends AbstractRecorder<OsCommandRecorder.OsCommandRecordRequest> {

	static final String COMMAND_SUBDIR = "command";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, OsCommandRecorder> RECORDERS = new ConcurrentHashMap<>();

	record OsCommandRecordRequest(String commandLine, String result) {}

	/**
	 * Creates a new {@link OsCommandRecorder} writing to the given output directory.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 */
	OsCommandRecorder(final String recordOutputDirectory) {
		super(recordOutputDirectory, COMMAND_SUBDIR);
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
	 * Flushes the recorder for the specified output directory and removes it from cache.
	 *
	 * @param recordOutputDirectory root recording output directory
	 */
	public static void flushAndRemoveInstance(final String recordOutputDirectory) {
		final OsCommandRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records an OS command execution. Duplicate entries are allowed
	 * so that the emulation extension can serve them in round-robin order.
	 *
	 * @param commandLine The raw OS command line (before any interpolation).
	 * @param result      The output returned by the command execution.
	 */
	public void record(final String commandLine, final String result) {
		recordInternal(new OsCommandRecordRequest(commandLine, result));
	}

	/**
	 * Flushes buffered entries to {@code image.yaml}.
	 */
	@Override
	protected String writeResponsePayload(final OsCommandRecordRequest request, final Path outputDir) throws IOException {
		final String resultFileName = UUID.randomUUID() + ".txt";
		Files.writeString(
			outputDir.resolve(resultFileName),
			request.result() != null ? request.result() : "",
			StandardCharsets.UTF_8
		);
		return resultFileName;
	}

	/**
	 * Returns the in-memory recording entries, loading them from disk on first access.
	 *
	 * @return mutable list of recording entries
	 * @throws IOException if the index file cannot be read
	 */
	@Override
	protected Map<String, Object> buildEntry(final OsCommandRecordRequest request, final String responseFileName) {
		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("command", request.commandLine());
		entry.put("result", responseFileName);
		return entry;
	}

	/**
	 * Loads existing entries from the image.yaml file, or returns an empty list
	 * if the file does not exist.
	 *
	 * @param indexFile The path to the image.yaml file.
	 * @return A mutable list of existing entries.
	 * @throws IOException If the file cannot be read or parsed.
	 */
	@Override
	protected void logRecordFailure(final OsCommandRecordRequest request, final IOException exception) {
		log.error("OS command recording - Failed to record command: {}", exception.getMessage());
		log.debug("OS command recording - Error details:", exception);
	}

	@Override
	protected void logFlushFailure(final IOException exception) {
		log.error("OS command recording - Failed to flush image file: {}", exception.getMessage());
		log.debug("OS command recording - Flush error details:", exception);
	}
}
