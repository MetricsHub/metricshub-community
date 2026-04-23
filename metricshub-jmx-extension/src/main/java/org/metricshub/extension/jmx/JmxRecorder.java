package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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
 * Records JMX query exchanges to an emulation image ({@code image.yaml})
 * and response payload files under a {@code jmx/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - request:
 *     objectName: com.example:type=Example
 *     attributes:
 *       - Attribute1
 *     keyProperties:
 *       - scope
 *   response: uuid-random.txt
 * </pre>
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class JmxRecorder {

	static final String JMX_SUBDIR = "jmx";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, JmxRecorder> RECORDERS = new ConcurrentHashMap<>();

	private final Path jmxDir;
	private final Path indexFile;
	private final ObjectMapper yamlMapper;
	private List<Map<String, Object>> entries;

	JmxRecorder(final String recordOutputDirectory) {
		this.jmxDir = Path.of(recordOutputDirectory, JMX_SUBDIR);
		this.indexFile = jmxDir.resolve(IMAGE_YAML);
		this.yamlMapper = JsonHelper.buildYamlMapper();
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static JmxRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, JmxRecorder::new);
	}

	/**
	 * Clears all cached recorder instances.
	 * Intended for tests.
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
		final JmxRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records a JMX response.
	 *
	 * @param objectName    the MBean ObjectName pattern
	 * @param attributes    the list of attributes fetched
	 * @param keyProperties the list of key properties included
	 * @param csvResponse   the response already serialized as CSV
	 */
	public synchronized void record(
		final String objectName,
		final List<String> attributes,
		final List<String> keyProperties,
		final String csvResponse
	) {
		try {
			Files.createDirectories(jmxDir);
			final List<Map<String, Object>> entries = getEntries();

			final String responseFileName = UUID.randomUUID() + ".txt";
			Files.writeString(
				jmxDir.resolve(responseFileName),
				csvResponse != null ? csvResponse : "",
				StandardCharsets.UTF_8
			);

			final Map<String, Object> request = new LinkedHashMap<>();
			request.put("objectName", objectName);
			request.put("attributes", attributes != null ? attributes : List.of());
			request.put("keyProperties", keyProperties != null ? keyProperties : List.of());

			final Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("request", request);
			entry.put("response", responseFileName);
			entries.add(entry);
		} catch (IOException e) {
			log.error("JMX recording - Failed to record JMX query: {}", e.getMessage());
			log.debug("JMX recording - Error details:", e);
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
			Files.createDirectories(jmxDir);
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);
		} catch (IOException e) {
			log.error("JMX recording - Failed to flush image file: {}", e.getMessage());
			log.debug("JMX recording - Flush error details:", e);
		}
	}

	/**
	 * Returns the in-memory recording entries, loading them from disk on first access.
	 *
	 * @return mutable list of recording entries
	 * @throws IOException if the index file cannot be read
	 */
	private List<Map<String, Object>> getEntries() throws IOException {
		if (entries == null) {
			entries = loadExistingEntries(indexFile);
		}
		return entries;
	}

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
