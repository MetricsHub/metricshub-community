package org.metricshub.engine.connector.deserializer.criterion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;

class IpmiCriterionDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/detection/criteria/ipmi/";
	}

	@Test
	/**
	 * Checks that the criteria type is ipmi
	 *
	 * @throws IOException
	 */
	void testDeserializeDoesntThrow() throws IOException {
		// NOSONAR compareCriterion performs assertion
		final Connector connector = getConnector("ipmiCriterion");

		List<Criterion> expected = new ArrayList<>();
		expected.add(new IpmiCriterion("ipmi", true));
		// Serialized whatever the connector says
		assertTrue(new IpmiCriterion().isForceSerialization());

		compareCriterion(connector, expected);
	}
}
