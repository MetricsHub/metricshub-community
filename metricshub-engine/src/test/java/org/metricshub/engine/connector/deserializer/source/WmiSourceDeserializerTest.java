package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;

class WmiSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/wmi/";
	}

	@Test
	void testDeserializeWmiSource() throws IOException {
		final String testResource = "wmiSource";
		final Connector connector = getConnector(testResource);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testWmiSource",
				WmiSource
					.builder()
					.key("${source::beforeAll.testWmiSource}")
					.type("wmi")
					.query("testQuery")
					.namespace("testNamespace")
					.computes(Collections.emptyList())
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
