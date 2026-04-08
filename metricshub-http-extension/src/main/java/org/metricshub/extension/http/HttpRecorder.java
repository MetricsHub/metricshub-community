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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	private final ObjectMapper yamlMapper;

	/**
	 * Creates a new {@link HttpRecorder} writing to the given output directory.
	 *
	 * @param recordOutputDirectory The root output directory for recorded files.
	 */
	HttpRecorder(final String recordOutputDirectory) {
		this.httpDir = Path.of(recordOutputDirectory, HTTP_SUBDIR);
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
	 * Records an HTTP request-response pair. If an identical request signature
	 * (method, path, body, headers, resultContent) already exists in the image,
	 * the call is silently skipped to avoid duplicates.
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

			final Path indexFile = httpDir.resolve(IMAGE_YAML);

			// Load existing entries or start fresh
			final List<Map<String, Object>> entries = loadExistingEntries(indexFile);

			// Normalize body: treat empty string as null for recording
			final String normalizedBody = (bodyContent == null || bodyContent.isEmpty()) ? null : bodyContent;

			// Normalize headers: treat empty map as null for recording
			final Map<String, String> normalizedHeaders = (headerContent == null || headerContent.isEmpty())
				? null
				: headerContent;

			// Check for duplicate
			if (isDuplicate(entries, method, path, normalizedBody, normalizedHeaders, resultContent)) {
				log.debug("HTTP recording - Skipping duplicate entry for {} {}", method, path);
				return;
			}

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

			// Write image.yaml
			final Map<String, Object> image = new LinkedHashMap<>();
			image.put("image", entries);
			yamlMapper.writeValue(indexFile.toFile(), image);

			log.debug("HTTP recording - Recorded {} {} -> {}", method, path, responseFileName);
		} catch (IOException e) {
			log.error("HTTP recording - Failed to record HTTP request {} {}: {}", method, path, e.getMessage());
			log.debug("HTTP recording - Error details:", e);
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
	@SuppressWarnings("unchecked")
	List<Map<String, Object>> loadExistingEntries(final Path indexFile) throws IOException {
		if (Files.isRegularFile(indexFile)) {
			final Map<String, Object> existing = yamlMapper.readValue(indexFile.toFile(), Map.class);
			final Object imageObj = existing.get("image");
			if (imageObj instanceof List) {
				return new ArrayList<>((List<Map<String, Object>>) imageObj);
			}
		}
		return new ArrayList<>();
	}

	/**
	 * Checks whether an entry with the same request signature already exists.
	 *
	 * @param entries       The current list of entries.
	 * @param method        The HTTP method.
	 * @param path          The request path.
	 * @param body          The normalized request body (may be {@code null}).
	 * @param headers       The normalized request headers (may be {@code null}).
	 * @param resultContent The result content type.
	 * @return {@code true} if a duplicate exists.
	 */
	@SuppressWarnings("unchecked")
	boolean isDuplicate(
		final List<Map<String, Object>> entries,
		final String method,
		final String path,
		final String body,
		final Map<String, String> headers,
		final ResultContent resultContent
	) {
		for (final Map<String, Object> entry : entries) {
			final Map<String, Object> request = (Map<String, Object>) entry.get("request");
			final Map<String, Object> response = (Map<String, Object>) entry.get("response");
			if (request == null || response == null) {
				continue;
			}

			final String entryMethod = (String) request.getOrDefault("method", "GET");
			if (
				Objects.equals(entryMethod, method) &&
				Objects.equals(request.get("path"), path) &&
				Objects.equals(request.get("body"), body) &&
				headersEqual(request.get("headers"), headers) &&
				resultContentEquals(response.get("resultContent"), resultContent)
			) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Compares entry headers (from YAML) with request headers for equality.
	 *
	 * @param entryHeaders The headers from the YAML entry.
	 * @param headers      The request headers to compare.
	 * @return {@code true} if both are equal (both null/empty are considered equal).
	 */
	@SuppressWarnings("unchecked")
	boolean headersEqual(final Object entryHeaders, final Map<String, String> headers) {
		final Map<String, String> entryMap = entryHeaders instanceof Map ? (Map<String, String>) entryHeaders : null;
		final boolean entryEmpty = entryMap == null || entryMap.isEmpty();
		final boolean requestEmpty = headers == null || headers.isEmpty();
		if (entryEmpty && requestEmpty) {
			return true;
		}
		if (entryEmpty || requestEmpty) {
			return false;
		}
		return entryMap.equals(headers);
	}

	/**
	 * Compares the result content from a YAML entry with the expected result content.
	 *
	 * @param entryResultContent The result content string from the YAML entry.
	 * @param resultContent      The expected result content enum value.
	 * @return {@code true} if they represent the same result content.
	 */
	boolean resultContentEquals(final Object entryResultContent, final ResultContent resultContent) {
		if (entryResultContent == null) {
			return resultContent == null;
		}
		if (resultContent == null) {
			return false;
		}
		return entryResultContent.toString().equalsIgnoreCase(resultContent.getName());
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
