package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.m8b.protocol.HostDescriptor;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.extension.wmi.WmiConfiguration;

class HostInventoryTest {

	@Test
	void shouldListSortedHostsWithProtocolsAndAttributes() {
		final AgentConfig agentConfig = AgentConfig.builder()
			.resourceGroups(
				Map.of(
					"paris",
					ResourceGroupConfig.builder()
						.resources(
							Map.of(
								"paris-host1",
								ResourceConfig.builder()
									.attributes(Map.of("host.name", "paris-host1.example.com", "host.type", "linux"))
									.protocols(
										Map.of(
											"ssh",
											SshConfiguration.sshConfigurationBuilder().hostname("paris-host1.example.com").build()
										)
									)
									.build()
							)
						)
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

		final List<HostDescriptor> hosts = HostInventory.from(agentConfig);

		assertEquals(List.of("paris-host1", "win-01"), hosts.stream().map(HostDescriptor::resourceKey).toList());

		final HostDescriptor paris = hosts.get(0);
		assertEquals("paris", paris.resourceGroup());
		assertEquals(Map.of("ssh", "paris-host1.example.com"), paris.hostnames());
		assertEquals("linux", paris.attributes().get("host.type"));

		final HostDescriptor win = hosts.get(1);
		assertEquals("metricshub-top-level-rg", win.resourceGroup());
		assertEquals(Map.of("wmi", "win-01.example.com"), win.hostnames());
	}

	@Test
	void shouldBeEmptyWithoutResources() {
		assertTrue(HostInventory.from(AgentConfig.builder().build()).isEmpty());
	}
}
