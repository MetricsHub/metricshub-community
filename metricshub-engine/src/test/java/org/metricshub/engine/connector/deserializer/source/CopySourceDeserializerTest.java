package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class CopySourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/copy/";
	}

	@Test
	void testDeserializeCopySource() throws IOException {
		final String testResource = "copySource";
		final Connector connector = getConnector(testResource);

		final Map<String, Source> expected = new LinkedHashMap<String, Source>(
			Map.of(
				"testCopySource",
				CopySource
					.builder()
					.key("${source::beforeAll.testCopySource}")
					.type("copy")
					.from("${source::beforeAll.anotherSource}")
					.computes(Collections.emptyList())
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
