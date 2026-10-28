package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;

class IpmiSelSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/ipmiSel/";
	}

	private IpmiSelSource expected(final int maxEventsPerPoll) {
		return IpmiSelSource.builder()
			.key("${source::beforeAll.testIpmiSelSource}")
			.type("ipmiSel")
			.maxEventsPerPoll(maxEventsPerPoll)
			.build();
	}

	@Test
	void testDeserializeIpmiSelSource() throws IOException {
		assertEquals(Map.of("testIpmiSelSource", expected(30)), getConnector("ipmiSel").getBeforeAll());
		assertEquals(Map.of("testIpmiSelSource", expected(50)), getConnector("ipmiSelDefault").getBeforeAll());
		assertEquals(Map.of("testIpmiSelSource", expected(-1)), getConnector("ipmiSelUnlimited").getBeforeAll());
		// Serialized whatever the connector says
		assertTrue(getConnector("ipmiSelDefault").getBeforeAll().get("testIpmiSelSource").isForceSerialization());
	}
}
