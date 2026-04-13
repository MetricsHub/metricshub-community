package org.metricshub.extension.ping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * Test of {@link PingConfiguration}
 */
class PingConfigurationTest {

	private static final String RESOURCE_KEY = "resource-test-key";

	@Test
	void testValidateConfiguration() {
		assertThrows(
			InvalidConfigurationException.class,
			() -> PingConfiguration.builder().timeout(-60L).build().validateConfiguration(RESOURCE_KEY)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> PingConfiguration.builder().timeout(null).build().validateConfiguration(RESOURCE_KEY)
		);
		assertDoesNotThrow(() -> PingConfiguration.builder().timeout(60L).build().validateConfiguration(RESOURCE_KEY));
	}

	@Test
	void testCopy() {
		final PingConfiguration pingConfiguration = PingConfiguration.builder().timeout(100L).build();

		final IConfiguration pingConfigurationCopy = pingConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(pingConfiguration, pingConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (pingConfiguration != pingConfigurationCopy);
	}

	@Test
	void testGetProperty() {
		final PingConfiguration pingConfiguration = PingConfiguration
			.builder()
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(pingConfiguration.getProperty(null));
		assertNull(pingConfiguration.getProperty(""));
		assertNull(pingConfiguration.getProperty("badProperty"));

		assertEquals("100", pingConfiguration.getProperty("timeout"));
		assertEquals("myHostname", pingConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final PingConfiguration pingConfiguration = new PingConfiguration();
		assertFalse(pingConfiguration.isCorrespondingProtocol(null));
		assertFalse(pingConfiguration.isCorrespondingProtocol(""));
		assertFalse(pingConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(pingConfiguration.isCorrespondingProtocol("PING"));
		assertTrue(pingConfiguration.isCorrespondingProtocol("ping"));
		assertTrue(pingConfiguration.isCorrespondingProtocol("PiNg"));
	}
}
