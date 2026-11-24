package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.extension.HttpResult;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.http.HttpResponse;

class HttpRequestExecutorTest {

	@AfterEach
	void tearDown() {
		HttpRecorder.clearInstances();
	}

	@Test
	void testExecuteHttpGetBody() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		final String expecteBody = "{ \"key\": \"value\" }";
		httpResponse.appendBody(expecteBody);
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.BODY)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals(expecteBody, result);
	}

	/**
	 * A request that the server refuses gives nothing, except to whoever asked for the status: the status and what
	 * the server answered.
	 */
	@Test
	void testExecuteHttpErrorStatus() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(401);
		httpResponse.appendHeader("Content-Type", "application/json");
		httpResponse.appendBody("{\"error\":\"bad token\"}");
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, "{}", timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		// 401: refused at once. 503: retried, and refused again
		for (final int status : new int[] { 401, 503 }) {
			httpResponse.setStatusCode(status);
			for (final ResultContent resultContent : ResultContent.values()) {
				final HttpRequest request = HttpRequest.builder()
					.path("/")
					.method(method)
					.body("{}", Map.of(), "connector", hostname)
					.hostname(hostname)
					.httpConfiguration(httpConfiguration)
					.header("Connection: keep-alive", Map.of(), "connector", hostname)
					.resultContent(resultContent)
					.build();
				final String result = executor.executeHttp(request, true, telemetryManager);
				if (resultContent == ResultContent.ALL_WITH_STATUS) {
					assertEquals("Status: " + status + "\n" + httpResponse.getHeader() + "\n{\"error\":\"bad token\"}", result);
					// Who asks for the status gets the answer
					assertEquals(SourceTable.ofText(result), executor.executeHttpSource(request, true, telemetryManager));
				} else {
					assertEquals("", result, status + " " + resultContent.name());
					// An http source fails
					assertEquals(
						SourceTable.failed("The server answered with the status " + status + "."),
						executor.executeHttpSource(request, true, telemetryManager)
					);
				}

				// The response itself has its status, whatever the result content
				assertEquals(
					new HttpResult(status, httpResponse.getHeader(), "{\"error\":\"bad token\"}"),
					executor.executeHttpRequest(request, true, telemetryManager)
				);
			}
		}
	}

	@Test
	void testExecuteHttpGetHeader() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		httpResponse.appendHeader("token", "value");
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.HEADER)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals("token: value\n", result);
	}

	@Test
	void testExecuteHttpGetStatus() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.HTTP_STATUS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals("200", result);
	}

	@Test
	void testExecuteHttpGetAllContent() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		httpResponse.appendBody("body");
		httpResponse.appendHeader("key", "value");
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.ALL)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals("key: value\n\nbody", result);
	}

	@Test
	void testExecuteHttpThrowsException() throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		httpResponse.appendBody("body");
		httpResponse.appendHeader("key", "value");
		doThrow(new IOException()).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.ALL)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertNull(result);
		// No response: an http source fails
		assertEquals(SourceTable.failed("No response."), executor.executeHttpSource(request, true, telemetryManager));
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 503, 504, 507 })
	void testExecuteHttpRetry(final int httpStatusCode) throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse1 = new HttpResponse();
		httpResponse1.setStatusCode(httpStatusCode);
		String expecteBody = "{ \"key\": \"error\" }";
		httpResponse1.appendBody(expecteBody);
		final HttpResponse httpResponse2 = new HttpResponse();
		httpResponse2.setStatusCode(200);
		expecteBody = "{ \"key\": \"success\" }";
		httpResponse2.appendBody(expecteBody);
		doReturn(httpResponse1, httpResponse2).when(executor)
			.send("https://hostname:443/", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.BODY)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.retryDelay(0)
					.build()
			)
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals(expecteBody, result);
	}

	@Test
	void testExecuteHttpRecordsWhenRecordOutputDirectorySet(@TempDir Path tempDir) throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final String path = "/api/test";
		final String method = "GET";
		final String username = "username";
		final char[] password = "pwd".toCharArray();
		final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
		final String bodyContent = "{}";
		final int timeout = 120;
		final HttpResponse httpResponse = new HttpResponse();
		httpResponse.setStatusCode(200);
		final String expectedBody = "recorded response";
		httpResponse.appendBody(expectedBody);
		doReturn(httpResponse).when(executor)
			.send("https://hostname:443/api/test", method, username, password, headerContent, bodyContent, timeout);

		final String hostname = "hostname";
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
			.username(username)
			.password(password)
			.timeout(timeout * 1L)
			.build();
		final HttpRequest request = HttpRequest.builder()
			.path(path)
			.method(method)
			.body(bodyContent, Map.of(), "connector", hostname)
			.hostname(hostname)
			.httpConfiguration(httpConfiguration)
			.header("Connection: keep-alive", Map.of(), "connector", hostname)
			.resultContent(ResultContent.BODY)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.build()
			)
			.recordOutputDirectory(tempDir.toString())
			.build();
		final String result = executor.executeHttp(request, true, telemetryManager);
		assertEquals(expectedBody, result);
		HttpRecorder.flushAndRemoveInstance(tempDir.toString());

		// Verify recording files were created
		final Path httpDir = tempDir.resolve("http");
		assertTrue(Files.isRegularFile(httpDir.resolve("image.yaml")));
		try (java.util.stream.Stream<Path> stream = Files.list(httpDir)) {
			assertEquals(1, stream.filter(p -> p.toString().endsWith(".txt")).count());
		}
	}

	/**
	 * Builds the telemetry manager of a resource that records its requests in the given directory.
	 *
	 * @param hostname          The hostname of the resource
	 * @param httpConfiguration The HTTP configuration of the resource
	 * @param recordDirectory   Where the requests are recorded
	 * @return The telemetry manager
	 */
	private static TelemetryManager recordingResource(
		final String hostname,
		final HttpConfiguration httpConfiguration,
		final Path recordDirectory
	) {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(hostname)
					.hostId(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
					.retryDelay(0)
					.build()
			)
			.recordOutputDirectory(recordDirectory.toString())
			.build();
	}

	/**
	 * A job cut short while its request waits for a server that never answers stops waiting at once: no response,
	 * no retry, no recording, one timeout counted, and its interrupt status kept. The request left behind records
	 * nothing when it ends.
	 */
	@Test
	void testInterruptedRequestStopsWaiting(@TempDir Path tempDir) throws Exception {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final List<ExecutorService> abandoned = new ArrayList<>();
		final TelemetryManager telemetryManager;
		final String result;
		final long elapsedMillis;
		final boolean interrupted;
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			// Accepts the connection and never answers
			final CompletableFuture<Socket> accepted = CompletableFuture.supplyAsync(() -> {
				try {
					return server.accept();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			final String hostname = server.getInetAddress().getHostAddress();
			final HttpConfiguration httpConfiguration = HttpConfiguration.builder()
				.https(false)
				.port(server.getLocalPort())
				.timeout(120L)
				.build();
			final HttpRequest request = HttpRequest.builder()
				.hostname(hostname)
				.path("/")
				.method("GET")
				.httpConfiguration(httpConfiguration)
				.resultContent(ResultContent.BODY)
				.build();
			telemetryManager = recordingResource(hostname, httpConfiguration, tempDir);

			final Thread caller = Thread.currentThread();
			final Thread interrupter = Thread.ofVirtual().start(() -> {
				try {
					Thread.sleep(200);
					caller.interrupt();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			final long start = System.nanoTime();
			result = ThreadHelper.trackingAbandonedRequests(
				abandoned,
				() -> executor.executeHttp(request, true, telemetryManager)
			);
			elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			interrupted = Thread.interrupted();
			interrupter.join();

			// The server closes the connection: the request left behind ends
			accepted.get(5, TimeUnit.SECONDS).close();
		}

		assertNull(result);
		assertTrue(interrupted, "The interrupt status is kept");
		assertTrue(elapsedMillis < 1200, "Returned after " + elapsedMillis + " ms");
		verify(executor, times(1)).send(anyString(), anyString(), any(), any(), any(), any(), anyInt());
		assertFalse(Files.exists(tempDir.resolve("http")), "Nothing is recorded");
		assertEquals(1, abandoned.size());
		assertTrue(abandoned.get(0).awaitTermination(5, TimeUnit.SECONDS));
		assertEquals(
			Map.of(
				HttpRequestExecutor.OPERATION_TYPE,
				Map.of(RequestOutcome.COMPLETED, 0L, RequestOutcome.TIMEOUT, 1L, RequestOutcome.FAILED, 0L)
			),
			telemetryManager.getRequestStats().snapshot()
		);
	}

	/**
	 * A job already cut short sends nothing and counts nothing.
	 */
	@Test
	void testInterruptedCallerSendsNothing(@TempDir Path tempDir) throws IOException {
		final HttpRequestExecutor executor = spy(new HttpRequestExecutor());
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
		final HttpRequest request = HttpRequest.builder()
			.hostname("hostname")
			.path("/")
			.method("GET")
			.httpConfiguration(httpConfiguration)
			.resultContent(ResultContent.BODY)
			.build();
		final TelemetryManager telemetryManager = recordingResource("hostname", httpConfiguration, tempDir);

		Thread.currentThread().interrupt();
		final String result;
		try {
			result = executor.executeHttp(request, true, telemetryManager);
		} finally {
			assertTrue(Thread.interrupted(), "The interrupt status is kept");
		}

		assertNull(result);
		verify(executor, never()).send(anyString(), anyString(), any(), any(), any(), any(), anyInt());
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}
}
