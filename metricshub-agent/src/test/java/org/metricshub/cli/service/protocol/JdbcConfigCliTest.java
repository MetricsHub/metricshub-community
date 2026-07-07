package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.connector.model.identity.DriverInfo;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.JdbcExtension;
import org.mockito.MockedStatic;

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
		jdbcConfigCli.setHostname("jdbc-host");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager with SQL protocol extension
			final ExtensionManager extensionManager = ExtensionManager.builder()
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
			assertEquals("jdbc-host", jdbcConfiguration.getHostname());
			assertEquals("testPassword", String.valueOf(jdbcConfiguration.getPassword()));
			assertNull(jdbcConfiguration.getDriver(), "driver should be null when no --jdbc-driver-class is provided");
		}
	}

	@Test
	void testToProtocolWithDriver() throws Exception {
		final JdbcConfigCli jdbcConfigCli = new JdbcConfigCli();
		jdbcConfigCli.setUrl("jdbc:as400://ibmi-01".toCharArray());
		jdbcConfigCli.setUsername("QSECOFR");
		jdbcConfigCli.setPassword("secret".toCharArray());
		jdbcConfigCli.setTimeout("60");
		jdbcConfigCli.setDatabase("ibmi-db");
		jdbcConfigCli.setType("DB2");
		jdbcConfigCli.setDriverClass("  com.ibm.as400.access.AS400JDBCDriver  ");
		jdbcConfigCli.setDriverJar("  $APP_DIR/drivers/jt400.jar  ");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			final ExtensionManager extensionManager = ExtensionManager.builder()
				.withProtocolExtensions(List.of(new JdbcExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			final JdbcConfiguration jdbcConfiguration = (JdbcConfiguration) jdbcConfigCli.toConfiguration(
				"defaultUser",
				"defaultPassword".toCharArray()
			);

			assertNotNull(jdbcConfiguration);
			final DriverInfo driver = jdbcConfiguration.getDriver();
			assertNotNull(driver, "driver should be populated when --jdbc-driver-class is provided");
			assertEquals("com.ibm.as400.access.AS400JDBCDriver", driver.getClassName());
			assertEquals("$APP_DIR/drivers/jt400.jar", driver.getJarPath());
		}
	}

	@Test
	void testToProtocolWithDriverClassOnly() throws Exception {
		final JdbcConfigCli jdbcConfigCli = new JdbcConfigCli();
		jdbcConfigCli.setUrl("jdbc:as400://ibmi-01".toCharArray());
		jdbcConfigCli.setUsername("QSECOFR");
		jdbcConfigCli.setPassword("secret".toCharArray());
		jdbcConfigCli.setTimeout("60");
		jdbcConfigCli.setDatabase("ibmi-db");
		jdbcConfigCli.setType("DB2");
		jdbcConfigCli.setDriverClass("com.ibm.as400.access.AS400JDBCDriver");
		// driverJar intentionally unset → should not appear in serialised driver block
		jdbcConfigCli.setDriverJar("   ");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			final ExtensionManager extensionManager = ExtensionManager.builder()
				.withProtocolExtensions(List.of(new JdbcExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			final JdbcConfiguration jdbcConfiguration = (JdbcConfiguration) jdbcConfigCli.toConfiguration(
				"defaultUser",
				"defaultPassword".toCharArray()
			);

			final DriverInfo driver = jdbcConfiguration.getDriver();
			assertNotNull(driver);
			assertEquals("com.ibm.as400.access.AS400JDBCDriver", driver.getClassName());
			assertNull(driver.getJarPath(), "jarPath should be null when --jdbc-driver-jar is blank");
		}
	}
}
