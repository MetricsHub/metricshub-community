package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * Test of {@link IpmiConfiguration}
 */
class IpmiConfigurationTest {

	private static final String BMC_KEY = "bmckey";
	private static final String USERNAME = "testUser";
	private static final String PASSWORD = "testPassword";
	public static final String IPMI = "IPMI";
	public static final String IPMI_CONFIGURATION_TO_STRING = "IPMI as testUser";

	@Test
	void testToString() {
		final IpmiConfiguration ipmiConfiguration = new IpmiConfiguration();

		// When the userName is NOT null, it's appended to the result
		ipmiConfiguration.setUsername(USERNAME);
		ipmiConfiguration.setPassword(PASSWORD.toCharArray());
		ipmiConfiguration.setBmcKey(BMC_KEY);
		ipmiConfiguration.setSkipAuth(false);
		assertEquals(IPMI_CONFIGURATION_TO_STRING, ipmiConfiguration.toString());

		// When the userName is null, it's not appended to the result
		ipmiConfiguration.setUsername(null);
		assertEquals(IPMI, ipmiConfiguration.toString());
	}

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resourceKey";
		{
			final IpmiConfiguration ipmiConfig = IpmiConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.bmcKey(BMC_KEY)
				.timeout(-60L)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> ipmiConfig.validateConfiguration(resourceKey));
		}

		{
			final IpmiConfiguration ipmiConfig = IpmiConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.bmcKey(BMC_KEY)
				.timeout(null)
				.build();

			assertThrows(InvalidConfigurationException.class, () -> ipmiConfig.validateConfiguration(resourceKey));
		}
	}

	@Test
	void testCopy() {
		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.bmcKey(BMC_KEY)
			.password(PASSWORD.toCharArray())
			.skipAuth(false)
			.timeout(100L)
			.username(USERNAME)
			.build();

		final IConfiguration ipmiConfigurationCopy = ipmiConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(ipmiConfiguration, ipmiConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (ipmiConfiguration != ipmiConfigurationCopy);
	}

	@Test
	void testGetProperty() {
		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.username("myUsername")
			.password("myPassword".toCharArray())
			.bmcKey("myBmckey")
			.skipAuth(true)
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(ipmiConfiguration.getProperty(null));
		assertNull(ipmiConfiguration.getProperty(""));
		assertNull(ipmiConfiguration.getProperty("badProperty"));

		final Object propertyPasswordObject = ipmiConfiguration.getProperty("password");
		assertInstanceOf(char[].class, propertyPasswordObject);
		assertArrayEquals("myPassword".toCharArray(), (char[]) propertyPasswordObject);

		assertEquals("myUsername", ipmiConfiguration.getProperty("username"));
		assertEquals(true, ipmiConfiguration.getProperty("skipAuth"));
		assertEquals("myBmckey", ipmiConfiguration.getProperty("bmckey"));
		assertEquals(100L, ipmiConfiguration.getProperty("timeout"));
		assertEquals("myHostname", ipmiConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final IpmiConfiguration ipmiConfiguration = new IpmiConfiguration();
		assertFalse(ipmiConfiguration.isCorrespondingProtocol(null));
		assertFalse(ipmiConfiguration.isCorrespondingProtocol(""));
		assertFalse(ipmiConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(ipmiConfiguration.isCorrespondingProtocol("IPMI"));
		assertTrue(ipmiConfiguration.isCorrespondingProtocol("ipmi"));
		assertTrue(ipmiConfiguration.isCorrespondingProtocol("IpMi"));
	}
}
