package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.JdbcExtension;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;

class JdbcConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		final JdbcConfigCli jdbcConfigCli = new JdbcConfigCli();
		jdbcConfigCli.setUrl("jdbc:mysql://localhost:3306/mydatabase".toCharArray());
		jdbcConfigCli.setUsername("testUser");
		final char[] password = "testPassword".toCharArray();
		jdbcConfigCli.setPassword(password);
		jdbcConfigCli.setTimeout("60");
		jdbcConfigCli.setPort(3306);
		jdbcConfigCli.setDatabase("mydatabase");
		jdbcConfigCli.setType("MySQL");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager with SQL protocol extension
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new JdbcExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Call the toProtocol method
			final JdbcConfiguration jdbcConfiguration = (JdbcConfiguration) jdbcConfigCli.toConfiguration(
				"defaultUser",
				"defaultPassword".toCharArray()
			);

			// Verify the configuration returned by toProtocol
			assertNotNull(jdbcConfiguration);
			assertEquals("jdbc:mysql://localhost:3306/mydatabase", String.valueOf(jdbcConfiguration.getUrl()));
			assertEquals("testUser", jdbcConfiguration.getUsername());
			assertEquals(60, jdbcConfiguration.getTimeout());
			assertEquals(3306, jdbcConfiguration.getPort());
			assertEquals("mydatabase", jdbcConfiguration.getDatabase());
			assertEquals("MySQL", jdbcConfiguration.getType());
			assertEquals("testPassword", String.valueOf(jdbcConfiguration.getPassword()));
		}
	}
}
