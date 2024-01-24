package org.sentrysoftware.metricshub.engine.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sentrysoftware.metricshub.engine.constants.Constants.PASSWORD;
import static org.sentrysoftware.metricshub.engine.constants.Constants.SSH;
import static org.sentrysoftware.metricshub.engine.constants.Constants.SSH_CONFIGURATION_TIMEOUT;
import static org.sentrysoftware.metricshub.engine.constants.Constants.SSH_CONFIGURATION_TO_STRING;
import static org.sentrysoftware.metricshub.engine.constants.Constants.SSH_SUDO_COMMAND;
import static org.sentrysoftware.metricshub.engine.constants.Constants.USERNAME;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link SshConfiguration}
 */
class SshConfigurationTest {

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
}
