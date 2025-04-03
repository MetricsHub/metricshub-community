package org.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.springframework.util.Assert.notNull;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.http.utils.Body;
import org.metricshub.extension.http.utils.Header;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.extension.http.utils.UrlHelper;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.sentrysoftware.metricshub.engine.common.exception.RetryableException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.MacrosUpdater;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.strategy.utils.RetryOperation;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Executes HTTP requests configured through {@link HttpRequest} objects.
 * This executor supports dynamic substitution of request parameters,
 * authentication handling, and logging of request execution details.
 */
@Slf4j
public class HttpRequestExecutor {

	private static final String MASK = "*****";
	private static final char[] CHAR_ARRAY_MASK = MASK.toCharArray();
	private static final String HOSTNAME_CANNOT_BE_NULL = "hostname cannot be null";
	private static final String PROTOCOL_CANNOT_BE_NULL = "protocol cannot be null";

	/**
	 * Executes the given HTTP request
	 *
	 * @param httpRequest The {@link HttpRequest} values.
	 * @param logMode     Whether or not logging is enabled.
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @return The result of the execution of the given HTTP request.
	 */
	@WithSpan("HTTP")
	public String executeHttp(
		@SpanAttribute("http.config") @NonNull HttpRequest httpRequest,
		final boolean logMode,
		final TelemetryManager telemetryManager
	) {
		final HttpConfiguration httpConfiguration = httpRequest.getHttpConfiguration();
		notNull(httpConfiguration, PROTOCOL_CANNOT_BE_NULL);

		final String hostname = httpRequest.getHostname();
		notNull(hostname, HOSTNAME_CANNOT_BE_NULL);

		// Get the HTTP method (GET, POST, DELETE, PUT, ...). Default: GET
		String requestMethod = httpRequest.getMethod();
		final String method = requestMethod != null ? requestMethod : "GET";

		// Username and password
		final String username = httpConfiguration.getUsername();
		final char[] password = httpConfiguration.getPassword();

		// Update macros in the authentication token
		final String httpRequestAuthToken = httpRequest.getAuthenticationToken();
		final String authenticationToken = MacrosUpdater.update(
			httpRequestAuthToken,
			username,
			password,
			httpRequestAuthToken,
			hostname,
			false
		);

		// Get the header to send
		final Header header = httpRequest.getHeader();

		// This will get the header content as a new map by updating all the known macros
		final Map<String, String> headerContent = header == null
			? Collections.emptyMap()
			: header.getContent(username, password, authenticationToken, hostname);

		// This will get the header content as a new map by updating all the known macros
		// except sensitive data such as password and authentication token
		final Map<String, String> headerContentProtected = header == null
			? Collections.emptyMap()
			: header.getContent(username, CHAR_ARRAY_MASK, MASK, hostname);

		// Get the body to send
		final Body body = httpRequest.getBody();

		// This will get the body content as map by updating all the known macros
		final String bodyContent = body == null
			? EMPTY
			: body.getContent(username, password, authenticationToken, hostname);

		// This will get the body content as map by updating all the known macros
		// except sensitive data such as password and authentication token
		final String bodyContentProtected = body == null
			? EMPTY
			: body.getContent(username, CHAR_ARRAY_MASK, MASK, hostname);

		// Set the protocol http or https
		final String protocol = Boolean.TRUE.equals(httpConfiguration.getHttps()) ? "https" : "http";

		// Get the HTTP request URL
		final String httpRequestUrl = httpRequest.getUrl();

		// Get the HTTP request Path
		final String httpRequestPath = httpRequest.getPath();

		// Update the known HTTP macros, and return empty if the httpRequestPath is null
		final String path = MacrosUpdater.update(httpRequestPath, username, password, authenticationToken, hostname, false);

		// Update the known HTTP macros, and return empty if the httpRequestUrl is null
		final String url = MacrosUpdater.update(httpRequestUrl, username, password, authenticationToken, hostname, false);

		// Build the full URL
		final String fullUrl = UrlHelper.format(protocol, hostname, httpConfiguration.getPort(), path, url);

		LoggingHelper.trace(() ->
			log.trace(
				"Executing HTTP request: {} {}\n- hostname: {}\n- url: {}\n- path: {}\n" + // NOSONAR
				"- Protocol: {}\n- Port: {}\n" +
				"- Request-headers:\n{}\n- Request-body:\n{}\n- Timeout: {} s\n- Get-result-content: {}\n",
				method,
				fullUrl,
				hostname,
				url,
				path,
				protocol,
				httpConfiguration.getPort(),
				StringHelper.prettyHttpHeaders(headerContentProtected),
				bodyContentProtected,
				httpConfiguration.getTimeout().intValue(),
				httpRequest.getResultContent()
			)
		);

		return RetryOperation
			.<String>builder()
			.withDescription(String.format("%s %s", method, fullUrl))
			.withWaitStrategy((int) telemetryManager.getHostConfiguration().getRetryDelay())
			.withMaxRetries(1)
			.withHostname(hostname)
			.withDefaultValue(EMPTY)
			.build()
			.run(() ->
				doHttpRequest(
					httpRequest.getResultContent(),
					logMode,
					httpConfiguration,
					hostname,
					method,
					username,
					password,
					headerContent,
					headerContentProtected,
					bodyContent,
					bodyContentProtected,
					url,
					path,
					protocol,
					fullUrl
				)
			);
	}

