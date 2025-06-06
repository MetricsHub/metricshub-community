package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.configuration.TransportProtocols;

/**
 * Test of {@link WbemConfiguration}
 */
class WbemConfigurationTest {

	private static final String USERNAME = "testUser";
	public static final String PASSWORD = "testPassword";
	public static final String WBEM_NAMESPACE = "testWbemNamespace";
	public static final String WBEM_VCENTER = "testWbemVCenter";
	public static final String WBEM_CONFIGURATION_TO_STRING = "https/5989 as testUser";
	public static final String WBEM_SECURE = "https/5989";

	@Test
	void testToString() {
		final WbemConfiguration wbemConfiguration = new WbemConfiguration();

		// When the userName is NOT null, it's appended to the result
		wbemConfiguration.setUsername(USERNAME);
		wbemConfiguration.setPassword(PASSWORD.toCharArray());
		wbemConfiguration.setNamespace(WBEM_NAMESPACE);
		wbemConfiguration.setVCenter(WBEM_VCENTER);
		assertEquals(WBEM_CONFIGURATION_TO_STRING, wbemConfiguration.toString());

		// When the userName is null, it's not appended to the result
		wbemConfiguration.setUsername(null);
		assertEquals(WBEM_SECURE, wbemConfiguration.toString());
	}

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resourceKey";
		assertThrows(
			InvalidConfigurationException.class,
			() ->
				WbemConfiguration.builder().timeout(-60L).port(1234).vCenter(null).build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(null).port(1234).build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(60L).port(-1).build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(60L).port(null).build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(60L).port(66666).build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(60L).username("").build().validateConfiguration(resourceKey)
		);
		assertThrows(
			InvalidConfigurationException.class,
			() -> WbemConfiguration.builder().timeout(60L).vCenter("").build().validateConfiguration(resourceKey)
		);
		assertDoesNotThrow(() ->
			WbemConfiguration
				.builder()
				.timeout(60L)
				.port(1234)
				.vCenter("vCenter")
				.username("username")
				.password("password".toCharArray())
				.build()
				.validateConfiguration(resourceKey)
		);
	}

	@Test
	void testCopy() {
		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.namespace(WBEM_NAMESPACE)
			.password(PASSWORD.toCharArray())
			.port(100)
			.protocol(TransportProtocols.HTTPS)
			.timeout(100L)
			.username(USERNAME)
			.vCenter(WBEM_VCENTER)
			.build();

		final IConfiguration wbemConfigurationCopy = wbemConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(wbemConfiguration, wbemConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (wbemConfiguration != wbemConfigurationCopy);
	}
}
