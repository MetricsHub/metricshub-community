package org.sentrysoftware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.extension.ipmi.IpmiConfiguration;

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
}
