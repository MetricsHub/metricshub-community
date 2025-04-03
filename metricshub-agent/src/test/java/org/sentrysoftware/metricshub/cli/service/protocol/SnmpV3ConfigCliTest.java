package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.snmpv3.SnmpV3Configuration;
import org.metricshub.extension.snmpv3.SnmpV3Extension;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;

class SnmpV3ConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		final SnmpV3ConfigCli snmpV3ConfigCli = new SnmpV3ConfigCli();
		snmpV3ConfigCli.setPrivacy("AES");
		final int[] retryIntervals = { 20, 30, 50 };
		snmpV3ConfigCli.setRetryIntervals(retryIntervals);
		final char[] passwordPrivacy = "passwordPrivacy".toCharArray();
		snmpV3ConfigCli.setPrivacyPassword(passwordPrivacy);
		snmpV3ConfigCli.setAuthType("SHA");
		final String username = "testUser";
		snmpV3ConfigCli.setUsername(username);
		final char[] password = "password".toCharArray();
		snmpV3ConfigCli.setPassword(password);
		snmpV3ConfigCli.setContextName("testContext");
		snmpV3ConfigCli.setTimeout("60");
		snmpV3ConfigCli.setPort(161);

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new SnmpV3Extension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create an Snmpv3TestConfiguration and call method toProtocol
			final SnmpV3Configuration snmpV3Configuration = (SnmpV3Configuration) snmpV3ConfigCli.toConfiguration(
				username,
				password
			);

			assertNotNull(snmpV3Configuration);
			assertEquals(3, snmpV3Configuration.getIntVersion());
			assertEquals("SNMP V3 as testUser (AES-encrypted)", snmpV3Configuration.toString());
			assertEquals(SnmpV3Configuration.Privacy.AES, snmpV3Configuration.getPrivacy());
			assertArrayEquals("passwordPrivacy".toCharArray(), snmpV3Configuration.getPrivacyPassword());
			assertEquals(SnmpV3Configuration.AuthType.SHA, snmpV3Configuration.getAuthType());
			assertEquals(username, snmpV3Configuration.getUsername());
			assertArrayEquals("password".toCharArray(), snmpV3Configuration.getPassword());
			assertArrayEquals(retryIntervals, snmpV3Configuration.getRetryIntervals());
			assertEquals("testContext", snmpV3Configuration.getContextName());
		}
	}
}
