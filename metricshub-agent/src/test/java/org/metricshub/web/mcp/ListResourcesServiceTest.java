package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.extension.wmi.WmiConfiguration;
import org.metricshub.web.AgentContextHolder;

class ListResourcesServiceTest {

	private ListResourcesService listResourcesService;

	@BeforeEach
	void setup() {
		final AgentContext agentContext = mock(AgentContext.class);
		final ExtensionManager extensionManager = ExtensionManager.empty();

		// Populate paris resource group resources
		final Map<String, ResourceConfig> parisResources = Map.of(
			"paris-host1",
			ResourceConfig
				.builder()
				.attributes(Map.of("host.name", "attrib-paris-host1", "host.type", "linux"))
				.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("paris-config-host1").build()))
				.build(),
			"paris-host2",
			ResourceConfig
				.builder()
				.attributes(Map.of("host.name", "attrib-paris-host2", "host.type", "windows"))
				.protocols(Map.of("wmi", WmiConfiguration.builder().hostname("paris-config-host2").build()))
				.build()
		);

		// Create paris resource group configuration
		final ResourceGroupConfig paris = ResourceGroupConfig.builder().resources(parisResources).build();

		// Populate top level resources
		final Map<String, ResourceConfig> topLevelResources = Map.of(
			"topLevel-host1",
			ResourceConfig
				.builder()
				.attributes(Map.of("host.name", "attrib-host1", "host.type", "linux"))
				.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("config-host1").build()))
				.build(),
			"topLevel-host2",
			ResourceConfig
				.builder()
				.attributes(Map.of("host.name", "attrib-host2", "host.type", "windows"))
				.protocols(Map.of("wmi", WmiConfiguration.builder().hostname("config-host2").build()))
				.build()
		);

		// Create an agent config with top level resources and resource groups configurations
		AgentConfig agentConfig = AgentConfig
			.builder()
			.resourceGroups(Map.of("paris", paris))
			.resources(topLevelResources)
			.build();
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getAgentConfig()).thenReturn(agentConfig);
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		listResourcesService = new ListResourcesService(agentContextHolder);
	}

	@Test
	void testListResources() {
		final Map<String, ResourceDetails> expectedResources = new HashMap<>();

		expectedResources.put(
			"paris-host1",
			new ResourceDetails(
				"paris",
				Set.of(new ProtocolHostname("ssh", "paris-config-host1")),
				Map.of("host.name", "attrib-paris-host1", "host.type", "linux")
			)
		);
		expectedResources.put(
			"paris-host2",
			new ResourceDetails(
				"paris",
				Set.of(new ProtocolHostname("wmi", "paris-config-host2")),
				Map.of("host.name", "attrib-paris-host2", "host.type", "windows")
			)
		);

		expectedResources.put(
			"topLevel-host1",
			new ResourceDetails(
				"metricshub-top-level-rg",
				Set.of(new ProtocolHostname("ssh", "config-host1")),
				Map.of("host.name", "attrib-host1", "host.type", "linux")
			)
		);
		expectedResources.put(
			"topLevel-host2",
			new ResourceDetails(
				"metricshub-top-level-rg",
				Set.of(new ProtocolHostname("wmi", "config-host2")),
				Map.of("host.name", "attrib-host2", "host.type", "windows")
			)
		);

		Map<String, ResourceDetails> result = listResourcesService.listConfiguredHosts();

		// Verify total number of resources
		assertEquals(expectedResources.size(), result.size());

		// Verify all expected resources
		expectedResources.forEach((key, expected) -> assertEquals(expected, result.get(key)));
	}
}
