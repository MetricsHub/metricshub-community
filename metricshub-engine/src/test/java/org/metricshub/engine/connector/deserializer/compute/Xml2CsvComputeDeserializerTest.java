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
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;

class Xml2CsvComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/xml2Csv/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("xml2Csv");

		final List<Compute> computes = new ArrayList<>();
		computes.add(
			Xml2Csv.builder()
				.type("xml2Csv")
				.recordTag("recordTagTest")
				.properties(List.of("tag>attribute", "tag/child"))
				.build()
		);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testCompute",
				HttpSource.builder()
					.key("${source::beforeAll.testCompute}")
					.type("http")
					.url("/testUrl/")
					.computes(computes)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());

		// Each property is updated on its own, and the copy has its own list
		final Xml2Csv origin = Xml2Csv.builder().properties(List.of("a", "b")).build();
		final Xml2Csv copy = origin.copy();
		copy.update(value -> value + "!");
		assertEquals(List.of("a!", "b!"), copy.getProperties());
		assertEquals(List.of("a", "b"), origin.getProperties());
	}
}
