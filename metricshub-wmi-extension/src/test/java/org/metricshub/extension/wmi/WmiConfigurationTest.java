package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

class WmiConfigurationTest {

	@Test
	void testValidateConfiguration() throws InvalidConfigurationException {
		assertDoesNotThrow(() ->
			WmiConfiguration
				.builder()
				.username("user")
				.password("pass".toCharArray())
				.timeout(15L)
				.build()
				.validateConfiguration("resourceKey")
		);
		assertThrows(
			InvalidConfigurationException.class,
			() ->
				WmiConfiguration
					.builder()
					.username("user")
					.password("pass".toCharArray())
					.timeout(-15L) // Bad timeout
					.build()
					.validateConfiguration("resourceKey")
		);
	}

	@Test
	void testToString() {
		assertEquals(
			"WMI as Administrator",
			WmiConfiguration
				.builder()
				.username("Administrator")
				.password("passwd".toCharArray())
				.timeout(15L)
				.build()
				.toString()
		);
	}

	@Test
	void testCopy() {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.namespace("namespace")
			.password("password".toCharArray())
			.timeout(100L)
			.username("username")
			.build();

		final IConfiguration wmiConfigurationCopy = wmiConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(wmiConfiguration, wmiConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (wmiConfiguration != wmiConfigurationCopy);
	}

	@Test
	void testGetProperty() {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.namespace("myNamespace")
			.password("myPassword".toCharArray())
			.username("myUsername")
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(wmiConfiguration.getProperty(null));
		assertNull(wmiConfiguration.getProperty(""));
		assertNull(wmiConfiguration.getProperty("badProperty"));

		assertEquals("myPassword", wmiConfiguration.getProperty("password"));
		assertEquals("myNamespace", wmiConfiguration.getProperty("namespace"));
		assertEquals("myUsername", wmiConfiguration.getProperty("username"));
		assertEquals("100", wmiConfiguration.getProperty("timeout"));
		assertEquals("myHostname", wmiConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final WmiConfiguration wmiConfiguration = new WmiConfiguration();
		assertFalse(wmiConfiguration.isCorrespondingProtocol(null));
		assertFalse(wmiConfiguration.isCorrespondingProtocol(""));
		assertFalse(wmiConfiguration.isCorrespondingProtocol("SNMP"));

		assertTrue(wmiConfiguration.isCorrespondingProtocol("wmi"));
		assertTrue(wmiConfiguration.isCorrespondingProtocol("WMI"));
		assertTrue(wmiConfiguration.isCorrespondingProtocol("WmI"));
	}
}
