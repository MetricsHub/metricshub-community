package org.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Prepend;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Substring;

class AppendComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/append/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("append");

		final List<Compute> computes = new ArrayList<>();
		computes.add(Append.builder().type("append").column(1).value("column(n)").build());

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

	/**
	 * <code>column</code> is optional on <code>prepend</code>, <code>append</code>, <code>replace</code> and
	 * <code>substring</code>: without it, they read and write a text.
	 */
	@Test
	void testDeserializeComputesWithoutColumn() throws IOException {
		final List<Compute> computes = getConnector("textComputes").getBeforeAll().get("testCompute").getComputes();

		assertEquals(
			List.of(
				Prepend.builder().type("prepend").value("Bearer ").build(),
				Append.builder().type("append").value("suffix").build(),
				Replace.builder().type("replace").existingValue("a").newValue("b").build(),
				Substring.builder().type("substring").start("1").length("10").build()
			),
			computes
		);
		for (final Compute compute : computes) {
			// Without a column, the compute works on what it is fed and leaves its type as it is
			assertNull(compute.getInputContentType());
			assertEquals(ContentType.TEXT, compute.getOutputContentType(ContentType.TEXT));
			assertEquals(ContentType.TABLE, compute.getOutputContentType(ContentType.TABLE));
			assertEquals(compute, compute.copy());
		}
		assertEquals(ContentType.TABLE, Append.builder().column(1).value("x").build().getInputContentType());
		assertEquals(
			ContentType.TABLE,
			Append.builder().column(1).value("x").build().getOutputContentType(ContentType.TABLE)
		);
	}
}
