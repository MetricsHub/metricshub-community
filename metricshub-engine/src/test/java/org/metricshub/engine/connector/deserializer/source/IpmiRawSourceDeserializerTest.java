package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;

class IpmiRawSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/ipmiRaw/";
	}

	@Test
	void testDeserializeIpmiRawSource() throws IOException {
		// An unquoted 0x06 is the YAML integer 6; the extension decodes both forms
		final IpmiRawSource expected = IpmiRawSource.builder()
			.key("${source::beforeAll.testIpmiRawSource}")
			.type("ipmiRaw")
			.netFn("6")
			.command("0x01")
			.data("0x00 0x01")
			.build();
		assertEquals(Map.of("testIpmiRawSource", expected), getConnector("ipmiRaw").getBeforeAll());
	}

	@Test
	void testDeserializeIpmiRawSourceWithoutNetFn() {
		assertThrows(JsonMappingException.class, () -> getConnector("ipmiRawNoNetFn"));
	}
}
