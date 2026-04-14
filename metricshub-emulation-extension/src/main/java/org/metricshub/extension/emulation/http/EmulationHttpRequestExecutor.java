package org.metricshub.extension.emulation.http;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MacrosUpdater;
import org.metricshub.engine.common.helpers.MapHelper;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.http.HttpRequestExecutor;
import org.metricshub.extension.http.utils.Body;
import org.metricshub.extension.http.utils.Header;
import org.metricshub.extension.http.utils.HttpRequest;

/**
 * An HTTP request executor that reads responses from emulation files instead of
 * making real network calls. It looks up the request (method, path, body, headers)
 * in the {@code image.yaml} index file and returns the content of the
 * referenced response file. When multiple entries match the same request,
 * responses are served in round-robin order via {@link EmulationRoundRobinManager}.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationHttpRequestExecutor extends HttpRequestExecutor {

	private static final String HTTP_EMULATION_YAML = "image.yaml";
	private static final String DEFAULT_USERNAME = "username";
	private static final char[] DEFAULT_PASSWORD = "password".toCharArray();

	private final EmulationRoundRobinManager roundRobinManager;

	@Override
	public String executeHttp(
		final HttpRequest httpRequest,
		final boolean logMode,
		final TelemetryManager telemetryManager
	) {
		final EmulationConfiguration emulationConfiguration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);
		final String emulationInputDirectory = emulationConfiguration != null && emulationConfiguration.getHttp() != null
			? emulationConfiguration.getHttp().getDirectory()
			: null;
		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			log.warn("Hostname {} - Emulation input directory is not configured.", httpRequest.getHostname());
			return null;
		}

		final Path httpDir = Path.of(emulationInputDirectory);
		final Path indexFile = httpDir.resolve(HTTP_EMULATION_YAML);

		if (!Files.isRegularFile(indexFile)) {
			log.warn("Hostname {} - HTTP emulation index file not found: {}", httpRequest.getHostname(), indexFile);
			return null;
		}

		// Parse the emulation index
		final HttpEmulationImage emulationImage;
		try {
			emulationImage = JsonHelper.buildYamlMapper().readValue(indexFile.toFile(), HttpEmulationImage.class);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to parse HTTP emulation index file {}. Error: {}",
				httpRequest.getHostname(),
				indexFile,
				e.getMessage()
			);
			log.debug("Hostname {} - HTTP emulation index parse error:", httpRequest.getHostname(), e);
			return null;
		}

		final List<HttpEmulationEntry> entries = emulationImage.getImage();
		if (entries == null || entries.isEmpty()) {
			log.warn("Hostname {} - HTTP emulation index is empty: {}", httpRequest.getHostname(), indexFile);
			return null;
		}

		// Resolve macros in the authentication token (same logic as HttpRequestExecutor)
		final String httpRequestAuthToken = httpRequest.getAuthenticationToken();
		final String authenticationToken = MacrosUpdater.update(
			httpRequestAuthToken,
			DEFAULT_USERNAME,
			DEFAULT_PASSWORD,
			httpRequestAuthToken,
			httpRequest.getHostname(),
			false
		);

		// Resolve body and headers using the same macro substitution as HttpRequestExecutor
		final String method = httpRequest.getMethod() != null ? httpRequest.getMethod().toUpperCase() : "GET";
		final String path = httpRequest.getPath();
		final Body body = httpRequest.getBody();
		final String resolvedBody = body != null
			? body.getContent(DEFAULT_USERNAME, DEFAULT_PASSWORD, authenticationToken, httpRequest.getHostname())
			: null;
		final Header header = httpRequest.getHeader();
		final Map<String, String> resolvedHeaders = header != null
			? header.getContent(DEFAULT_USERNAME, DEFAULT_PASSWORD, authenticationToken, httpRequest.getHostname())
			: Collections.emptyMap();
		final ResultContent resultContent = httpRequest.getResultContent();

		// Find all matching entries
		final List<HttpEmulationEntry> matchingEntries = findMatchingEntries(
			entries,
			method,
			path,
			resolvedBody,
			resolvedHeaders,
			resultContent
		);

		if (matchingEntries.isEmpty()) {
			log.warn(
				"Hostname {} - No matching HTTP emulation entry found for {} {}",
				httpRequest.getHostname(),
				method,
				path
			);
			return null;
		}

		// Compute request key and get the next round-robin entry
		final String requestKey = buildRequestKey(method, path, resolvedBody, resolvedHeaders, resultContent);
		final int index = roundRobinManager.nextIndex(
			indexFile.toAbsolutePath().toString(),
			requestKey,
			matchingEntries.size()
		);
		final HttpEmulationEntry matchingEntry = matchingEntries.get(index);

		// Read the response file
		final String responseFileName = matchingEntry.getResponse().getFile();
		final Path responseFile = httpDir.resolve(responseFileName);

		try {
			return Files.readString(responseFile, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error(
				"Hostname {} - Failed to read HTTP emulation response file {}. Error: {}",
				httpRequest.getHostname(),
				responseFile,
				e.getMessage()
			);
			log.debug("Hostname {} - HTTP emulation response file read error:", httpRequest.getHostname(), e);
			return null;
		}
	}

	/**
	 * Finds all entries in the emulation image that match the given HTTP
	 * method, path, body, headers, and result content.
	 *
	 * @param entries       The list of emulation entries to search.
	 * @param method        The HTTP method (GET, POST, etc.).
	 * @param path          The request path.
	 * @param body          The request body (may be {@code null}).
	 * @param headers       The request headers as key-value pairs (may be {@code null}).
	 * @param resultContent The expected result content type.
	 * @return A list of all matching entries (may be empty).
	 */
	List<HttpEmulationEntry> findMatchingEntries(
		final List<HttpEmulationEntry> entries,
		final String method,
		final String path,
		final String body,
		final Map<String, String> headers,
		final ResultContent resultContent
	) {
		final List<HttpEmulationEntry> matches = new ArrayList<>();
		for (final HttpEmulationEntry entry : entries) {
			final HttpEmulationRequest request = entry.getRequest();
			if (request == null) {
				continue;
			}

			final String entryMethod = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";

			if (
				entryMethod.equals(method) &&
				nullableEquals(request.getPath(), path) &&
				nullableEquals(request.getBody(), body) &&
				headersMatch(request.getHeaders(), headers) &&
				resultContentMatches(entry.getResponse(), resultContent)
			) {
				matches.add(entry);
			}
		}
		return matches;
	}

	/**
	 * Compares an entry value with a request value using strict equality.
	 * Both {@code null} values are considered equal.
	 *
	 * @param entryValue   The value defined in the emulation entry.
	 * @param requestValue The value from the actual HTTP request.
	 * @return {@code true} if both values are equal.
	 */
	private boolean nullableEquals(final String entryValue, final String requestValue) {
		if (entryValue == null) {
			return requestValue == null;
		}
		return entryValue.equals(requestValue);
	}

	/**
	 * Checks whether the entry headers match the request headers
	 * using {@link MapHelper#areEqual(Map, Map)}.
	 *
	 * @param entryHeaders   The headers from the emulation entry.
	 * @param requestHeaders The resolved headers from the HTTP request.
	 * @return {@code true} if the headers are deeply equal.
	 */
	private boolean headersMatch(
		final LinkedHashMap<String, String> entryHeaders,
		final Map<String, String> requestHeaders
	) {
		final Map<String, String> normalizedEntry = entryHeaders == null ? Collections.emptyMap() : entryHeaders;
		final Map<String, String> normalizedRequest = requestHeaders == null ? Collections.emptyMap() : requestHeaders;
		return MapHelper.areEqual(normalizedEntry, normalizedRequest);
	}

	/**
	 * Checks whether the response's result content strictly matches the requested one.
	 *
	 * @param response      The emulation response entry.
	 * @param resultContent The result content requested by the HTTP request.
	 * @return {@code true} if the result content values are equal.
	 */
	private boolean resultContentMatches(final HttpEmulationResponse response, final ResultContent resultContent) {
		if (response == null || response.getResultContent() == null) {
			return resultContent == null;
		}
		return response.getResultContent().equals(resultContent);
	}

	/**
	 * Builds a unique request key from the HTTP request fields.
	 * This key is used by the round-robin manager to track which response
	 * was last served for a specific request signature.
	 *
	 * @param method        The HTTP method.
	 * @param path          The request path.
	 * @param body          The resolved request body (may be {@code null}).
	 * @param headers       The resolved request headers (may be empty).
	 * @param resultContent The result content type.
	 * @return A string key uniquely identifying this request.
	 */
	String buildRequestKey(
		final String method,
		final String path,
		final String body,
		final Map<String, String> headers,
		final ResultContent resultContent
	) {
		final StringBuilder key = new StringBuilder();
		key.append(method).append('|');
		key.append(path != null ? path : "").append('|');
		key.append(body != null ? body : "").append('|');
		if (headers != null && !headers.isEmpty()) {
			key.append(
				headers
					.entrySet()
					.stream()
					.sorted(Map.Entry.comparingByKey())
					.map(e -> e.getKey() + "=" + e.getValue())
					.collect(Collectors.joining(","))
			);
		}
		key.append('|');
		key.append(resultContent != null ? resultContent.name() : "");
		return key.toString();
	}
}
