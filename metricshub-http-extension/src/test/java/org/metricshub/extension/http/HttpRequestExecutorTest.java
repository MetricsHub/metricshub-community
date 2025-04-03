package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

class HttpRequestExecutorTest {

	@Test
	void testExecuteHttpGetBody() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
			final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
			final String bodyContent = "{}";
			final int timeout = 120;
			final HttpResponse httpResponse = new HttpResponse();
			httpResponse.setStatusCode(200);
			final String expecteBody = "{ \"key\": \"value\" }";
			httpResponse.appendBody(expecteBody);
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenReturn(httpResponse);

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.BODY)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertEquals(expecteBody, result);
		}
	}

	@Test
	void testExecuteHttpGetHeader() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
			final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
			final String bodyContent = "{}";
			final int timeout = 120;
			final HttpResponse httpResponse = new HttpResponse();
			httpResponse.setStatusCode(200);
			httpResponse.appendHeader("token", "value");
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenReturn(httpResponse);

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.HEADER)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertEquals("token: value\n", result);
		}
	}

	@Test
	void testExecuteHttpGetStatus() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
			final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
			final String bodyContent = "{}";
			final int timeout = 120;
			final HttpResponse httpResponse = new HttpResponse();
			httpResponse.setStatusCode(200);
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenReturn(httpResponse);

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.HTTP_STATUS)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertEquals("200", result);
		}
	}

	@Test
	void testExecuteHttpGetAllContent() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
			final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
			final String bodyContent = "{}";
			final int timeout = 120;
			final HttpResponse httpResponse = new HttpResponse();
			httpResponse.setStatusCode(200);
			httpResponse.appendBody("body");
			httpResponse.appendHeader("key", "value");
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenReturn(httpResponse);

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.ALL)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertEquals("key: value\n\nbody", result);
		}
	}

	@Test
	void testExecuteHttpThrowsException() {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
			final Map<String, String> headerContent = Map.of("Connection", "keep-alive");
			final String bodyContent = "{}";
			final int timeout = 120;
			final HttpResponse httpResponse = new HttpResponse();
			httpResponse.setStatusCode(200);
			httpResponse.appendBody("body");
			httpResponse.appendHeader("key", "value");
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenThrow(new IOException());

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.ALL)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertNull(result);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 500, 503, 504, 507 })
	void testExecuteHttpRetry(final int httpStatusCode) {
		try (MockedStatic<HttpClient> httpClientMock = mockStatic(HttpClient.class)) {
			final String path = "/";
			final String method = "GET";
			final String username = "username";
			final char[] password = "pwd".toCharArray();
			final int proxyPort = 0;
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
			httpClientMock
				.when(() ->
					HttpClient.sendRequest(
						"https://hostname:443/",
						method,
						null,
						username,
						password,
						null,
						proxyPort,
						null,
						null,
						null,
						headerContent,
						bodyContent,
						timeout,
						null
					)
				)
				.thenReturn(httpResponse1)
				.thenReturn(httpResponse2);

			final String hostname = "hostname";
			final HttpConfiguration httpConfiguration = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(timeout * 1L)
				.build();
			final HttpRequest request = HttpRequest
				.builder()
				.path(path)
				.method(method)
				.body(bodyContent, Map.of(), "connector", hostname)
				.hostname(hostname)
				.httpConfiguration(httpConfiguration)
				.header("Connection: keep-alive", Map.of(), "connector", hostname)
				.resultContent(ResultContent.BODY)
				.build();
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(hostname)
						.hostId(hostname)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
						.retryDelay(0)
						.build()
				)
				.build();
			final String result = new HttpRequestExecutor().executeHttp(request, true, telemetryManager);
			assertEquals(expecteBody, result);
		}
	}
}
