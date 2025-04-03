package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;

/**
 * Test of {@link SshConfiguration}
 */
class SshConfigurationTest {

	public static final String USERNAME = "testUser";
	public static final String PASSWORD = "testPassword";
	public static final String SSH_SUDO_COMMAND = "sudo pwd";
	public static final String SSH_CONFIGURATION_TO_STRING = "SSH as testUser";
	public static final String SSH = "SSH";
	public static final Long SSH_CONFIGURATION_TIMEOUT = 50L;

	@Test
	void testBuilder() {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.sudoCommand(SSH_SUDO_COMMAND)
			.timeout(SSH_CONFIGURATION_TIMEOUT)
			.useSudo(true)
			.build();
		assertEquals(USERNAME, sshConfiguration.getUsername());
		assertEquals(PASSWORD, new String(sshConfiguration.getPassword()));
		assertEquals(SSH_SUDO_COMMAND, sshConfiguration.getSudoCommand());
		assertEquals(SSH_CONFIGURATION_TIMEOUT, sshConfiguration.getTimeout());
		assertEquals(true, sshConfiguration.isUseSudo());
	}

	@Test
	void testToString() {
		final SshConfiguration sshConfiguration = new SshConfiguration();

		// When the userName is NOT null, it's appended to the result
		sshConfiguration.setUsername(USERNAME);
		sshConfiguration.setPassword(PASSWORD.toCharArray());
		sshConfiguration.setTimeout(SSH_CONFIGURATION_TIMEOUT);
		sshConfiguration.setPrivateKey(null);
		sshConfiguration.setSudoCommand("");
		assertEquals(SSH_CONFIGURATION_TO_STRING, sshConfiguration.toString());

		// When the userName is null, it's not appended to the result
		sshConfiguration.setUsername(null);
		assertEquals(SSH, sshConfiguration.toString());
	}

	@Test
	void testValidateConfiguration() {
		final String resourceKey = "resource";
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("username")
			.password("password".toCharArray())
			.timeout(120L)
			.privateKey("privateKey")
			.useSudoCommands(Set.of("sudo"))
			.useSudo(true)
			.sudoCommand("sudo")
			.timeout(120L)
			.build();

		assertDoesNotThrow(() -> sshConfiguration.validateConfiguration(resourceKey));

		sshConfiguration.setTimeout(null);
		assertThrows(InvalidConfigurationException.class, () -> sshConfiguration.validateConfiguration(resourceKey));

		sshConfiguration.setTimeout(-1L);
		assertThrows(InvalidConfigurationException.class, () -> sshConfiguration.validateConfiguration(resourceKey));

		sshConfiguration.setUsername(null);
		assertThrows(InvalidConfigurationException.class, () -> sshConfiguration.validateConfiguration(resourceKey));

		sshConfiguration.setUsername(" ");
		assertThrows(InvalidConfigurationException.class, () -> sshConfiguration.validateConfiguration(resourceKey));
	}

	@Test
	void testCopy() {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.password(PASSWORD.toCharArray())
			.port(100)
			.privateKey("privateKey")
			.sudoCommand(SSH_SUDO_COMMAND)
			.timeout(SSH_CONFIGURATION_TIMEOUT)
			.username(USERNAME)
			.useSudo(false)
			.useSudoCommands(Set.of("sudo"))
			.build();

		final SshConfiguration sshConfigurationCopy = sshConfiguration.copy();

		// Verify that the copied configuration has the same values as the original configuration
		assertEquals(sshConfiguration, sshConfigurationCopy);

		// Ensure that the copied configuration is a distinct object
		assert (sshConfiguration != sshConfigurationCopy);
		assert (sshConfiguration.getUseSudoCommands() != sshConfigurationCopy.getUseSudoCommands());
	}
}
