package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class SnmpGetSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/snmpGet/";
	}

	@Test
	void testDeserializeTableJoin() throws IOException {
		final String testResource = "snmpGet";

		final Connector connector = getConnector(testResource);
		Map<String, Source> expected = new LinkedHashMap<>();
		expected.put(
			"testSnmpGetSource",
			SnmpGetSource
				.builder()
				.key("${source::beforeAll.testSnmpGetSource}")
				.type("snmpGet")
				.oid("testOidString")
				.forceSerialization(true)
				.computes(Collections.emptyList())
				.build()
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
