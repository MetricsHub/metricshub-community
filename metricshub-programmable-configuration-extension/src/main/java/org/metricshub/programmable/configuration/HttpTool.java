package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;

/**
 * This class provides a tool for executing HTTP requests with given arguments.
 */
public class HttpTool {

	/**
	 * The default HTTP timeout in seconds.
	 */
	protected static final int DEFAULT_HTTP_TIMEOUT = 60;

	/**
	 * The HTTP status code indicating a bad request.
	 */
	protected static final int BAD_REQUEST = 400;

	/**
	 * Executes an HTTP request based on the provided arguments.
	 *
	 * @param arguments A map containing the following keys: url, method, username, password, header, body, timeout, resultContent.
	 * @return A string response from the HTTP request, or null if not applicable.
	 * @throws IOException If an I/O error occurs during the request execution.
	 * @throws ClientException If the HTTP request fails with a status code of 4xx or 5xx.
	 */
	public HttpResponse execute(final Map<String, String> arguments) throws IOException, ClientException {
		final String url = arguments.get("url");
		final String method = arguments.get("method");
		final String username = arguments.get("username");
		final String password = arguments.get("password");
		final Map<String, String> headers = parseHeader(arguments.get("headers"));
		final String body = arguments.get("body");
		final var timeoutString = arguments.get("timeout");
		var timeout = DEFAULT_HTTP_TIMEOUT;
		if (timeoutString != null && !timeoutString.isBlank()) {
			timeout = Integer.valueOf(timeoutString);
		}

		return HttpClient.sendRequest(
			url,
			method == null ? "GET" : method,
			null,
			username,
			password == null ? null : password.toCharArray(),
			null,
			0,
			null,
			null,
			null,
			headers,
			body,
			timeout,
			null
		);
	}

	/**
	 * Executes an HTTP GET request with the provided arguments.
	 *
	 * @param arguments A map containing the following keys: url, username, password, header, timeout, resultContent.
	 * @return A string response from the HTTP GET request, or null if not applicable.
	 * @throws IOException     If an I/O error occurs during the request execution.
	 * @throws ClientException If the HTTP request fails with a status code of 4xx or 5xx.
	 */
	public HttpResponse get(final Map<String, String> arguments) throws IOException, ClientException {
		var args = new HashMap<>(arguments);
		args.put("method", "GET");
		return execute(args);
	}

	/**
	 * Executes an HTTP POST request with the provided arguments.
	 *
	 * @param arguments A map containing the following keys: url, username, password, header, body, timeout, resultContent.
	 * @return A string response from the HTTP POST request, or null if not applicable.
	 * @throws IOException     If an I/O error occurs during the request execution.
	 * @throws ClientException If the HTTP request fails with a status code of 4xx or 5xx.
	 */
	public HttpResponse post(final Map<String, String> arguments) throws IOException, ClientException {
		var args = new HashMap<>(arguments);
		args.put("method", "POST");
		return execute(args);
	}

	/**
	 * Parses the given string header and builds a header {@link Map}
	 *
	 * @param headers Header content as string formatted like the following example:
	 *
	 *    <pre>
	 *     Accept: application/json
	 *     Content-Encoding: utf-8
	 *    </pre>
	 *
	 * @return Map which indexes keys (header keys) to values (header values)
	 */
	private static Map<String, String> parseHeader(final String headers) {
		Map<String, String> result = new HashMap<>();
		if (headers == null || headers.isBlank()) {
			return result;
		}
		for (String line : headers.split(NEW_LINE)) {
			if (line != null && !line.trim().isEmpty()) {
				final String[] tuple = line.split(":", 2);
				if (tuple.length != 2) {
					throw new IllegalArgumentException("Invalid header format: " + line);
				}

				result.put(tuple[0].trim(), tuple[1].trim());
			}
		}

		return result;
	}
}
