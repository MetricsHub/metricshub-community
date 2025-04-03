package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.extension.winrm.WinRmConfiguration;
import org.sentrysoftware.metricshub.extension.winrm.WinRmExtension;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

class WinRmConfigCliTest {

	@Test
	void testToProtocol() throws InvalidConfigurationException {
		// Create a WmiConfigCli instance and set its data
		WinRmConfigCli winRmConfigCli = new WinRmConfigCli();
		final String username = "username";
		final char[] password = "p4ssw0rd".toCharArray();
		final int port = 443;
		final String timeout = "120";
		final String namespace = "namespace";
		final List<String> authentications = List.of("kerberos");
		final TransportProtocols transportProtocolHttp = TransportProtocols.HTTP;

		winRmConfigCli.setUsername(username);
		winRmConfigCli.setPassword(password);
		winRmConfigCli.setPort(port);
		winRmConfigCli.setTimeout(timeout);
		winRmConfigCli.setNamespace(namespace);
		winRmConfigCli.setAuthentications(authentications);
		winRmConfigCli.setProtocol("HTTP");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new WinRmExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create a WinRmTestConfiguration and call method toProtocol
			WinRmConfiguration winRmConfiguration = (WinRmConfiguration) winRmConfigCli.toConfiguration(null, null);
			final WinRmConfiguration expected = WinRmConfiguration
				.builder()
				.username(username)
				.password(password)
				.port(port)
				.timeout(120L)
				.namespace(namespace)
				.authentications(List.of(AuthenticationEnum.KERBEROS))
				.protocol(transportProtocolHttp)
				.build();
			// Check the resulting WinRm configuration
			assertEquals(expected, winRmConfiguration);

			// Check null password and null username
			winRmConfigCli.setPassword(null);
			winRmConfigCli.setUsername(null);
			winRmConfiguration = (WinRmConfiguration) winRmConfigCli.toConfiguration(null, null);

			assertNull(winRmConfiguration.getPassword());
			assertNull(winRmConfiguration.getUsername());
		}
	}

	@Test
	void testGetOrDeducePortNumber() {
		final WinRmConfigCli winRmConfigCli = new WinRmConfigCli();
		final int expectedPortNumber = 4443;
		winRmConfigCli.setPort(expectedPortNumber);
		assertEquals(expectedPortNumber, winRmConfigCli.getOrDeducePortNumber());
		winRmConfigCli.setPort(null);
		winRmConfigCli.setProtocol("HTTPS");
		assertEquals(5986, winRmConfigCli.getOrDeducePortNumber());
		winRmConfigCli.setProtocol("HTTP");
		assertEquals(5985, winRmConfigCli.getOrDeducePortNumber());
	}

	@Test
	void testGetAuthentications() {
		final WinRmConfigCli winRmConfigCli = new WinRmConfigCli();
		assertNull(winRmConfigCli.getAuthentications());
		winRmConfigCli.setAuthentications(List.of());
		assertEquals(JsonNodeFactory.instance.arrayNode(), winRmConfigCli.getAuthentications());
		winRmConfigCli.setAuthentications(List.of("ntlm"));
		assertEquals(JsonNodeFactory.instance.arrayNode().add("ntlm"), winRmConfigCli.getAuthentications());
	}
}
