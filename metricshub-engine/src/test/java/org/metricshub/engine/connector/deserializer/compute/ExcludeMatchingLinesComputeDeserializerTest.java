package org.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExcludeMatchingLines;

class ExcludeMatchingLinesComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/excludeMatchingLines/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("excludeMatchingLines");

		final List<Compute> computes = new ArrayList<>();
		computes.add(
			ExcludeMatchingLines.builder().type("excludeMatchingLines").column(1).valueList("test,exclude,values").build()
		);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testCompute",
				HttpSource
					.builder()
					.key("${source::beforeAll.testCompute}")
					.type("http")
					.url("/testUrl/")
					.computes(computes)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
