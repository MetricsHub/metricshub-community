package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.cli.service.protocol.HttpConfigCli.HttpOrHttps;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.mockito.Mock;
import org.mockito.MockedStatic;

class HttpConfigCliTest {

	@Mock
	HttpConfigCli httpConfigCli;

	@Test
	void testToProtocol() throws Exception {
		HttpConfigCli httpConfigCli = new HttpConfigCli();
		final char[] password = "p4ssw0rd".toCharArray();
		final String username = "username";
		final String timeout = "120";
		final HttpOrHttps httpOrHttps = new HttpOrHttps();
		httpConfigCli.setPassword(password);
		httpConfigCli.setUsername(username);
		httpConfigCli.setTimeout(timeout);
		httpConfigCli.setHttpOrHttps(httpOrHttps);

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new HttpExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			HttpConfiguration expected = HttpConfiguration
				.builder()
				.username(username)
				.password(password)
				.timeout(120L)
				.port(80)
				.https(false)
				.build();

			HttpConfiguration result = (HttpConfiguration) httpConfigCli.toConfiguration(null, null);

			assertNotNull(result);
			assertEquals(expected, result);

			httpConfigCli = new HttpConfigCli();
			httpConfigCli.setTimeout(timeout);
			httpConfigCli.setHttpOrHttps(httpOrHttps);

			result = (HttpConfiguration) httpConfigCli.toConfiguration(username, password);
			assertNotNull(result);
			assertEquals(expected, result);
		}
	}

	@Test
	void testGetOrDeducePortNumber() {
		final HttpConfigCli httpConfigCli = new HttpConfigCli();
		final int expectedPortNumber = 4443;
		httpConfigCli.setPort(expectedPortNumber);
		assertEquals(expectedPortNumber, httpConfigCli.getOrDeducePortNumber());
		httpConfigCli.setPort(null);
		httpConfigCli.httpOrHttps = new HttpOrHttps();
		httpConfigCli.httpOrHttps.http = false;
		httpConfigCli.httpOrHttps.https = true;
		assertEquals(443, httpConfigCli.getOrDeducePortNumber());

		httpConfigCli.httpOrHttps.http = true;
		httpConfigCli.httpOrHttps.https = false;
		assertEquals(80, httpConfigCli.getOrDeducePortNumber());
	}
}
