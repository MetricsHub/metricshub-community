package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

class WinRmConfigurationTest {

	@Test
	void testValidateConfiguration() throws InvalidConfigurationException {
		assertDoesNotThrow(() ->
			WinRmConfiguration
				.builder()
				.username("user")
				.password("pass".toCharArray())
				.namespace("namespace")
				.port(443)
				.timeout(15L)
				.build()
				.validateConfiguration("resourceKey")
		);
		assertThrows(
			InvalidConfigurationException.class,
			() ->
				WinRmConfiguration
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
			"WinRm as Administrator",
			WinRmConfiguration
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
		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
			.builder()
			.authentications(List.of(AuthenticationEnum.KERBEROS))
			.namespace("namespace")
			.password("password".toCharArray())
			.port(100)
			.protocol(TransportProtocols.HTTPS)
			.timeout(100L)
			.username("username")
			.build();

		final WinRmConfiguration winRmConfigurationCopy = (WinRmConfiguration) winRmConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(winRmConfiguration, winRmConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (winRmConfiguration != winRmConfigurationCopy);
		assert (winRmConfiguration.getAuthentications() != winRmConfigurationCopy.getAuthentications());
	}

	@Test
	void testGetProperty() {
		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
			.builder()
			.namespace("myNamespace")
			.password("myPassword".toCharArray())
			.port(443)
			.protocol(TransportProtocols.HTTPS)
			.username("myUsername")
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(winRmConfiguration.getProperty(null));
		assertNull(winRmConfiguration.getProperty(""));
		assertNull(winRmConfiguration.getProperty("badProperty"));

		final Object propertyPasswordObject = winRmConfiguration.getProperty("password");
		assertInstanceOf(char[].class, propertyPasswordObject);
		assertArrayEquals("myPassword".toCharArray(), (char[]) propertyPasswordObject);

		assertEquals("myNamespace", winRmConfiguration.getProperty("namespace"));
		assertEquals(443, winRmConfiguration.getProperty("port"));
		assertEquals(TransportProtocols.HTTPS.toString(), winRmConfiguration.getProperty("protocol"));
		assertEquals("myUsername", winRmConfiguration.getProperty("username"));
		assertEquals(100L, winRmConfiguration.getProperty("timeout"));
		assertEquals("myHostname", winRmConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final WinRmConfiguration winRmConfiguration = new WinRmConfiguration();
		assertFalse(winRmConfiguration.isCorrespondingProtocol(null));
		assertFalse(winRmConfiguration.isCorrespondingProtocol(""));
		assertFalse(winRmConfiguration.isCorrespondingProtocol("SNMP"));

		assertTrue(winRmConfiguration.isCorrespondingProtocol("winrm"));
		assertTrue(winRmConfiguration.isCorrespondingProtocol("WINRM"));
		assertTrue(winRmConfiguration.isCorrespondingProtocol("WinRm"));
	}
}
