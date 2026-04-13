package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class JmxSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/jmx/";
	}

	@Test
	void testDeserializeStatic() throws IOException {
		final String testResource = "jmx";
		final Connector connector = getConnector(testResource);

		final Map<String, Source> expected = new LinkedHashMap<String, Source>(
			Map.of(
				"jmxSource",
				JmxSource
					.builder()
					.type("jmx")
					.objectName("org.metricshub.extension.jmx:type=JmxMBean,scope=*")
					.attributes(new LinkedList<>(List.of("Name")))
					.key("${source::beforeAll.jmxSource}")
					.forceSerialization(false)
					.keyProperties(new LinkedList<>(List.of("scope")))
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll(), "Deserialized JMX source does not match expected structure.");
	}
}
