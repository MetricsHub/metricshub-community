package org.metricshub.extension.http;

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

	@Test
	void testCopy() {
		final HttpConfiguration httpConfiguration = HttpConfiguration
			.builder()
			.https(true)
			.password("password".toCharArray())
			.port(100)
			.timeout(100L)
			.username("username")
			.build();

		final IConfiguration httpConfigurationCopy = httpConfiguration.copy();
		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(httpConfiguration, httpConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (httpConfiguration != httpConfigurationCopy);
	}

	@Test
	void testGetProperty() {
		final HttpConfiguration httpConfiguration = HttpConfiguration
			.builder()
			.username("myUsername")
			.password("myPassword".toCharArray())
			.https(true)
			.port(443)
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(httpConfiguration.getProperty(null));
		assertNull(httpConfiguration.getProperty(""));
		assertNull(httpConfiguration.getProperty("badProperty"));

		assertEquals("myPassword", httpConfiguration.getProperty("password"));
		assertEquals("myUsername", httpConfiguration.getProperty("username"));
		assertEquals("true", httpConfiguration.getProperty("https"));
		assertEquals("443", httpConfiguration.getProperty("port"));
		assertEquals("100", httpConfiguration.getProperty("timeout"));
		assertEquals("myHostname", httpConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final HttpConfiguration httpConfiguration = new HttpConfiguration();
		assertFalse(httpConfiguration.isCorrespondingProtocol(null));
		assertFalse(httpConfiguration.isCorrespondingProtocol(""));
		assertFalse(httpConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(httpConfiguration.isCorrespondingProtocol("HTTP"));
		assertTrue(httpConfiguration.isCorrespondingProtocol("http"));
		assertTrue(httpConfiguration.isCorrespondingProtocol("HtTp"));
	}
}
