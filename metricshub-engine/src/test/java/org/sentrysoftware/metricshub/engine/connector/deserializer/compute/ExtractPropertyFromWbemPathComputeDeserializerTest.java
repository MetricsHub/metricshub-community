package org.sentrysoftware.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.connector.deserializer.DeserializerTest;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.compute.ExtractPropertyFromWbemPath;

class ExtractPropertyFromWbemPathComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/extractPropertyFromWbemPath/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("extractPropertyFromWbemPath");

		final List<Compute> computes = new ArrayList<>();
		computes.add(
			ExtractPropertyFromWbemPath
				.builder()
				.type("extractPropertyFromWbemPath")
				.column(1)
				.property("propertyTest")
				.build()
		);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testCompute",
				HttpSource.builder().key("${source::pre.testCompute}").type("http").url("/testUrl/").computes(computes).build()
			)
		);

		assertEquals(expected, connector.getPre());
	}
}
