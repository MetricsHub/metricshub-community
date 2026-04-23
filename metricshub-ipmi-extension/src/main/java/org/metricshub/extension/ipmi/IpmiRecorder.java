package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ipmi Extension
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
 * Records IPMI over-LAN exchanges to an emulation image ({@code image.yaml})
 * and response payload files under an {@code ipmi/} subdirectory.
 *
 * <p>The image format is:
 * <pre>
 * image:
 * - request: IpmiDetection
 *   response: uuid-random.txt
 * - request: GetSensors
 *   response: uuid-random.txt
 * </pre>
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class IpmiRecorder {

	/**
	 * Request type identifier for IPMI chassis status detection.
	 */
	public static final String IPMI_DETECTION_REQUEST = "IpmiDetection";

	/**
	 * Request type identifier for IPMI FRUs and Sensors retrieval.
	 */
	public static final String GET_SENSORS_REQUEST = "GetSensors";

	static final String IPMI_SUBDIR = "ipmi";
	static final String IMAGE_YAML = "image.yaml";

	private static final ConcurrentHashMap<String, IpmiRecorder> RECORDERS = new ConcurrentHashMap<>();

	private final Path ipmiDir;
	private final Path indexFile;
	private final ObjectMapper yamlMapper;
	private List<Map<String, Object>> entries;

	IpmiRecorder(final String recordOutputDirectory) {
		this.ipmiDir = Path.of(recordOutputDirectory, IPMI_SUBDIR);
		this.indexFile = ipmiDir.resolve(IMAGE_YAML);
		this.yamlMapper = JsonHelper.buildYamlMapper();
	}

	/**
	 * Returns a shared recorder instance for the specified output directory.
	 *
	 * @param recordOutputDirectory root recording output directory
	 * @return shared recorder instance
	 */
	public static IpmiRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, IpmiRecorder::new);
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
		final IpmiRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records an IPMI response.
	 *
	 * @param requestType the request type identifier (e.g., {@code "IpmiDetection"} or {@code "GetSensors"})
	 * @param response    the raw response string
	 */
	public synchronized void record(final String requestType, final String response) {
		try {
			Files.createDirectories(ipmiDir);
			final List<Map<String, Object>> entries = getEntries();

			final String responseFileName = UUID.randomUUID() + ".txt";
			Files.writeString(ipmiDir.resolve(responseFileName), response != null ? response : "", StandardCharsets.UTF_8);

			final Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("request", requestType);
			entry.put("response", responseFileName);
			entries.add(entry);
		} catch (IOException e) {
			log.error("IPMI recording - Failed to record {} response: {}", requestType, e.getMessage());
			log.debug("IPMI recording - Error details:", e);
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
			Files.createDirectories(ipmiDir);
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);
		} catch (IOException e) {
			log.error("IPMI recording - Failed to flush image file: {}", e.getMessage());
			log.debug("IPMI recording - Flush error details:", e);
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
