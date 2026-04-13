package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.connector.model.common.ResultContent.ALL;
import static org.metricshub.engine.connector.model.common.ResultContent.BODY;
import static org.metricshub.engine.connector.model.common.ResultContent.HEADER;
import static org.metricshub.engine.connector.model.common.ResultContent.HTTP_STATUS;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;

class ResultContentDeserializerTest {

	private static final String SOURCE_URL = "/url";
	private static final String HTTP_SOURCE_YAML =
		"""
		type: http
		url: /url
		resultContent: ReplaceMe
		""";

	private static ObjectMapper mapper;

	private static final String REPLACE_ME = "ReplaceMe";

	@BeforeAll
	static void setUp() {
		mapper =
			JsonMapper
				.builder(new YAMLFactory())
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
				.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
				.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
				.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
				.build();
	}

	@Test
	void testResultBodyValues() throws IOException {
		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(BODY).build(),
			deserializeHttpSource("body")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(BODY).build(),
			deserializeHttpSource("BODY")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(BODY).build(),
			deserializeHttpSource("Body")
		);
	}

	@Test
	void testResultAllValues() throws IOException {
		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(ALL).build(),
			deserializeHttpSource("all")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(ALL).build(),
			deserializeHttpSource("All")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(ALL).build(),
			deserializeHttpSource("ALL")
		);
	}

	@Test
	void testResultHeaderValues() throws IOException {
		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HEADER).build(),
			deserializeHttpSource("header")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HEADER).build(),
			deserializeHttpSource("Header")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HEADER).build(),
			deserializeHttpSource("HEADER")
		);
	}

	@Test
	void testResultHttpStatusValues() throws IOException {
		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HTTP_STATUS).build(),
			deserializeHttpSource("http_status")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HTTP_STATUS).build(),
			deserializeHttpSource("httpStatus")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HTTP_STATUS).build(),
			deserializeHttpSource("Http_Status")
		);

		assertEquals(
			HttpSource.builder().type("http").url(SOURCE_URL).resultContent(HTTP_STATUS).build(),
			deserializeHttpSource("HTTP_STATUS")
		);
	}

	/**
	 * Deserialization of HTTP_SOURCE_YAML into an {@link HttpSource} object
	 *
	 * @param replacement
	 * @return {@link HttpSource}
	 * @throws IOException
	 */
	private HttpSource deserializeHttpSource(final String replacement) throws IOException {
		return JsonHelper.deserialize(
			mapper,
			new ByteArrayInputStream(HTTP_SOURCE_YAML.replace(REPLACE_ME, replacement).getBytes()),
			HttpSource.class
		);
	}
}
