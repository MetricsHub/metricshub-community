package org.sentrysoftware.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.connector.deserializer.DeserializerTest;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.compute.ArrayTranslate;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.compute.Compute;

class ArrayTranslateComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/arrayTranslate/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("arrayTranslate");

		final List<Compute> computes = new ArrayList<>();
		computes.add(
			ArrayTranslate
				.builder()
				.type("arrayTranslate")
				.column(1)
				.arraySeparator("arraySeparatorTest")
				.resultSeparator("resultSeparatorTest")
				.translationTable(new ReferenceTranslationTable("${translation::translationTableTest}"))
				.build()
		);

		assertNotNull(connector.getPre());
		assertEquals(1, connector.getPre().size());

		final Source actual = connector.getPre().get("testCompute");
		assertNotNull(actual);
		assertInstanceOf(HttpSource.class, actual);
		assertEquals("${source::pre.testCompute}", actual.getKey());
		assertEquals("http", actual.getType());
		assertEquals("/testUrl/", ((HttpSource) actual).getUrl());
	}
}
