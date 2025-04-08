package org.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.mockito.MockedStatic;

class SnmpConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		final String password = "pwd";

		// Create a SnmpConfigCli instance and set its data
		final SnmpConfigCli snmpConfigCli = new SnmpConfigCli();
		snmpConfigCli.setSnmpVersion("v1");
		snmpConfigCli.setPort(80);
		final char[] community = "community".toCharArray();
		snmpConfigCli.setCommunity(community);
		snmpConfigCli.setTimeout("120");

		try (MockedStatic<CliExtensionManager> cliExtensionManagerMock = mockStatic(CliExtensionManager.class)) {
			// Initialize the extension manager required by the agent context
			final ExtensionManager extensionManager = ExtensionManager
				.builder()
				.withProtocolExtensions(List.of(new SnmpExtension()))
				.build();

			cliExtensionManagerMock
				.when(() -> CliExtensionManager.getExtensionManagerSingleton())
				.thenReturn(extensionManager);

			// Create an SnmpTestConfiguration and call method toProtocol
			final SnmpConfiguration snmpConfiguration = (SnmpConfiguration) snmpConfigCli.toConfiguration(
				"user1",
				password.toCharArray()
			);

			// Check the resulting snmp configuration
			assertNotNull(snmpConfiguration);
			assertEquals(SnmpConfiguration.SnmpVersion.V1, snmpConfiguration.getVersion());
			assertEquals("SNMP v1 (community)", snmpConfiguration.toString());
		}
	}
}
