package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;

class ConnectorDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/connector/";
	}

	@Test
	void testDeserializeConnectorWithStringPlatformsField() throws IOException {
		// Retrieve the connector
		final Connector connector = getConnector("connector");
		final ConnectorIdentity connectorIdentity = connector.getConnectorIdentity();
		assertNotNull(connector);
		assertEquals("testConnector", connectorIdentity.getDisplayName());

		// Compare the actual platforms against the expected platforms
		final Set<String> expectedPlatforms = new LinkedHashSet<>();
		expectedPlatforms.add("testPlatform1");
		expectedPlatforms.add("testPlatform2");
		expectedPlatforms.add("testPlatform3");
		final Set<String> actualPlatforms = connectorIdentity.getPlatforms();
		assertEquals(
			new ArrayList<>(expectedPlatforms),
			new ArrayList<>(actualPlatforms),
			"The order of platforms does not match!"
		);

		// Check other connector identity fields
		assertEquals("testReliesOn", connectorIdentity.getReliesOn());
		assertEquals("1.0.0", connectorIdentity.getVersion());
		assertEquals("20", connectorIdentity.getProjectVersion());
		assertEquals("testInformation", connectorIdentity.getInformation());
	}

	@Test
	void testDeserializeConnectorWithSetPlatformsField() throws IOException {
		// Retrieve the connector
		final Connector connector = getConnector("connector");
		final ConnectorIdentity connectorIdentity = connector.getConnectorIdentity();
		assertNotNull(connector);

		// Compare the actual platforms against the expected platforms
		final Set<String> expectedPlatforms = new LinkedHashSet<>();
		expectedPlatforms.add("testPlatform1");
		expectedPlatforms.add("testPlatform2");
		expectedPlatforms.add("testPlatform3");
		final Set<String> actualPlatforms = connectorIdentity.getPlatforms();
		assertEquals(
			new ArrayList<>(expectedPlatforms),
			new ArrayList<>(actualPlatforms),
			"The order of platforms does not match!"
		);

		// Check other connector identity fields
		assertEquals("testReliesOn", connectorIdentity.getReliesOn());
		assertEquals("1.0.0", connectorIdentity.getVersion());
		assertEquals("20", connectorIdentity.getProjectVersion());
		assertEquals("testInformation", connectorIdentity.getInformation());
	}
}
