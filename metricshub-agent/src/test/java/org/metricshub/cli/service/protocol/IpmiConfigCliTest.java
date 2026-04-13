package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiExtension;
import org.mockito.MockedStatic;

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
			final IpmiConfiguration ipmiConfiguration = (IpmiConfiguration) ipmiConfigCli.toConfiguration(username, password);

			assertNotNull(ipmiConfiguration);

			final IpmiConfiguration ipmiConfigurationExpected = IpmiConfiguration
				.builder()
				.username(username)
				.password(password)
				.build();

			assertEquals(ipmiConfigurationExpected, ipmiConfigCli.toConfiguration(username, password));
		}
	}
}
