package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.extension.HttpResult;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.mockito.MockedStatic;
import org.mockito.stubbing.OngoingStubbing;

/**
 * Each HTTP request attempt sent for a resource counts one outcome in the request counters of its
 * {@link TelemetryManager}.
 */
class HttpRequestStatsTest {

	private static final String RESOURCE_HOSTNAME = "resource-host";

	/**
	 * Stubs any call to {@link HttpClient#sendRequest}.
	 *
	 * @param httpClientMock The static mock of {@link HttpClient}
	 * @return The stubbing
	 */
	private static OngoingStubbing<HttpResponse> whenSend(final MockedStatic<HttpClient> httpClientMock) {
		return httpClientMock.when(
			() -> HttpClient.sendRequest(
				anyString(),
				anyString(),
				any(),
				any(),
				any(),
				any(),
				anyInt(),
				any(),
				any(),
				any(),
				any(),
				any(),
				anyInt(),
				any()
			)
		);
	}

	/**
	 * Verifies how many times {@link HttpClient#sendRequest} was called.
	 *
	 * @param httpClientMock The static mock of {@link HttpClient}
	 * @param count          The expected number of calls
	 */
	private static void verifySent(final MockedStatic<HttpClient> httpClientMock, final int count) {
		httpClientMock.verify(
			() -> HttpClient.sendRequest(
				anyString(),
				anyString(),
				any(),
				any(),
				any(),
				any(),
				anyInt(),
				any(),
				any(),
				any(),
				any(),
				any(),
				anyInt(),
				any()
			),
			times(count)
		);
	}

	/**
	 * Builds a response.
	 *
	 * @param statusCode The status
	 * @param body       The body, <code>null</code> for none
	 * @return The response
	 */
	private static HttpResponse response(final int statusCode, final String body) {
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(statusCode);
		if (body != null) {
			httpResponse.appendBody(body);
		}
		return httpResponse;
	}

