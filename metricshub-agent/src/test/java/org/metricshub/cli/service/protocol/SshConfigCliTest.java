package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.mockito.MockedStatic;

class SshConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		SshConfigCli sshConfigCli = new SshConfigCli();
		final char[] password = "value".toCharArray();
		final String username = "username";

		sshConfigCli.setPassword(password);
		sshConfigCli.setUsername(username);
		sshConfigCli.setTimeout("120");
		sshConfigCli.setPort(22);
		sshConfigCli.setPrivateKey("privateKey");
		sshConfigCli.setSudoCommand("sudoCommand");
		sshConfigCli.setUseSudoCommands(Set.of("command1", "command2"));

		try (MockedStatic<CliExtensionManager> CliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new OsCommandExtension()))
				.build();

			CliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create an SshTestConfiguration and call method toProtocol
			final SshConfiguration sshConfiguration = (SshConfiguration) sshConfigCli.toConfiguration(username, password);

			// Check the resulting ssh configuration
			assertNotNull(sshConfiguration);
			assertEquals(username, sshConfiguration.getUsername());
			assertEquals(String.valueOf(password), String.valueOf(sshConfiguration.getPassword()));
		}
	}

	@Test
	void testPortNumber() {
		final SshConfigCli sshConfigCli = new SshConfigCli();
		final int expectedPortNumber = 2222;
		sshConfigCli.setPort(expectedPortNumber);
		assertEquals(expectedPortNumber, sshConfigCli.getPort());
		sshConfigCli.setPort(null);
		assertEquals(22, sshConfigCli.getPort());
	}
}
