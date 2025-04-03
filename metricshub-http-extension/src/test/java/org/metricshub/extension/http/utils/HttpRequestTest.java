package org.metricshub.extension.http.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.utils.HttpRequest.HttpRequestBuilder;
import org.sentrysoftware.metricshub.engine.connector.model.common.EmbeddedFile;

class HttpRequestTest {

	private static final String CONNECTION_KEEP_ALIVE_HEADER = "Connection: keep-alive";
	private static final String BODY =
		"""
		{ "key" : "value" }
		""";
	private static final HttpConfiguration HTTP_CONFIG = HttpConfiguration.builder().build();
	private static Map<Integer, EmbeddedFile> httpEmbeddedFiles;
	public static final String HOST = "host";
	public static final String MY_CONNECTOR_1_NAME = "myConnector1";
	private static final String REF_FORMAT = "${file::%d}";
	public static final Integer EMBEDDED_FILE_1_ID = 1;
	public static final Integer EMBEDDED_FILE_2_ID = 2;
	public static final String EMBEDDED_FILE_1_REF = String.format(REF_FORMAT, EMBEDDED_FILE_1_ID);
	public static final String EMBEDDED_FILE_2_REF = String.format(REF_FORMAT, EMBEDDED_FILE_2_ID);

	/**
	 * Setup unit tests.
	 */
	@BeforeAll
	static void setup() {
		httpEmbeddedFiles = new HashMap<>();
	}

	@BeforeEach
	void clearEmbeddedFiles() {
		httpEmbeddedFiles.clear();
	}

	@Test
	void testHeaderBuilder() throws IOException {
		{
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.header(CONNECTION_KEEP_ALIVE_HEADER, Map.of(), MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertEquals(new StringHeader(CONNECTION_KEEP_ALIVE_HEADER), request.getHeader());
		}

		{
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.header(null, Map.of(), MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertNull(request.getHeader());
		}

		{
			final EmbeddedFile expectedEmbeddedFile = new EmbeddedFile(
				CONNECTION_KEEP_ALIVE_HEADER.getBytes(),
				"header",
				EMBEDDED_FILE_1_ID
			);
			httpEmbeddedFiles.put(EMBEDDED_FILE_1_ID, expectedEmbeddedFile);

			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.header(EMBEDDED_FILE_1_REF, httpEmbeddedFiles, MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertEquals(new EmbeddedFileHeader(expectedEmbeddedFile), request.getHeader());
		}
	}

	@Test
	void testBodyBuilder() throws IOException {
		{
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.body(BODY, Map.of(), MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertEquals(new StringBody(BODY), request.getBody());
		}

		{
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.body(null, Map.of(), MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertNull(request.getBody());
		}

		{
			final EmbeddedFile expectedEmbeddedFile = new EmbeddedFile(BODY.getBytes(), "body", EMBEDDED_FILE_1_ID);
			httpEmbeddedFiles.put(EMBEDDED_FILE_1_ID, expectedEmbeddedFile);

			final HttpRequest request = HttpRequest
				.builder()
				.hostname(HOST)
				.httpConfiguration(HTTP_CONFIG)
				.body(EMBEDDED_FILE_1_REF, httpEmbeddedFiles, MY_CONNECTOR_1_NAME, HOST)
				.build();

			assertEquals(new EmbeddedFileBody(expectedEmbeddedFile), request.getBody());
		}
	}

	@Test
	void testGetHttpEmbeddedFileFailsOnManyEmbeddedFiles() {
		httpEmbeddedFiles.put(
			EMBEDDED_FILE_1_ID,
			new EmbeddedFile(CONNECTION_KEEP_ALIVE_HEADER.getBytes(), "header", EMBEDDED_FILE_1_ID)
		);

		httpEmbeddedFiles.put(
			EMBEDDED_FILE_2_ID,
			new EmbeddedFile(CONNECTION_KEEP_ALIVE_HEADER.getBytes(), "header", EMBEDDED_FILE_2_ID)
		);

		final String headerValue = EMBEDDED_FILE_1_REF + " " + EMBEDDED_FILE_2_REF;

		final HttpRequestBuilder httpRequestBuilder = HttpRequest.builder();
		assertThrows(
			IllegalStateException.class,
			() -> httpRequestBuilder.header(headerValue, httpEmbeddedFiles, MY_CONNECTOR_1_NAME, HOST)
		);
	}
}
