package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.jmx.JmxConfiguration;
import org.metricshub.extension.jmx.JmxExtension;
import org.mockito.MockedStatic;

class JmxConfigCliTest {

	@Test
	void testToConfiguration() throws Exception {
		final JmxConfigCli jmxConfigCli = new JmxConfigCli();
		jmxConfigCli.setUsername("user");
		final char[] password = "secret".toCharArray();
		jmxConfigCli.setPassword(password);
		jmxConfigCli.setTimeout("60");
		jmxConfigCli.setPort(9999);

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager with JMX protocol extension
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new JmxExtension()))
				.build();

			cliExtensionManagerMock.when(CliExtensionManager::getExtensionManagerSingleton).thenReturn(extensionManager);

			// Call the toProtocol method
			final JmxConfiguration jmxConfiguration = (JmxConfiguration) jmxConfigCli.toConfiguration(
				"default-user",
				"default-secret".toCharArray()
			);

			// Verify the configuration returned by toProtocol
			assertNotNull(jmxConfiguration, "JMX configuration should not be null");
			assertEquals("user", jmxConfiguration.getUsername(), "Username should match");
			assertEquals(60, jmxConfiguration.getTimeout(), "Timeout should be 60 seconds");
			assertEquals(9999, jmxConfiguration.getPort(), "Port should be 3306");
			assertEquals("secret", String.valueOf(jmxConfiguration.getPassword()), "Password should match");
		}
	}
}
