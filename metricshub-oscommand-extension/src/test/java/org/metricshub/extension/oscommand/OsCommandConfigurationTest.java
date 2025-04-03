package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;

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
}
