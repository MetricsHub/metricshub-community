package org.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.springframework.util.Assert.notNull;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.RetryableException;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.MacrosUpdater;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.extension.HttpResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.RetryOperation;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.Body;
import org.metricshub.extension.http.utils.Header;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.extension.http.utils.UrlHelper;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.slf4j.event.Level;

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
	 * The {@code operation_type} of the HTTP and HTTPS requests in {@code metricshub.host.requests}.
	 */
	public static final String OPERATION_TYPE = HttpExtension.IDENTIFIER;

	/**
	 * The lowest HTTP status code (100 Continue). A lower status means that the answer was not HTTP.
	 */
	private static final int MIN_STATUS_CODE = 100;

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
		final ResultContent resultContent = httpRequest.getResultContent();
		return toText(
			execute(httpRequest, logMode, telemetryManager, resultContent, new AtomicReference<>()),
			resultContent
		);
	}

	/**
	 * Executes the given HTTP request and gives the response itself: its status code, its header and its body,
	 * whatever the result content of the request.
	 *
	 * @param httpRequest      The {@link HttpRequest} values.
	 * @param logMode          Whether or not logging is enabled.
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @return The response, <code>null</code> when the request got none.
	 */
	@WithSpan("HTTP")
	public HttpResult executeHttpRequest(
		@SpanAttribute("http.config") @NonNull HttpRequest httpRequest,
		final boolean logMode,
		final TelemetryManager telemetryManager
	) {
		// Recorded whole, with its status
		return execute(httpRequest, logMode, telemetryManager, ResultContent.ALL_WITH_STATUS, new AtomicReference<>());
	}

	/**
	 * Executes the request of an <code>http</code> source: the text of the part of the response its result content
	 * names, or a failure when no response came or when the server answered with an error status (400 and above),
	 * except for who asks for the status.
	 *
	 * @param httpRequest      The {@link HttpRequest} values.
	 * @param logMode          Whether or not logging is enabled.
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @return The text, or a failure.
	 */
	@WithSpan("HTTP")
	public SourceTable executeHttpSource(
		@SpanAttribute("http.config") @NonNull HttpRequest httpRequest,
		final boolean logMode,
		final TelemetryManager telemetryManager
	) {
		final ResultContent resultContent = httpRequest.getResultContent();
		final AtomicReference<IOException> error = new AtomicReference<>();
		final HttpResult response = execute(httpRequest, logMode, telemetryManager, resultContent, error);
		if (response == null) {
			// Why: a refused connection, a timeout, an unknown host, a TLS error
			return error.get() != null ? SourceTable.failed("No response", error.get()) : SourceTable.failed("No response.");
		}
		if (isRefused(response, resultContent)) {
			return SourceTable.failed("The server answered with the status " + response.statusCode() + ".");
		}
		return SourceTable.ofText(response.toText(resultContent));
	}

	/**
	 * Whether the server refused the request (400 and above) and the caller does not ask for the status.
	 *
	 * @param response      The response
	 * @param resultContent Which part of the response to return
	 * @return {@code true} for an error answer
	 */
	private static boolean isRefused(final HttpResult response, final ResultContent resultContent) {
		return response.statusCode() >= HTTP_BAD_REQUEST && resultContent != ResultContent.ALL_WITH_STATUS;
	}

	/**
	 * The text an HTTP request gives: the part of the response its result content names. The response to a refused
	 * request (400 and above) is an empty text, except for who asks for the status.
	 *
	 * @param response      The response, <code>null</code> when the request got none
	 * @param resultContent Which part of the response to return
	 * @return The text, <code>null</code> without a response
	 */
	static String toText(final HttpResult response, final ResultContent resultContent) {
		if (response == null) {
			return null;
		}
		return isRefused(response, resultContent) ? EMPTY : response.toText(resultContent);
	}

	/**
	 * Executes the given HTTP request, retries it when the server asks to, and records the exchange.
	 *
	 * @param httpRequest      The {@link HttpRequest} values.
	 * @param logMode          Whether or not logging is enabled.
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @param resultContent    The part of the response that is recorded.
	 * @param error            Receives the exception of a request that got no response.
	 * @return The response, <code>null</code> when the request got none.
	 */
	private HttpResult execute(
		final HttpRequest httpRequest,
		final boolean logMode,
		final TelemetryManager telemetryManager,
		final ResultContent resultContent,
		final AtomicReference<IOException> error
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

		LoggingHelper.logWithHiddenPassword(
			Level.TRACE,
			() -> String.format(
				"Executing HTTP request: %s %s\n- hostname: %s\n- url: %s\n- path: %s\n" + // NOSONAR
					"- Protocol: %s\n- Port: %d\n" +
					"- Request-headers:\n%s\n- Request-body:\n%s\n- Timeout: %d s\n- Get-result-content: %s\n",
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

		// What the server answered when it asked for a retry
		final AtomicReference<HttpResult> retriedResponse = new AtomicReference<>();

		final HttpResult retried = RetryOperation.<HttpResult>builder()
			.withDescription(String.format("%s %s", method, fullUrl))
			.withWaitStrategy((int) telemetryManager.getHostConfiguration().getRetryDelay())
			.withMaxRetries(1)
			.withHostname(hostname)
			.build()
			.run(
				() -> doHttpRequest(
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
					fullUrl,
					retriedResponse,
					error,
					telemetryManager.getRequestStats()
				)
			);

		// The retries are exhausted and the server still answers with an error: that answer is the response
		final HttpResult response = retried != null ? retried : retriedResponse.get();
		final String result = toText(response, resultContent);

		// Record the HTTP exchange if recording is enabled. A refused request is not recorded: its replay finds no
		// response, and fails as the request did
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (
			recordOutputDirectory != null &&
				!recordOutputDirectory.isBlank() &&
				result != null &&
				!isRefused(response, resultContent)
		) {
			// Resolve body and headers using default credentials so that recorded values
			// match what the emulation extension will compute during playback
			final String defaultAuthToken = MacrosUpdater.update(
				httpRequestAuthToken,
				HttpRecorder.DEFAULT_USERNAME,
				HttpRecorder.DEFAULT_PASSWORD,
				httpRequestAuthToken,
				hostname,
				false
			);
			final Map<String, String> recordHeaders = header == null
				? Collections.emptyMap()
				: header.getContent(HttpRecorder.DEFAULT_USERNAME, HttpRecorder.DEFAULT_PASSWORD, defaultAuthToken, hostname);
			final String recordBody = body == null
				? EMPTY
				: body.getContent(HttpRecorder.DEFAULT_USERNAME, HttpRecorder.DEFAULT_PASSWORD, defaultAuthToken, hostname);

			HttpRecorder.getInstance(recordOutputDirectory)
				.record(method, httpRequestPath, httpRequestUrl, recordBody, recordHeaders, resultContent, result);
		}

		return response;
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
	 * @param retriedResponse        Receives the response of a request that is retried
	 * @param error                  Receives the exception of an attempt that got no response
	 * @param requestStats           The request counters of the monitored resource, which receive the outcome of the
	 *                               attempt
	 * @return The response, <code>null</code> when the request got none
	 */
	private HttpResult doHttpRequest(
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
		final String fullUrl,
		final AtomicReference<HttpResult> retriedResponse,
		final AtomicReference<IOException> error,
		final RequestStats requestStats
	) {
		// The response of a previous attempt is not the response of this one
		retriedResponse.set(null);
		error.set(null);
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
				httpConfiguration.getTimeout().intValue(),
				requestStats
			);

			// Compute the response time
			final long responseTime = System.currentTimeMillis() - startTime;

			final int statusCode = httpResponse.getStatusCode();
			final HttpResult result = new HttpResult(statusCode, httpResponse.getHeader(), httpResponse.getBody());

			// The request returned an error
			if (statusCode >= HTTP_BAD_REQUEST) {
				log.warn("Hostname {} - Bad response for HTTP request {} {}: {}.", hostname, method, fullUrl, statusCode);

				// Retry the request when receiving the following HTTP statuses
				// 500 Internal Server Error
				// 503 Service Unavailable
				// 504 Gateway Timeout
				// 507 Insufficient Storage
				if (statusCode == 500 || statusCode == 503 || statusCode == 504 || statusCode == 507) {
					retriedResponse.set(result);
					throw new RetryableException();
				}
			}

			LoggingHelper.logWithHiddenPassword(
				Level.TRACE,
				() -> String.format(
					"Executed HTTP request: %s %s\n- Hostname: %s\n- Url: %s\n- Path: %s\n- Protocol: %s\n- Port: %d\n" + // NOSONAR
						"- Request-headers:\n%s\n- Request-body:\n%s\n- Timeout: %d s\n" +
						"- get-result-content: %s\n- response-status: %d\n- response-headers:\n%s\n" +
						"- response-body:\n%s\n- response-time: %d%n\n",
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
			error.set(e);
			if (e instanceof InterruptedIOException && Thread.currentThread().isInterrupted()) {
				// The job was cut short: an expected end, not an error
				log.debug("Hostname {} - HTTP request {} {} interrupted. Returning null.", hostname, method, fullUrl);
			} else if (logMode) {
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
	 * @param username      The username for the connection.
	 * @param password      The password for the connection.
	 * @param headerContent The {@link Map} of properties-values in the header.
	 * @param bodyContent   The body as a plain text.
	 * @param timeout       The timeout of the request.
	 * @param requestStats  The request counters of the monitored resource, which receive the outcome of the attempt.
	 * @return The {@link HttpResponse} returned by the server.
	 * @throws IOException If a reading or writing operation fails, an {@link InterruptedIOException} when the caller
	 *                     is interrupted.
	 */
	private HttpResponse sendHttpRequest(
		String url,
		String method,
		String username,
		char[] password,
		Map<String, String> headerContent,
		String bodyContent,
		int timeout,
		final RequestStats requestStats
	) throws IOException {
		// A thread already interrupted (an expired deadline of its job) sends nothing and counts nothing:
		// HttpURLConnection connects and sends without looking at the interrupt
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedIOException("Interrupted before the HTTP request was sent");
		}
		final HttpResponse httpResponse;
		try {
			httpResponse = sendInterruptibly(url, method, username, password, headerContent, bodyContent, timeout);
		} catch (MalformedURLException e) {
			// The URL is invalid: nothing was sent
			throw e;
		} catch (IOException e) {
			// An interrupt that came before the request started sent nothing
			if (!ThreadHelper.neverStarted(e)) {
				requestStats.record(OPERATION_TYPE, RequestOutcome.of(e));
			}
			throw e;
		} catch (RuntimeException e) {
			requestStats.record(OPERATION_TYPE, RequestOutcome.FAILED);
			throw e;
		}
		requestStats.record(OPERATION_TYPE, outcomeOf(httpResponse.getStatusCode()));
		return httpResponse;
	}

	/**
	 * Sends the request on its own thread, which the caller stops waiting for when it is interrupted: the request
	 * may still run there, but its response is dropped.
	 *
	 * @param url           The full URL of the HTTP request.
	 * @param method        The HTTP method (GET, POST, ...).
	 * @param username      The username for the connection.
	 * @param password      The password for the connection.
	 * @param headerContent The {@link Map} of properties-values in the header.
	 * @param bodyContent   The body as a plain text.
	 * @param timeout       The timeout of the request.
	 * @return The {@link HttpResponse} returned by the server.
	 * @throws IOException If a reading or writing operation fails, an {@link InterruptedIOException} when the caller
	 *                     is interrupted (its interrupt status is kept).
	 */
	private HttpResponse sendInterruptibly(
		final String url,
		final String method,
		final String username,
		final char[] password,
		final Map<String, String> headerContent,
		final String bodyContent,
		final int timeout
	) throws IOException {
		try {
			return ThreadHelper.executeInterruptibly(
				() -> send(url, method, username, password, headerContent, bodyContent, timeout)
			);
		} catch (InterruptedException e) {
			final InterruptedIOException interrupted = new InterruptedIOException("HTTP request interrupted");
			interrupted.initCause(e);
			throw interrupted;
		} catch (ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof IOException ioException) {
				throw ioException;
			}
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IOException(cause);
		}
	}

	/**
	 * Sends the request with {@link HttpClient}. Test seam.
	 *
	 * @param url           The full URL of the HTTP request.
	 * @param method        The HTTP method (GET, POST, ...).
	 * @param username      The username for the connection.
	 * @param password      The password for the connection.
	 * @param headerContent The {@link Map} of properties-values in the header.
	 * @param bodyContent   The body as a plain text.
	 * @param timeout       The timeout of the request.
	 * @return The {@link HttpResponse} returned by the server.
	 * @throws IOException If a reading or writing operation fails.
	 */
	HttpResponse send(
		final String url,
		final String method,
		final String username,
		final char[] password,
		final Map<String, String> headerContent,
		final String bodyContent,
		final int timeout
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

	/**
	 * The outcome of a request the server answered: completed for an informational, successful or redirection status
	 * (100 to 399), failed for an error status (400 and above) or an answer that is not HTTP (below 100, e.g. -1).
	 *
	 * @param statusCode The status of the response.
	 * @return The outcome of the request.
	 */
	private static RequestOutcome outcomeOf(final int statusCode) {
		return statusCode >= MIN_STATUS_CODE && statusCode < HTTP_BAD_REQUEST
			? RequestOutcome.COMPLETED
			: RequestOutcome.FAILED;
	}
}
