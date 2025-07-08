package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.mockito.MockedStatic;

class HttpToolTest {

	private final HttpTool httpTool = new HttpTool();

	@Test
	void testShouldReturnBodyWhenResultContentIsBody() throws Exception {
		final HttpResponse mockResponse = new HttpResponse();
		mockResponse.setStatusCode(200);
		mockResponse.appendBody("test-body");

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://example.com",
						"POST",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						"{}",
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(mockResponse);

			final String result = httpTool.execute(
				Map.of("url", "https://example.com", "method", "POST", "body", "{}", "resultContent", "BODY")
			);

			assertEquals("test-body", result, "Expected body content not returned");
		}
	}

	@Test
	void testShouldReturnHeaderWhenResultContentIsHeader() throws Exception {
		final HttpResponse mockResponse = new HttpResponse();
		mockResponse.setStatusCode(200);
		mockResponse.appendHeader("x-token", "abc123");

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://example.com",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(mockResponse);

			final String result = httpTool.execute(Map.of("url", "https://example.com", "resultContent", "HEADER"));

			assertEquals("x-token: abc123\n", result, "Expected header content not returned");
		}
	}

	@Test
	void testShouldReturnHttpStatusCodeWhenResultContentIsHttpStatus() throws Exception {
		final HttpResponse mockResponse = new HttpResponse();
		mockResponse.setStatusCode(201);

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://example.com",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(mockResponse);

			final String result = httpTool.execute(Map.of("url", "https://example.com", "resultContent", "HTTP_STATUS"));

			assertEquals("201", result, "Expected HTTP status code not returned");
		}
	}

	@Test
	void testShouldThrowClientExceptionOnBadStatusCode() {
		final HttpResponse mockResponse = new HttpResponse();
		mockResponse.setStatusCode(404);

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://fail.com",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(mockResponse);

			final ClientException ex = assertThrows(
				ClientException.class,
				() -> httpTool.execute(Map.of("url", "https://fail.com")),
				"Expected ClientException for bad status code"
			);

			assertTrue(ex.getMessage().contains("HTTP request"), "Expected error message not found");
		}
	}

	@Test
	void testShouldParseValidHeaders() throws Exception {
		final HttpResponse response = new HttpResponse();
		response.setStatusCode(200);
		response.appendBody("ok");

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://headers.com",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of("Accept", "application/json", "X-Test", "yes"),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(response);

			final String result = httpTool.execute(
				Map.of("url", "https://headers.com", "hearders", "Accept: application/json\nX-Test: yes")
			);

			assertEquals("ok", result, "Expected response body not returned");
		}
	}

	@Test
	void testShouldUseDefaultValuesWhenNotProvided() throws Exception {
		final HttpResponse response = new HttpResponse();
		response.setStatusCode(200);
		response.appendBody("default-ok");

		try (MockedStatic<HttpClient> mock = mockStatic(HttpClient.class)) {
			mock
				.when(() ->
					HttpClient.sendRequest(
						"https://default.com",
						"GET",
						null,
						null,
						null,
						null,
						0,
						null,
						null,
						null,
						Map.of(),
						null,
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(response);

			final String result = httpTool.execute(Map.of("url", "https://default.com"));

			assertEquals("default-ok", result, "Expected default response body not returned");
		}
	}

	@Test
	void testShouldThrowOnInvalidHeaderFormat() {
		final IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> httpTool.execute(Map.of("url", "https://badheaders.com", "hearders", "InvalidHeaderWithoutColon")),
			"Expected IllegalArgumentException for invalid header format"
		);

		assertTrue(
			ex.getMessage().contains("Invalid header format"),
			"Expected invalid header format exception not thrown"
		);
	}
}
