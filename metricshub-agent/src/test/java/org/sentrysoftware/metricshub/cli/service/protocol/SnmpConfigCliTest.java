package org.sentrysoftware.metricshub.cli.service.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.extension.snmp.SnmpConfiguration;
import org.sentrysoftware.metricshub.extension.snmp.SnmpExtension;

class SnmpConfigCliTest {

	@Test
	void testToProtocol() throws Exception {
		final String password = "pwd";

		// Create a SnmpConfigCli instance and set its data
		final SnmpConfigCli snmpConfigCli = new SnmpConfigCli();
		snmpConfigCli.setSnmpVersion("v1");
		snmpConfigCli.setPort(80);
		snmpConfigCli.setCommunity("community");
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
			final SnmpConfiguration snmpConfiguration = (SnmpConfiguration) snmpConfigCli.toProtocol(
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
