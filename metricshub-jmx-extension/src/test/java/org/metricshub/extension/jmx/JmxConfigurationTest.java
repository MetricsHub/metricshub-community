package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;

class JmxConfigurationTest {

	@Test
	void testShouldUseDefaultPortWhenNotSpecified() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").build();

		assertEquals(JmxConfiguration.DEFAULT_JMX_PORT, config.getPort(), "Default port should be set when not specified");
		assertEquals("localhost", config.getHostname(), "Hostname should be correctly assigned");
	}

	@Test
	void testShouldThrowWhenHostnameIsNull() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname(null).port(1099).build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> config.validateConfiguration("resA"),
			"Expected exception for null hostname"
		);
		assertTrue(exception.getMessage().contains("Invalid JMX host"), "Error message should mention invalid JMX host");
	}

	@Test
	void testShouldThrowWhenHostnameIsBlank() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("   ").port(1099).build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> config.validateConfiguration("resB"),
			"Expected exception for blank hostname"
		);
		assertTrue(exception.getMessage().contains("Invalid JMX host"), "Error message should mention invalid JMX host");
	}

	@Test
	void testShouldThrowWhenPortIsTooLow() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").port(0).build();

		final Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> config.validateConfiguration("resC"),
			"Expected exception for port < 1"
		);
		assertTrue(exception.getMessage().contains("Invalid JMX port"), "Error message should mention invalid JMX port");
	}

	@Test
	void testShouldThrowWhenPortIsTooHigh() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").port(70000).build();

		Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> config.validateConfiguration("resD"),
			"Expected exception for port > 65535"
		);
		assertTrue(exception.getMessage().contains("Invalid JMX port"), "Error message should mention invalid JMX port");
	}

	@Test
	void testShouldThrowWhenPortIsNull() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("localhost").port(null).build();

		Exception exception = assertThrows(
			InvalidConfigurationException.class,
			() -> config.validateConfiguration("resE"),
			"Expected exception for <null> port"
		);
		assertTrue(exception.getMessage().contains("Invalid JMX port"), "Error message should mention invalid JMX port");
	}

	@Test
	void testShouldPassValidationForValidConfig() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("192.168.0.1").port(1099).build();

		assertDoesNotThrow(() -> config.validateConfiguration("resValid"), "Valid config should not throw");
	}

	@Test
	void testShouldCopyConfigurationCorrectly() {
		char[] password = { 's', 'e', 'c', 'r', 'e', 't' };
		final JmxConfiguration original = JmxConfiguration
			.builder()
			.hostname("myhost")
			.port(2020)
			.username("admin")
			.password(password)
			.build();

		final JmxConfiguration copy = (JmxConfiguration) original.copy();

		assertNotSame(original, copy, "Copy should return a new instance");
		assertEquals(original.getHostname(), copy.getHostname(), "Hostname should match in copy");
		assertEquals(original.getPort(), copy.getPort(), "Port should match in copy");
		assertEquals(original.getUsername(), copy.getUsername(), "Username should match in copy");
		assertArrayEquals(original.getPassword(), copy.getPassword(), "Password should match in copy");
	}

	@Test
	void testShouldFormatToStringCorrectlyWithUsername() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host").port(1234).username("user").build();

		assertEquals("JMX/host:1234 as user", config.toString(), "toString() should format with username");
	}

	@Test
	void testShouldFormatToStringCorrectlyWithoutUsername() {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host").port(1234).build();

		assertEquals("JMX/host:1234", config.toString(), "toString() should format without username");
	}

	@Test
	void testGetProperty() {
		final JmxConfiguration jmxConfiguration = JmxConfiguration
			.builder()
			.username("myUsername")
			.password("myPassword".toCharArray())
			.port(443)
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(jmxConfiguration.getProperty(null));
		assertNull(jmxConfiguration.getProperty(""));
		assertNull(jmxConfiguration.getProperty("badProperty"));

		assertEquals("myPassword", jmxConfiguration.getProperty("password"));
		assertEquals("myUsername", jmxConfiguration.getProperty("username"));
		assertEquals("443", jmxConfiguration.getProperty("port"));
		assertEquals("100", jmxConfiguration.getProperty("timeout"));
		assertEquals("myHostname", jmxConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final JmxConfiguration jmxConfiguration = new JmxConfiguration();
		assertFalse(jmxConfiguration.isCorrespondingProtocol(null));
		assertFalse(jmxConfiguration.isCorrespondingProtocol(""));
		assertFalse(jmxConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(jmxConfiguration.isCorrespondingProtocol("JMX"));
		assertTrue(jmxConfiguration.isCorrespondingProtocol("jmx"));
		assertTrue(jmxConfiguration.isCorrespondingProtocol("JmX"));
	}
}
