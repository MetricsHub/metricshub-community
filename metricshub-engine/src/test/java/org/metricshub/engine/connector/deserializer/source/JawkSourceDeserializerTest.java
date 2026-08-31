package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class JawkSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/awk/";
	}

	@Test
	void testDeserializeAwk() throws IOException {
		final String testResource = "awk";
		final Connector connector = getConnector(testResource);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testAwkSource",
				JawkSource.builder()
					.key("${source::beforeAll.testAwkSource}")
					.type("awk")
					.script("scriptTest")
					.input("inputTest")
					.separators(";")
					.variables(new LinkedHashMap<>(Map.of("FS", ";", "aTable", "${source::beforeAll.otherSource}")))
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
