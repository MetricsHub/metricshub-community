package org.metricshub.engine.extension.recorder;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.metricshub.engine.common.helpers.JsonHelper;

/**
 * Base recorder implementation that centralizes entry buffering and image file persistence.
 *
 * @param <R> record request payload type
 */
public abstract class AbstractRecorder<R> {

	/**
	 * Name of the recorder image file.
	 */
	protected static final String IMAGE_YAML = "image.yaml";

	private final Path protocolDir;
	private final Path indexFile;
	private final ObjectMapper yamlMapper;
	private List<Map<String, Object>> entries;

	/**
	 * Creates a recorder base for a protocol-specific output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @param protocolSubdir protocol-specific subdirectory
	 */
	protected AbstractRecorder(final String recordOutputDirectory, final String protocolSubdir) {
		protocolDir = Path.of(recordOutputDirectory, protocolSubdir);
		indexFile = protocolDir.resolve(IMAGE_YAML);
		yamlMapper = JsonHelper.buildYamlMapper();
	}

	/**
	 * Records one request/response payload.
	 *
	 * @param recordRequest protocol-specific record payload
	 */
	protected synchronized void recordInternal(final R recordRequest) {
		try {
			Files.createDirectories(protocolDir);
			final List<Map<String, Object>> currentEntries = getEntries();
			final String responseFileName = writeResponsePayload(recordRequest, protocolDir);
			currentEntries.add(buildEntry(recordRequest, responseFileName));
		} catch (IOException e) {
			logRecordFailure(recordRequest, e);
		}
	}

	/**
	 * Flushes buffered entries to {@code image.yaml}.
	 */
	public synchronized void flush() {
		if (entries == null) {
			return;
		}
		try {
			Files.createDirectories(protocolDir);
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);
		} catch (IOException e) {
			logFlushFailure(e);
		}
	}

	/**
	 * Loads existing entries from disk and returns them as a mutable list.
	 *
	 * @return mutable list of existing entries
	 * @throws IOException if the image file cannot be read
	 */
	protected List<Map<String, Object>> loadExistingEntries() throws IOException {
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

	/**
	 * Returns the in-memory entries, loading them from disk on first access.
	 *
	 * @return mutable list of buffered entries
	 * @throws IOException if loading the image file fails
	 */
	private List<Map<String, Object>> getEntries() throws IOException {
		if (entries == null) {
			entries = loadExistingEntries();
		}
		return entries;
	}

	/**
	 * Writes one protocol-specific response payload and returns its file name.
	 *
	 * @param recordRequest protocol-specific request payload
	 * @param outputDir protocol-specific output directory
	 * @return response file name referenced in image entry
	 * @throws IOException if payload writing fails
	 */
	protected abstract String writeResponsePayload(R recordRequest, Path outputDir) throws IOException;

	/**
	 * Builds one YAML image entry for a recorded payload.
	 *
	 * @param recordRequest protocol-specific request payload
	 * @param responseFileName written response file name
	 * @return YAML-compatible image entry map
	 */
	protected abstract Map<String, Object> buildEntry(R recordRequest, String responseFileName);

	/**
	 * Logs a protocol-specific record failure.
	 *
	 * @param recordRequest protocol-specific request payload
	 * @param exception thrown exception
	 */
	protected abstract void logRecordFailure(R recordRequest, IOException exception);

	/**
	 * Logs a protocol-specific flush failure.
	 *
	 * @param exception thrown exception
	 */
	protected abstract void logFlushFailure(IOException exception);
}
