package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;

/**
 * Test of {@link OsCommandConfiguration}
 */
class OsCommandConfigurationTest {

	public static final String OS_COMMAND_CONFIGURATION_TO_STRING = "Local Commands";

	@Test
	void testToString() {
		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		assertEquals(OS_COMMAND_CONFIGURATION_TO_STRING, osCommandConfiguration.toString());
	}

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resource";
		final OsCommandConfiguration osConfiguration = OsCommandConfiguration.builder().timeout(120L).build();

		assertDoesNotThrow(() -> osConfiguration.validateConfiguration(resourceKey));

		osConfiguration.setTimeout(null);
		assertThrows(InvalidConfigurationException.class, () -> osConfiguration.validateConfiguration(resourceKey));

		osConfiguration.setTimeout(-1L);
		assertThrows(InvalidConfigurationException.class, () -> osConfiguration.validateConfiguration(resourceKey));
	}

	@Test
	void testCopy() {
		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration
			.builder()
			.sudoCommand("sudoCommand")
			.timeout(100L)
			.useSudoCommands(Set.of("sudo"))
			.useSudo(true)
			.build();

		final OsCommandConfiguration osCommandConfigurationCopy = (OsCommandConfiguration) osCommandConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(osCommandConfiguration, osCommandConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (osCommandConfiguration != osCommandConfigurationCopy);
		assert (osCommandConfiguration.getUseSudoCommands() != osCommandConfigurationCopy.getUseSudoCommands());
	}

	@Test
	void testGetProperty() {
		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration
			.builder()
			.sudoCommand("mySudocommand")
			.useSudo(true)
			.timeout(100L)
			.hostname("myHostname")
			.build();

		assertNull(osCommandConfiguration.getProperty(null));
		assertNull(osCommandConfiguration.getProperty(""));
		assertNull(osCommandConfiguration.getProperty("badProperty"));

		assertEquals("mySudocommand", osCommandConfiguration.getProperty("sudocommand"));
		assertEquals("true", osCommandConfiguration.getProperty("usesudo"));
		assertEquals("100", osCommandConfiguration.getProperty("timeout"));
		assertEquals("myHostname", osCommandConfiguration.getProperty("hostname"));
	}

	@Test
	void testIsCorrespondingProtocol() {
		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		assertFalse(osCommandConfiguration.isCorrespondingProtocol(null));
		assertFalse(osCommandConfiguration.isCorrespondingProtocol(""));
		assertFalse(osCommandConfiguration.isCorrespondingProtocol("snmp"));
		assertTrue(osCommandConfiguration.isCorrespondingProtocol("OSCOMMAND"));
		assertTrue(osCommandConfiguration.isCorrespondingProtocol("oscommand"));
		assertTrue(osCommandConfiguration.isCorrespondingProtocol("OscoMMand"));
	}
}
