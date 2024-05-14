package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.extension.ipmi.IpmiConfiguration;
import org.sentrysoftware.metricshub.extension.ipmi.IpmiExtension;

class IpmiConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		IpmiConfigCli ipmiConfigCli = new IpmiConfigCli();
		final char[] password = "value".toCharArray();
		final String username = "username";
		final String timeout = "120";

		ipmiConfigCli.setPassword(password);
		ipmiConfigCli.setUsername(username);
		ipmiConfigCli.setTimeout(timeout);

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new IpmiExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create an IpmiTestConfiguration and call method toProtocol
			final IpmiConfiguration ipmiConfiguration = (IpmiConfiguration) ipmiConfigCli.toProtocol(
				username,
				password
			);

			assertNotNull(ipmiConfiguration);

			final IpmiConfiguration ipmiConfigurationExpected = IpmiConfiguration
				.builder()
				.username(username)
				.password(password)
				.build();

			assertEquals(ipmiConfigurationExpected, ipmiConfigCli.toProtocol(username, password));
		}
	}
}
