package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
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
		final EmulationConfiguration configuration = EmulationConfiguration.builder().hostname("my-host").build();

		assertNull(configuration.getProperty(null));
		assertNull(configuration.getProperty(""));
		assertNull(configuration.getProperty("badProperty"));
		assertEquals("my-host", configuration.getProperty("hostname"));
		assertEquals("my-host", configuration.getProperty("HOSTNAME"));
		assertEquals("my-host", configuration.getProperty("Hostname"));
	}

	@Test
	void testGetPropertyNullHostname() {
		final EmulationConfiguration configuration = EmulationConfiguration.builder().build();
		assertNull(configuration.getProperty("hostname"));
	}

	@Test
	void testGetPropertyFromOsCommandConfiguration() {
		final EmulationConfiguration configuration = EmulationConfiguration
			.builder()
			.oscommand(OsCommandConfiguration.builder().timeout(42L).build())
			.build();

		assertEquals("42", configuration.getProperty("timeout"));
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
