package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class BeforeAllDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/beforeAll/";
	}

	@Test
	void testDeserializeBeforeAll() throws IOException {
		final Connector connector = getConnector("beforeAll");

		assertNotNull(connector);

		var beforeAll = connector.getBeforeAll();

		assertTrue(beforeAll instanceof LinkedHashMap, "beforeAll are expected to be a LinkedHashMap.");

		final Map<String, Source> expected = new LinkedHashMap<String, Source>(
			Map.of(
				"ipmiSource",
				new IpmiSource("ipmi", Collections.emptyList(), false, "${source::beforeAll.ipmiSource}", null)
			)
		);

		assertEquals(expected, beforeAll);
	}

	@Test
	void testBeforeAllBlankSource() throws IOException {
		try {
			getConnector("beforeAllBlankSource");
			Assertions.fail(IO_EXCEPTION_MSG);
		} catch (InvalidFormatException e) {
			final String message = "The source key referenced by 'beforeAll' cannot be empty.";
			checkMessage(e, message);
		}
	}

	@Test
	void testBeforeAllNull() throws IOException {
		assertEquals(Collections.emptyMap(), getConnector("beforeAllNull").getBeforeAll());
	}
}
