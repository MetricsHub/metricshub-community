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
import org.metricshub.engine.connector.model.common.ResultContent;
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
	public String execute(final Map<String, String> arguments) throws IOException, ClientException {
		final String url = arguments.get("url");
		final String method = arguments.get("method");
		final String username = arguments.get("username");
		final String password = arguments.get("password");
		final Map<String, String> headers = parseHeader(arguments.get("hearders"));
		final String body = arguments.get("body");
		final var timeoutString = arguments.get("timeout");
		var timeout = DEFAULT_HTTP_TIMEOUT;
		if (timeoutString != null && !timeoutString.isBlank()) {
			timeout = Integer.valueOf(timeoutString);
		}
		final var resultContentString = arguments.get("resultContent");
		var resultContent = ResultContent.detect(resultContentString);
		if (resultContent == null) {
			resultContent = ResultContent.BODY;
		}

		final HttpResponse response = HttpClient.sendRequest(
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

		// The request returned an error
		final int statusCode = response.getStatusCode();
		if (statusCode >= BAD_REQUEST) {
			// If the status code is 4xx or 5xx, we throw an IOException
			// to indicate that the request failed.
			throw new ClientException("HTTP request %s %s failed with status code: %s".formatted(method, url, statusCode));
		}

		// The request has been successful
		String result;
		switch (resultContent) {
			case BODY:
				result = response.getBody();
				break;
			case HEADER:
				result = response.getHeader();
				break;
			case HTTP_STATUS:
				result = String.valueOf(statusCode);
				break;
			default:
				throw new IllegalArgumentException("Unsupported ResultContent: " + resultContent);
		}

		return result;
	}

	/**
	 * Executes an HTTP GET request with the provided arguments.
	 *
	 * @param arguments A map containing the following keys: url, username, password, header, timeout, resultContent.
	 * @return A string response from the HTTP GET request, or null if not applicable.
	 * @throws IOException     If an I/O error occurs during the request execution.
	 * @throws ClientException If the HTTP request fails with a status code of 4xx or 5xx.
	 */
	public String get(final Map<String, String> arguments) throws IOException, ClientException {
		arguments.put("method", "GET");
		arguments.remove("body");
		return execute(arguments);
	}

	/**
	 * Executes an HTTP POST request with the provided arguments.
	 *
	 * @param arguments A map containing the following keys: url, username, password, header, body, timeout, resultContent.
	 * @return A string response from the HTTP POST request, or null if not applicable.
	 * @throws IOException     If an I/O error occurs during the request execution.
	 * @throws ClientException If the HTTP request fails with a status code of 4xx or 5xx.
	 */
	public String post(final Map<String, String> arguments) throws IOException, ClientException {
		arguments.put("method", "POST");
		return execute(arguments);
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
