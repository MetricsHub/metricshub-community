package org.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Head;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Sort;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Tail;

class HeadTailSortComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/headTailSort/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("headTailSort");

		// ignoreCase is false by default, and lines: 20 is read as a string
		final List<Compute> computes = List.of(
			Sort.builder().type("sort").column(2).numeric(true).ignoreCase(false).descending(true).build(),
			Head.builder().type("head").lines("20").build(),
			Tail.builder().type("tail").lines("5").build()
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
	}
}
