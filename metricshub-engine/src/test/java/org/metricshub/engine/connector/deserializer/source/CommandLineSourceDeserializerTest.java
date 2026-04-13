package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class CommandLineSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/commandLine/";
	}

	@Test
	void testDeserializeCommandLine() throws IOException {
		final Connector connector = getConnector("commandLine");

		Map<String, Source> expected = new LinkedHashMap<>();
		expected.put(
			"commandLine1",
			CommandLineSource
				.builder()
				.key("${source::beforeAll.commandLine1}")
				.type("commandLine")
				.timeout((long) 30)
				.exclude("excludeRegExp")
				.keep("keepRegExp")
				.beginAtLineNumber(2)
				.endAtLineNumber(10)
				.separators(",;")
				.selectColumns("1-6")
				.forceSerialization(true)
				.executeLocally(true)
				.commandLine("myCommand")
				.computes(Collections.emptyList())
				.build()
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
