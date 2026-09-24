package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.m8b.protocol.HostDescriptor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.extension.wmi.WmiConfiguration;

class HostInventoryTest {

	private static final String TOP_LEVEL = "metricshub-top-level-rg";

	private static ResourceConfig ssh(final String hostname, final String hostType) {
		return ResourceConfig.builder()
			.attributes(Map.of("host.name", hostname, "host.type", hostType))
			.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname(hostname).build()))
			.build();
	}

	/**
	 * Builds a context whose active telemetry managers mirror the given (group -> resource keys).
	 */
	private static AgentContext context(final AgentConfig agentConfig, final Map<String, List<String>> active) {
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		active.forEach((group, keys) -> {
			final Map<String, TelemetryManager> managers = new HashMap<>();
			keys.forEach(key -> managers.put(key, mock(TelemetryManager.class)));
			telemetryManagers.put(group, managers);
		});
		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getAgentConfig()).thenReturn(agentConfig);
		when(agentContext.getTelemetryManagers()).thenReturn(telemetryManagers);
		return agentContext;
	}

	@Test
	void shouldListSortedHostsWithProtocolsAndAttributes() {
		final AgentConfig agentConfig = AgentConfig.builder()
			.resourceGroups(
				Map.of(
					"paris",
					ResourceGroupConfig.builder()
						.resources(Map.of("paris-host1", ssh("paris-host1.example.com", "linux")))
						.build()
				)
			)
			.resources(
				Map.of(
					"win-01",
					ResourceConfig.builder()
						.attributes(Map.of("host.name", "win-01", "host.type", "windows"))
						.protocols(Map.of("wmi", WmiConfiguration.builder().hostname("win-01.example.com").build()))
						.build()
				)
			)
			.build();

		final List<HostDescriptor> hosts = HostInventory.from(
			context(agentConfig, Map.of("paris", List.of("paris-host1"), TOP_LEVEL, List.of("win-01")))
		);

		assertEquals(
			List.of(TOP_LEVEL + "/win-01", "paris/paris-host1"),
			hosts
				.stream()
				.map(host -> host.resourceGroup() + "/" + host.resourceKey())
				.toList(),
			"Sorted by resource group then resource key"
		);

		final HostDescriptor win = hosts.get(0);
		assertEquals(Map.of("wmi", "win-01.example.com"), win.hostnames());
		assertEquals("windows", win.attributes().get("host.type"));

		final HostDescriptor paris = hosts.get(1);
		assertEquals(Map.of("ssh", "paris-host1.example.com"), paris.hostnames());
		assertEquals("linux", paris.attributes().get("host.type"));
	}

	@Test
	void shouldKeepHostsReusingAKeyAcrossGroups() {
		final AgentConfig agentConfig = AgentConfig.builder()
			.resourceGroups(
				Map.of(
					"paris",
					ResourceGroupConfig.builder().resources(Map.of("db-01", ssh("db-01.paris.example.com", "linux"))).build(),
					"london",
					ResourceGroupConfig.builder().resources(Map.of("db-01", ssh("db-01.london.example.com", "linux"))).build()
				)
			)
			.resources(Map.of("db-01", ssh("db-01.example.com", "linux")))
			.build();

		final List<HostDescriptor> hosts = HostInventory.from(
			context(agentConfig, Map.of("paris", List.of("db-01"), "london", List.of("db-01"), TOP_LEVEL, List.of("db-01")))
		);

		assertEquals(3, hosts.size(), "A resource key is scoped by its group: no host may be lost");
		assertEquals(List.of("london", TOP_LEVEL, "paris"), hosts.stream().map(HostDescriptor::resourceGroup).toList());
		assertEquals("db-01.london.example.com", hosts.get(0).hostnames().get("ssh"));
		assertEquals("db-01.example.com", hosts.get(1).hostnames().get("ssh"));
		assertEquals("db-01.paris.example.com", hosts.get(2).hostnames().get("ssh"));
	}

	@Test
	void shouldSkipConfiguredResourcesWithoutAnActiveTelemetryManager() {
		final AgentConfig agentConfig = AgentConfig.builder()
			.resources(Map.of("good", ssh("good.example.com", "linux"), "broken", ssh("broken.example.com", "linux")))
			.build();

		// "broken" failed validation: the agent runs no telemetry manager for it
		final List<HostDescriptor> hosts = HostInventory.from(context(agentConfig, Map.of(TOP_LEVEL, List.of("good"))));

		assertEquals(List.of("good"), hosts.stream().map(HostDescriptor::resourceKey).toList());
	}

	@Test
	void shouldBeEmptyWithoutResources() {
		assertTrue(HostInventory.from(context(AgentConfig.builder().build(), Map.of())).isEmpty());
		final AgentContext bare = mock(AgentContext.class);
		assertTrue(HostInventory.from(bare).isEmpty(), "A context without configuration reports no host");
	}
}