	/**
	 * Builds the telemetry manager of the resource.
	 *
	 * @param httpConfiguration The HTTP configuration of the resource
	 * @return The telemetry manager
	 */
	private static TelemetryManager resource(final HttpConfiguration httpConfiguration) {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(RESOURCE_HOSTNAME)
					.hostId(RESOURCE_HOSTNAME)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.retryDelay(0)
					.build()
			)
			.build();
	}

	/**
	 * Builds a GET / request on the resource.
	 *
	 * @param httpConfiguration The HTTP configuration of the resource
	 * @return The request
	 */
	private static HttpRequest request(final HttpConfiguration httpConfiguration) {
		return HttpRequest.builder()
			.hostname(RESOURCE_HOSTNAME)
			.path("/")
			.method("GET")
			.httpConfiguration(httpConfiguration)
			.resultContent(ResultContent.BODY)
			.build();
	}

	/**
	 * Asserts the HTTP counters of the resource.
	 *
	 * @param telemetryManager The telemetry manager of the resource
	 * @param completed        Expected completed attempts
	 * @param timeout          Expected attempts that timed out
	 * @param failed           Expected failed attempts
	 */
	private static void assertCounts(
		final TelemetryManager telemetryManager,
		final long completed,
		final long timeout,
		final long failed
	) {
		assertEquals(
			Map.of(
				HttpRequestExecutor.OPERATION_TYPE,
				Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
			),
			telemetryManager.getRequestStats().snapshot()
		);
	}

	@ParameterizedTest
	@CsvSource(
		{
			"200, body, 1, 0",
			"200, , 1, 0",
			"204, , 1, 0",
			"302, , 1, 0",
			"304, , 1, 0",
			"-1, , 0, 1",
			"99, , 0, 1",
			"400, error, 0, 1",
			"401, , 0, 1",
			"404, error, 0, 1" }
	)
	void testStatusOutcome(final int statusCode, final String body, final long completed, final long failed) {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(statusCode, body));
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);

			final HttpResult result = new HttpRequestExecutor().executeHttpRequest(
				request(httpConfiguration),
				true,
				telemetryManager
			);

			// The response is returned unchanged
			assertEquals(statusCode, result.statusCode());
			assertEquals(body == null ? "" : body, result.body());
			verifySent(httpClientMock, 1);
			assertCounts(telemetryManager, completed, 0, failed);
		}
	}

	@Test
	void testRetriedErrorCountsEachAttempt() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(500, "first")).thenReturn(response(500, "second"));
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);

			final HttpResult result = new HttpRequestExecutor().executeHttpRequest(
				request(httpConfiguration),
				true,
				telemetryManager
			);

			// The retry still happens and the last error is the response
			assertEquals(500, result.statusCode());
			assertEquals("second", result.body());
			verifySent(httpClientMock, 2);
			assertCounts(telemetryManager, 0, 0, 2);
		}
	}

	@Test
	void testRetriedThenAnsweredCountsBoth() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(503, "busy")).thenReturn(response(200, "ok"));
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);

			assertEquals("ok", new HttpRequestExecutor().executeHttp(request(httpConfiguration), true, telemetryManager));
			verifySent(httpClientMock, 2);
			assertCounts(telemetryManager, 1, 0, 1);
		}
	}

	@Test
	void testSocketTimeoutCountsTimeout() {
		assertIOExceptionOutcome(new SocketTimeoutException("Read timed out"), 0, 1, 0);
	}

	@Test
	void testWrappedSocketTimeoutCountsTimeout() {
		assertIOExceptionOutcome(new IOException("wrapped", new SocketTimeoutException("Connect timed out")), 0, 1, 0);
	}

	@Test
	void testConnectExceptionCountsFailed() {
		assertIOExceptionOutcome(new ConnectException("Connection refused"), 0, 0, 1);
	}

	@Test
	void testUnknownHostCountsFailed() {
		assertIOExceptionOutcome(new UnknownHostException(RESOURCE_HOSTNAME), 0, 0, 1);
	}

	@Test
	void testMalformedUrlCountsNothing() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenThrow(new MalformedURLException("no protocol"));
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);

			assertNull(new HttpRequestExecutor().executeHttp(request(httpConfiguration), true, telemetryManager));
			assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
		}
	}

	/**
	 * Asserts the outcome of a request whose client throws the given {@link IOException}: the executor still gives
	 * <code>null</code> and does not retry.
	 *
	 * @param exception The exception thrown by the client
	 * @param completed Expected completed attempts
	 * @param timeout   Expected attempts that timed out
	 * @param failed    Expected failed attempts
	 */
	private static void assertIOExceptionOutcome(
		final IOException exception,
		final long completed,
		final long timeout,
		final long failed
	) {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenThrow(exception);
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);

			assertNull(new HttpRequestExecutor().executeHttp(request(httpConfiguration), true, telemetryManager));
			verifySent(httpClientMock, 1);
			assertCounts(telemetryManager, completed, timeout, failed);
		}
	}

	@Test
	void testRuntimeExceptionCountsFailedAndPropagates() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final IllegalArgumentException exception = new IllegalArgumentException("bad charset");
			whenSend(httpClientMock).thenThrow(exception);
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);
			final HttpRequestExecutor executor = new HttpRequestExecutor();
			final HttpRequest request = request(httpConfiguration);

			assertSame(
				exception,
				assertThrows(IllegalArgumentException.class, () -> executor.executeHttp(request, true, telemetryManager))
			);
			assertCounts(telemetryManager, 0, 0, 1);
		}
	}

	@Test
	void testHealthCheckErrorStatusIsUpAndCountsFailed() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(401, "unauthorized"));
			final TelemetryManager telemetryManager = resource(HttpConfiguration.builder().build());

			assertEquals(Optional.of(true), new HttpExtension().checkProtocol(telemetryManager));
			assertCounts(telemetryManager, 0, 0, 1);
		}
	}

	@Test
	void testHostnameOverrideCountsOnResource() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(200, "ok"));
			final TelemetryManager telemetryManager = resource(HttpConfiguration.builder().hostname("other-host").build());

			assertEquals(Optional.of(true), new HttpExtension().checkProtocol(telemetryManager));

			// The request goes to the configured hostname, and counts on the resource
			httpClientMock.verify(
				() -> HttpClient.sendRequest(
					startsWith("https://other-host:"),
					anyString(),
					any(),
					any(),
					any(),
					any(),
					anyInt(),
					any(),
					any(),
					any(),
					any(),
					any(),
					anyInt(),
					any()
				)
			);
			assertCounts(telemetryManager, 1, 0, 0);
		}
	}

	@Test
	void testExecuteQueryDoesNotCountOnResource() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			whenSend(httpClientMock).thenReturn(response(200, "ok"));
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder().hostname(RESOURCE_HOSTNAME).build();
			final TelemetryManager telemetryManager = resource(httpConfiguration);
			final ObjectNode query = JsonNodeFactory.instance.objectNode();
			query.set("method", new TextNode("GET"));
			query.set("path", new TextNode("/"));

			assertTrue(new HttpExtension().executeQuery(httpConfiguration, query).contains("ok"));
			verifySent(httpClientMock, 1);
			assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
		}
	}
}