	/**
	 * Execute the HTTP request
	 *
	 * @param resultContent          Which result should be returned. E.g. HTTP status, body, header or all
	 * @param logMode                Whether or not logging is enabled
	 * @param httpConfiguration      HTTP protocol configuration
	 * @param hostname               The device hostname
	 * @param method                 The HTTP method: GET, POST, DELETE, ...etc.
	 * @param username               The HTTP server username
	 * @param password               The HTTP server password
	 * @param headerContent          The HTTP header
	 * @param headerContentProtected The HTTP header without sensitive information
	 * @param bodyContent            The HTTP body
	 * @param bodyContentProtected   The HTTP body without sensitive information
	 * @param url                    The HTTP URL
	 * @param path                    The HTTP URL path
	 * @param protocol               The protocol: http or https
	 * @param fullUrl                The full HTTP URL. E.g. <pre>http://www.example.com:1080/api/v1/examples</pre>
	 * @return String value
	 */
	private String doHttpRequest(
		final ResultContent resultContent,
		final boolean logMode,
		final HttpConfiguration httpConfiguration,
		final String hostname,
		final String method,
		final String username,
		final char[] password,
		final Map<String, String> headerContent,
		final Map<String, String> headerContentProtected,
		final String bodyContent,
		final String bodyContentProtected,
		final String url,
		final String path,
		final String protocol,
		final String fullUrl
	) {
		try {
			final long startTime = System.currentTimeMillis();

			// Sending the request
			HttpResponse httpResponse = sendHttpRequest(
				fullUrl,
				method,
				username,
				password,
				headerContent,
				bodyContent,
				httpConfiguration.getTimeout().intValue()
			);

			// Compute the response time
			final long responseTime = System.currentTimeMillis() - startTime;

			// The request returned an error
			final int statusCode = httpResponse.getStatusCode();
			if (statusCode >= HTTP_BAD_REQUEST) {
				log.warn("Hostname {} - Bad response for HTTP request {} {}: {}.", hostname, method, fullUrl, statusCode);

				// Retry the request when receiving the following HTTP statuses
				// 500 Internal Server Error
				// 503 Service Unavailable
				// 504 Gateway Timeout
				// 507 Insufficient Storage
				if (statusCode == 500 || statusCode == 503 || statusCode == 504 || statusCode == 507) {
					throw new RetryableException();
				}

				return "";
			}

			// The request has been successful
			String result;
			switch (resultContent) {
				case BODY:
					result = httpResponse.getBody();
					break;
				case HEADER:
					result = httpResponse.getHeader();
					break;
				case HTTP_STATUS:
					result = String.valueOf(statusCode);
					break;
				case ALL:
					result = httpResponse.toString();
					break;
				case ALL_WITH_STATUS:
					result = "Status: %s\n%s".formatted(statusCode, httpResponse.toString()); // NOSONAR
					break;
				default:
					throw new IllegalArgumentException("Unsupported ResultContent: " + resultContent);
			}

			LoggingHelper.trace(() ->
				log.trace(
					"Executed HTTP request: {} {}\n- Hostname: {}\n- Url: {}\n- Path: {}\n- Protocol: {}\n- Port: {}\n" + // NOSONAR
					"- Request-headers:\n{}\n- Request-body:\n{}\n- Timeout: {} s\n" +
					"- get-result-content: {}\n- response-status: {}\n- response-headers:\n{}\n" +
					"- response-body:\n{}\n- response-time: {}\n",
					method,
					fullUrl,
					hostname,
					url,
					path,
					protocol,
					httpConfiguration.getPort(),
					StringHelper.prettyHttpHeaders(headerContentProtected),
					bodyContentProtected,
					httpConfiguration.getTimeout().intValue(),
					resultContent,
					statusCode,
					httpResponse.getHeader(),
					httpResponse.getBody(),
					responseTime
				)
			);

			return result;
		} catch (IOException e) {
			if (logMode) {
				log.error(
					"Hostname {} - Error detected when running HTTP request {} {}: {}\nReturning null.",
					hostname,
					method,
					fullUrl,
					e.getMessage()
				);

				log.debug("Hostname {} - Exception detected when running HTTP request {} {}:", hostname, method, fullUrl, e);
			}
		}

		return null;
	}

	/**
	 * @param url           The full URL of the HTTP request.
	 * @param method        The HTTP method (GET, POST, ...).
	 * @param username      The username for the connexion.
	 * @param password      The password for the connexion.
	 * @param headerContent The {@link Map} of properties-values in the header.
	 * @param bodyContent   The body as a plain text.
	 * @param timeout       The timeout of the request.
	 * @return The {@link HttpResponse} returned by the server.
	 * @throws IOException If a reading or writing operation fails.
	 */
	private HttpResponse sendHttpRequest(
		String url,
		String method,
		String username,
		char[] password,
		Map<String, String> headerContent,
		String bodyContent,
		int timeout
	) throws IOException {
		return HttpClient.sendRequest(
			url,
			method,
			null,
			username,
			password,
			null,
			0,
			null,
			null,
			null,
			headerContent,
			bodyContent,
			timeout,
			null
		);
	}
}
