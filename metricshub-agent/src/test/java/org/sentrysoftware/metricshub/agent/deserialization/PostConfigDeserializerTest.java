package org.sentrysoftware.metricshub.agent.deserialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.sentrysoftware.metricshub.agent.config.AgentConfig;
import org.sentrysoftware.metricshub.agent.config.ResourceConfig;
import org.sentrysoftware.metricshub.agent.config.ResourceGroupConfig;
import org.sentrysoftware.metricshub.agent.context.AgentContext;
import org.sentrysoftware.metricshub.engine.common.helpers.JsonHelper;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.extension.oscommand.OsCommandExtension;
import org.sentrysoftware.metricshub.extension.oscommand.SshConfiguration;

class PostConfigDeserializerTest {

	// Initialize the extension manager required by ther deserializer
	final ExtensionManager extensionManager = ExtensionManager
		.builder()
		.withProtocolExtensions(List.of(new OsCommandExtension(), new SnmpExtension()))
		.build();

	@Test
	void testDeserialize() throws IOException {
		final ObjectMapper mapper = AgentContext.newAgentConfigObjectMapper(extensionManager);

		final AgentConfig configuration = JsonHelper.deserialize(
			mapper,
			mapper.readTree(new FileInputStream("src/test/resources/config/metricshub-multi-hosts.yaml")),
			AgentConfig.class
		);

		final Map<String, ResourceGroupConfig> resourceGroups = configuration.getResourceGroups();

		assertNotNull(resourceGroups);
		final ResourceGroupConfig sentryParisResourceGroup = resourceGroups.get("sentry-paris");

		assertNotNull(sentryParisResourceGroup);

		final Map<String, ResourceConfig> sentryParisResources = sentryParisResourceGroup.getResources();

		assertNotNull(sentryParisResources);

		assertSshResourceConfig(sentryParisResources, "ssh-resources1-1-server-1", "server-1", "server-1-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources1-2-server-2", "server-2", "server-2-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources2", "server-3", "server-3-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources3-1-server-4", "server-4", "server-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources3-2-server-5", "server-5", "server-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources4-1-server-6", "server-6", "server-6-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources4-2-server-7", "server-7", "server-7-id", null);
		assertSshResourceConfig(sentryParisResources, "ssh-resources4-3-server-7", "server-7", "server-8-id", null);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources5-1-server-8",
			"server-8",
			"server-8-id",
			"server-8-card"
		);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources5-2-server-9",
			"server-9",
			"server-9-id",
			"server-9-card"
		);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources6-1-server-10",
			"server-10",
			"server-10-id",
			"server-card"
		);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources6-2-server-11",
			"server-11",
			"server-11-id",
			"server-card"
		);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources7-1-server-12",
			"server-12",
			"server-12-id",
			"server-12-card"
		);
		assertSshResourceConfig(
			sentryParisResources,
			"ssh-resources7-2-server-13",
			"server-13",
			"server-13-id",
			"server-13-card"
		);

		final Map<String, ResourceConfig> resources = configuration.getResources();
		assertSnmpResourceConfig(
			resources,
			"snmp-resources-1-snmp-agent-1",
			"snmp-agent-1",
			"snmp-agent-1-id",
			"snmp-agent-1-card"
		);
		assertSnmpResourceConfig(
			resources,
			"snmp-resources-2-snmp-agent-2",
			"snmp-agent-2",
			"snmp-agent-2-id",
			"snmp-agent-2-card"
		);
	}

	/**
	 * Asserts the SSH resource configuration.
	 *
	 * @param sentryParisResources     The resource group from which the resources are extracted
	 * @param expectedResourceKey      The key of the resource to assert
	 * @param expectedHostName         The expected hostname
	 * @param expectedHostId           The expected host id
	 * @param expectedProtocolHostname The expected hostname for the protocol
	 */
	private void assertSshResourceConfig(
		final Map<String, ResourceConfig> sentryParisResources,
		String expectedResourceKey,
		String expectedHostName,
		String expectedHostId,
		String expectedProtocolHostname
	) {
		final ResourceConfig resourceConfig = sentryParisResources.get(expectedResourceKey);

		assertResourceConfigAttributes(expectedResourceKey, expectedHostName, expectedHostId, resourceConfig);
		final SshConfiguration sshProtocol = (SshConfiguration) resourceConfig.getProtocols().get("ssh");
		assertEquals("username", sshProtocol.getUsername(), "Unexpected protocol username for " + expectedResourceKey);
		assertArrayEquals(
			new char[] { 'p', 'a', 's', 's' },
			sshProtocol.getPassword(),
			"Unexpected protocol password for " + expectedResourceKey
		);
		assertEquals(
			expectedProtocolHostname,
			sshProtocol.getHostname(),
			"Unexpected protocol hostname for " + expectedResourceKey
		);
	}

	/**
	 * Asserts the SNMP resource configuration.
	 *
	 * @param sentryParisResources     The resource group from which the resources are extracted
	 * @param expectedResourceKey      The key of the resource to assert
	 * @param expectedHostName         The expected hostname
	 * @param expectedHostId           The expected host id
	 * @param expectedProtocolHostname The expected hostname for the protocol
	 */
	private void assertSnmpResourceConfig(
		final Map<String, ResourceConfig> sentryParisResources,
		String expectedResourceKey,
		String expectedHostName,
		String expectedHostId,
		String expectedProtocolHostname
	) {
		final ResourceConfig resourceConfig = sentryParisResources.get(expectedResourceKey);

		assertResourceConfigAttributes(expectedResourceKey, expectedHostName, expectedHostId, resourceConfig);
		final SnmpConfiguration snmpProtocol = (SnmpConfiguration) resourceConfig.getProtocols().get("snmp");
		assertEquals(
			SnmpConfiguration.SnmpVersion.V2C,
			snmpProtocol.getVersion(),
			"Unexpected protocol version for " + expectedResourceKey
		);
		assertEquals(
			expectedProtocolHostname,
			snmpProtocol.getHostname(),
			"Unexpected protocol hostname for " + expectedResourceKey
		);
	}

	/**
	 * Asserts the resource configuration attributes.
	 *
	 * @param expectedResourceKey The key of the resource to assert
	 * @param expectedHostName    The expected hostname
	 * @param expectedHostId      The expected host id
	 * @param resourceConfig      The resource configuration to assert
	 */
	private void assertResourceConfigAttributes(
		final String expectedResourceKey,
		final String expectedHostName,
		final String expectedHostId,
		final ResourceConfig resourceConfig
	) {
		assertNotNull(resourceConfig, "Resource not found: " + expectedResourceKey);
		final Map<String, String> resourceAttributes = resourceConfig.getAttributes();
		assertEquals(
			expectedHostName,
			resourceAttributes.get("host.name"),
			"Unexpected host.name attribute value " + expectedResourceKey
		);
		assertEquals(
			expectedHostId,
			resourceAttributes.get("host.id"),
			"Unexpected host.id attribute value " + expectedResourceKey
		);
		assertEquals(
			"storage",
			resourceAttributes.get("host.type"),
			"Unexpected host.type attribute value " + expectedResourceKey
		);
	}
}
