package com.sentrysoftware.matrix.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.matrix.connector.deserializer.DeserializerTest;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.HttpSource;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.Source;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.compute.Compute;
import com.sentrysoftware.matrix.connector.model.monitor.task.source.compute.Substring;

class SubstringComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/substring/";
	}

	@Test
	void testDeserializeSubstringCompute() throws IOException {
		final Connector connector = getConnector("substring");

        final List<Compute> computes = new ArrayList<>();
        computes.add(
            Substring.builder()
					.type("substring")
					.column(1)
					.start("100")
					.length("10")
					.build()
        );

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testCompute",
				HttpSource
					.builder()
					.key("$pre.testCompute")
					.type("http")
					.url("/testUrl/")
                    .computes(computes)
					.build()
			)
		);

		assertEquals(expected, connector.getPre());
	}
}
