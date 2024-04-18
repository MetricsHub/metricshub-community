package org.sentrysoftware.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;

/**
 * Test of {@link HttpConfiguration}
 */
class HttpConfigurationTest {

	private static final String RESOURCE_KEY = "resource-test-key";

	final HttpConfiguration httpsConfiguration = new HttpConfiguration();

	@Test
	void testToString() {
		// When the userName is NOT null, it's appended to the result
		httpsConfiguration.setUsername("testUser");
		httpsConfiguration.setPassword("testPassword".toCharArray());
		assertEquals("HTTPS/443 as testUser", httpsConfiguration.toString());

		// When the userName is null, it's not appended to the result
		httpsConfiguration.setUsername(null);
		assertEquals("HTTPS/443", httpsConfiguration.toString());
	}

	@Test
	void testValidateConfiguration() {
		assertThrows(
			InvalidConfigurationException.class,
			() -> HttpConfiguration.builder().timeout(-60L).port(1234).build().validateConfiguration(RESOURCE_KEY)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> HttpConfiguration.builder().timeout(null).port(1234).build().validateConfiguration(RESOURCE_KEY)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> HttpConfiguration.builder().timeout(60L).port(-1).build().validateConfiguration(RESOURCE_KEY)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> HttpConfiguration.builder().timeout(60L).port(null).build().validateConfiguration(RESOURCE_KEY)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> HttpConfiguration.builder().timeout(60L).port(66666).build().validateConfiguration(RESOURCE_KEY)
		);
		assertDoesNotThrow(() ->
			HttpConfiguration.builder().timeout(60L).port(1234).build().validateConfiguration(RESOURCE_KEY)
		);
	}
}
