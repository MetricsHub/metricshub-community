package com.sentrysoftware.matrix.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.identity.criterion.Criterion;
import com.sentrysoftware.matrix.connector.model.identity.criterion.Ipmi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IpmiCriterionDeserializerTest {

	@Test
	/**
	 * Checks that the criteria type is ipmi
	 *
	 * @throws IOException
	 */
	void testDeserializeIpmiCriterion() throws IOException {
		final ConnectorDeserializer deserializer = new ConnectorDeserializer();
		final Connector connector = deserializer
				.deserialize(new File("src/test/resources/test-files/connector/ipmiCriterion.yaml"));

		List<Criterion> expected = new ArrayList<>();
		expected.add(new Ipmi("ipmi", true));

		assertNotNull(connector);
		assertEquals("ipmiCriterion", connector.getConnectorIdentity().getCompiledFilename());

		assertNotNull(connector.getConnectorIdentity().getDetection());
		List<Criterion> criteria = connector.getConnectorIdentity().getDetection().getCriteria();
		assertEquals(expected, criteria);
	}
}
