package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.oscommand.OsCommandConfiguration;

/**
 * Tests for {@link EmulationConfiguration}.
 */
class EmulationConfigurationTest {

	@Test
	void testValidateConfiguration() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().hostname("test-host").build();
		assertDoesNotThrow(() -> configuration.validateConfiguration("resource-key"));
	}

	@Test
	void testSetTimeout() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().build();
		// setTimeout is a no-op for emulation
		assertDoesNotThrow(() -> configuration.setTimeout(120L));
		assertDoesNotThrow(() -> configuration.setTimeout(null));
	}

	@Test
	void testCopy() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().hostname("test-host").build();
		final EmulationConfiguration copy = (EmulationConfiguration) configuration.copy();

		assertEquals(configuration, copy);
		assertNotSame(configuration, copy);
		assertEquals("test-host", copy.getHostname());
	}

	@Test
	void testCopyNullHostname() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().build();
		final EmulationConfiguration copy = (EmulationConfiguration) configuration.copy();

		assertEquals(configuration, copy);
		assertNotSame(configuration, copy);
		assertNull(copy.getHostname());
	}

	@Test
	void testGetProperty() {
		final EmulationConfiguration configuration = EmulationConfiguration
			.builder()
			.http(new HttpEmulationConfig(HttpConfiguration.builder().timeout(10L).build(), null))
			.oscommand(new OsCommandEmulationConfig(OsCommandConfiguration.builder().timeout(42L).build(), null))
			.hostname("my-host")
			.build();

		// Single-argument form is unsupported — use the protocol-scoped overload instead
		assertThrows(UnsupportedOperationException.class, () -> configuration.getProperty("timeout"));

		// Protocol-scoped lookups
		assertEquals("10", configuration.getProperty("http", "timeout"));
		assertEquals("42", configuration.getProperty("oscommand", "timeout"));
		assertEquals("my-host", configuration.getProperty("http", "hostname"));
		assertNull(configuration.getProperty("ssh", "timeout"));
		assertNull(configuration.getProperty(null, "timeout"));
		assertNull(configuration.getProperty("http", null));
	}

	@Test
	void testBuildConfigurationWithNestedProtocolConfigurationNode() throws Exception {
		final JsonNode jsonNode = EmulationExtension
			.newObjectMapper()
			.readTree(
				"""
				http:
				  directory: http-recordings
				  configuration:
				    username: http-user
				    password: http-password
				    port: 8443
				ssh:
				  directory: ssh-recordings
				  configuration:
				    username: ssh-user
				    password: ssh-password
				    port: 2222
				"""
			);

		final EmulationConfiguration configuration = (EmulationConfiguration) new EmulationExtension()
			.buildConfiguration(EmulationExtension.IDENTIFIER, jsonNode, value -> value);

		assertNotNull(configuration.getHttp());
		assertEquals("http-recordings", configuration.getHttp().getDirectory());
		assertEquals("http-user", configuration.getHttp().getUsername());
		assertArrayEquals("http-password".toCharArray(), configuration.getHttp().getPassword());
		assertEquals(8443, configuration.getHttp().getPort());

		assertNotNull(configuration.getSsh());
		assertEquals("ssh-recordings", configuration.getSsh().getDirectory());
		assertEquals("ssh-user", configuration.getSsh().getUsername());
		assertArrayEquals("ssh-password".toCharArray(), configuration.getSsh().getPassword());
		assertEquals(2222, configuration.getSsh().getPort());
	}

	@Test
	void testIsCorrespondingProtocol() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().build();

		assertTrue(configuration.isCorrespondingProtocol("http"));
		assertTrue(configuration.isCorrespondingProtocol("oscommand"));
		assertThrows(NullPointerException.class, () -> configuration.isCorrespondingProtocol(null));
		assertFalse(configuration.isCorrespondingProtocol(""));
	}
}
