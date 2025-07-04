package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ProgrammableConfigurationProviderTest {

	@Test
	void testLoad() {
		var pcp = new ProgrammableConfigurationProvider();
		var nodes = pcp.load(Paths.get("src/test/resources/config"));
		assertEquals(1, nodes.size(), "Should load one configuration fragment");
		final JsonNode node = nodes.iterator().next();
		final JsonNode resources = node.get("resources");
		assertNotNull(resources, "Resources node should not be null");
		assertTrue(resources.has("host-01-system"), "Resources should contain host-01-system");
		assertTrue(resources.has("host-02-system"), "Resources should contain host-02-system");
		assertTrue(resources.has("host-03-system"), "Resources should contain host-03-system");
		assertResourceConfiguration(resources, "host-01-system");
		assertResourceConfiguration(resources, "host-02-system");
		assertResourceConfiguration(resources, "host-03-system");
	}

	/**
	 * Asserts that the resource configuration for host-01-system is correct.
	 * @param resources The resources JsonNode containing the configuration.
	 */
	private void assertResourceConfiguration(final JsonNode resources, String resourceName) {
		final JsonNode resource = resources.get(resourceName);
		assertTrue(resource.has("attributes"), resourceName + " should have attributes");
		final JsonNode attributes = resource.get("attributes");
		assertTrue(attributes.has("host.name"), resourceName + " should have host.name attribute");
		assertTrue(attributes.has("host.type"), resourceName + " should have host.type attribute");
		assertTrue(resource.has("protocols"), resourceName + " should have protocols");
		final JsonNode protocols = resource.get("protocols");
		assertTrue(protocols.has("ssh"), resourceName + " should have ssh protocol");
		assertTrue(protocols.get("ssh").has("username"), resourceName + " ssh should have username");
		assertTrue(protocols.get("ssh").has("password"), resourceName + " ssh should have password");
		assertTrue(resource.has("connectors"), resourceName + " should have connectors");
		assertTrue(resource.get("connectors").isArray(), resourceName + " connectors should be an array");
	}
}
