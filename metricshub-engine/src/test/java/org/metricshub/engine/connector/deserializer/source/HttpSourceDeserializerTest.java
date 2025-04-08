package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.connector.model.common.HttpMethod.GET;
import static org.metricshub.engine.connector.model.common.HttpMethod.POST;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class HttpSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/http/";
	}

	@Test
	void testDeserializeHttpSource() throws IOException {
		final Connector connector = getConnector("http");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testHttpSource",
				HttpSource
					.builder()
					.key("${source::beforeAll.testHttpSource}")
					.type("http")
					.url("http://machine:5985/testUrl/")
					.method(POST)
					.body("test\nbody")
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeHttpSourceWithPathOnly() throws IOException {
		final Connector connector = getConnector("http2");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testHttpSource",
				HttpSource
					.builder()
					.key("${source::beforeAll.testHttpSource}")
					.type("http")
					.path("/testPath/")
					.method(POST)
					.body("test\nbody")
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeHttpSourceWithUrlAndPath() throws IOException {
		final Connector connector = getConnector("http3");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testHttpSource",
				HttpSource
					.builder()
					.key("${source::beforeAll.testHttpSource}")
					.type("http")
					.url("http://machine:5985/path/")
					.path("/testPath/")
					.method(POST)
					.body("test\nbody")
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeHttpSourceWithoutUrlNorPath() throws IOException {
		final Connector connector = getConnector("http4");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testHttpSource",
				HttpSource
					.builder()
					.key("${source::beforeAll.testHttpSource}")
					.type("http")
					.method(POST)
					.body("test\nbody")
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeHttpSourceWithExecuteForEachEntryOf() throws IOException {
		final Connector connector = getConnector("httpExecuteForEachEntryOf");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"devices",
				HttpSource.builder().key("${source::beforeAll.devices}").type("http").url("/devices").method(GET).build(),
				"detailsOfEachDevice",
				HttpSource
					.builder()
					.key("${source::beforeAll.detailsOfEachDevice}")
					.type("http")
					.url("/device-detail/$entry.column(1)$")
					.method(GET)
					.executeForEachEntryOf(new ExecuteForEachEntryOf("${source::beforeAll.devices}", EntryConcatMethod.LIST, 0))
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
