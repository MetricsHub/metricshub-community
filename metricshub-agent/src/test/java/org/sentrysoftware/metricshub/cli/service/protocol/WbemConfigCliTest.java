package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemExtension;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;

class WbemConfigCliTest {

	@Test
	void testToProtocol() throws InvalidConfigurationException {
		final WbemConfigCli wbemConfigCli = new WbemConfigCli();
		final char[] password = "value".toCharArray();
		final String username = "username";
		final String namespace = "root/sentrysoftware";
		final String timeout = "120";
		final String vCenter = "vcenter";
		final int port = 5989;
		wbemConfigCli.setPassword(password);
		wbemConfigCli.setUsername(username);
		wbemConfigCli.setTimeout(timeout);
		wbemConfigCli.setNamespace(namespace);
		wbemConfigCli.setPort(port);
		wbemConfigCli.setProtocol("HTTPS");
		wbemConfigCli.setVcenter(vCenter);

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new WbemExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create a WbemTestConfiguration and call method toProtocol
			final WbemConfiguration wbemConfiguration = (WbemConfiguration) wbemConfigCli.toConfiguration(username, password);

			assertNotNull(wbemConfiguration);

			final WbemConfiguration wbemConfigurationExpected = WbemConfiguration
				.builder()
				.username(username)
				.password(password)
				.namespace(namespace)
				.port(port)
				.vCenter(vCenter)
				.build();

			assertEquals(wbemConfigurationExpected, wbemConfigCli.toConfiguration(username, password));
		}
	}

	@Test
	void testGetOrDeducePortNumber() {
		final WbemConfigCli wbemConfigCli = new WbemConfigCli();
		final int expectedPortNumber = 4443;
		wbemConfigCli.setPort(expectedPortNumber);
		assertEquals(expectedPortNumber, wbemConfigCli.getOrDeducePortNumber());
		wbemConfigCli.setPort(null);
		wbemConfigCli.setProtocol("HTTPS");
		assertEquals(5989, wbemConfigCli.getOrDeducePortNumber());
		wbemConfigCli.setProtocol("HTTP");
		assertEquals(5988, wbemConfigCli.getOrDeducePortNumber());
	}
}
