package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.http.HttpClient;
import org.metricshub.http.HttpResponse;
import org.mockito.MockedStatic;

class HttpToolTest {

	private final HttpTool httpTool = new HttpTool();

	@Test
	void testExecute() throws Exception {
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

			final HttpResponse response = httpTool.execute(
				Map.of("url", "https://example.com", "method", "POST", "body", "{}")
			);

			assertEquals(mockResponse, response, "Expected response not returned");
		}
	}

	@Test
	void testGet() throws Exception {
		final HttpResponse mockResponse = new HttpResponse();
		mockResponse.setStatusCode(200);
		mockResponse.appendBody("test-body");

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

			final HttpResponse response = httpTool.get(Map.of("url", "https://example.com"));

			assertEquals(mockResponse, response, "Expected response not returned");
		}
	}

	@Test
	void testPost() throws Exception {
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
						"{ \"key\": \"value\" }",
						HttpTool.DEFAULT_HTTP_TIMEOUT,
						null
					)
				)
				.thenReturn(mockResponse);

			final HttpResponse response = httpTool.post(
				Map.of("url", "https://example.com", "body", "{ \"key\": \"value\" }")
			);

			assertEquals(mockResponse, response, "Expected response not returned");
		}
	}

	@Test
	void testShouldThrowOnInvalidHeaderFormat() {
		final IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class,
			() -> httpTool.execute(Map.of("url", "https://badheaders.com", "headers", "InvalidHeaderWithoutColon")),
			"Expected IllegalArgumentException for invalid header format"
		);

		assertTrue(
			ex.getMessage().contains("Invalid header format"),
			"Expected invalid header format exception not thrown"
		);
	}
}
