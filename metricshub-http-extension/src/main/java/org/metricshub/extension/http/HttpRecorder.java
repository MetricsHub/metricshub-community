package org.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * Records HTTP request-response pairs to an emulation image ({@code image.yaml})
 * and individual response files under a {@code http/} subdirectory. The recorded
 * files are compatible with the emulation extension's playback format.
 *
 * <p>Thread-safe: all writes are synchronized per recorder instance, and one
 * recorder is created per output directory via {@link #getInstance(String)}.
 */
@Slf4j
public class HttpRecorder {

	static final String HTTP_SUBDIR = "http";
	static final String IMAGE_YAML = "image.yaml";
	static final String DEFAULT_USERNAME = "username";
	static final char[] DEFAULT_PASSWORD = "password".toCharArray();

	private static final ConcurrentHashMap<String, HttpRecorder> RECORDERS = new ConcurrentHashMap<>();

	private final Path httpDir;
	private final Path indexFile;
	private final ObjectMapper yamlMapper;
	private List<Map<String, Object>> entries;

	/**
	 * Creates a new {@link HttpRecorder} writing to the given output directory.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 */
	HttpRecorder(final String recordOutputDirectory) {
		this.httpDir = Path.of(recordOutputDirectory, HTTP_SUBDIR);
		this.indexFile = httpDir.resolve(IMAGE_YAML);
		this.yamlMapper = JsonHelper.buildYamlMapper();
	}

	/**
	 * Returns the shared {@link HttpRecorder} for the given output directory,
	 * creating one if it does not already exist.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 * @return The shared recorder instance.
	 */
	public static HttpRecorder getInstance(final String recordOutputDirectory) {
		return RECORDERS.computeIfAbsent(recordOutputDirectory, HttpRecorder::new);
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
		final HttpRecorder recorder = RECORDERS.remove(recordOutputDirectory);
		if (recorder != null) {
			recorder.flush();
		}
	}

	/**
	 * Records an HTTP request-response pair. Duplicate entries are allowed
	 * so that the emulation extension can serve them in round-robin order.
	 *
	 * @param method          The HTTP method (GET, POST, etc.).
	 * @param path            The request path.
	 * @param bodyContent     The resolved request body (may be {@code null} or empty).
	 * @param headerContent   The resolved request headers (may be {@code null} or empty).
	 * @param resultContent   The requested result content type.
	 * @param responseContent The response content returned by the server.
	 */
	public synchronized void record(
		final String method,
		final String path,
		final String bodyContent,
		final Map<String, String> headerContent,
		final ResultContent resultContent,
		final String responseContent
	) {
		try {
			Files.createDirectories(httpDir);
			final List<Map<String, Object>> entries = getEntries();

			// Normalize body: treat empty string as null for recording
			final String normalizedBody = (bodyContent == null || bodyContent.isEmpty()) ? null : bodyContent;

			// Normalize headers: treat empty map as null for recording
			final Map<String, String> normalizedHeaders = (headerContent == null || headerContent.isEmpty())
				? null
				: headerContent;

			// Generate a unique response filename
			final String responseFileName = UUID.randomUUID().toString() + ".txt";

			// Write response file
			Files.writeString(httpDir.resolve(responseFileName), responseContent, StandardCharsets.UTF_8);

			// Build and append entry
			final Map<String, Object> entry = buildEntry(
				method,
				path,
				normalizedBody,
				normalizedHeaders,
				resultContent,
				responseFileName
			);
			entries.add(entry);

			log.debug("HTTP recording - Recorded {} {} -> {}", method, path, responseFileName);
		} catch (IOException e) {
			log.error("HTTP recording - Failed to record HTTP request {} {}: {}", method, path, e.getMessage());
			log.debug("HTTP recording - Error details:", e);
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
			Files.createDirectories(httpDir);
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);
		} catch (IOException e) {
			log.error("HTTP recording - Failed to flush image file: {}", e.getMessage());
			log.debug("HTTP recording - Flush error details:", e);
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

	/**
	 * Builds a YAML-compatible entry map for the given request-response data.
	 *
	 * @param method           The HTTP method.
	 * @param path             The request path.
	 * @param body             The request body (may be {@code null}).
	 * @param headers          The request headers (may be {@code null}).
	 * @param resultContent    The result content type.
	 * @param responseFileName The name of the response file.
	 * @return A map representing the entry.
	 */
	Map<String, Object> buildEntry(
		final String method,
		final String path,
		final String body,
		final Map<String, String> headers,
		final ResultContent resultContent,
		final String responseFileName
	) {
		final Map<String, Object> request = new LinkedHashMap<>();
		request.put("method", method);
		if (path != null) {
			request.put("path", path);
		}
		if (body != null) {
			request.put("body", body);
		}
		if (headers != null) {
			request.put("headers", new LinkedHashMap<>(headers));
		}

		final Map<String, Object> response = new LinkedHashMap<>();
		response.put("file", responseFileName);
		if (resultContent != null) {
			response.put("resultContent", resultContent.getName());
		}

		final Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("request", request);
		entry.put("response", response);
		return entry;
	}
}
