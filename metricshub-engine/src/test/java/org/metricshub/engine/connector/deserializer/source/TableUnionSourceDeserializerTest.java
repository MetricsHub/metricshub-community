package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;

class TableUnionSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/tableUnion/";
	}

	@Test
	void testDeserializeTableUnion() throws IOException {
		final String testResource = "tableUnion";

		List<String> tables = new ArrayList<>();
		tables.add("testTable1");
		tables.add("testTable2");
		tables.add("testTable3");

		final Connector connector = getConnector(testResource);
		Map<String, Source> expected = new LinkedHashMap<>();
		expected.put(
			"testTableUnionSource",
			TableUnionSource
				.builder()
				.key("${source::beforeAll.testTableUnionSource}")
				.type("tableUnion")
				.tables(tables)
				.build()
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
