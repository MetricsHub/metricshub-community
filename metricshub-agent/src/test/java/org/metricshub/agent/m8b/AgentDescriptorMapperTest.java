package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.m8b.protocol.AgentDescriptor;

class AgentDescriptorMapperTest {

	private static AgentContext context(final Map<String, String> agentInfoAttributes, final AgentConfig agentConfig) {
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(agentInfoAttributes);
		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentContext.getAgentConfig()).thenReturn(agentConfig);
		return agentContext;
	}

	@Test
	void shouldDescribeCommunityAgent() {
		final AgentContext agentContext = context(
			Map.of(
				"service.name",
				"MetricsHub Agent",
				"version",
				"3.9.07",
				"host.name",
				"server-01",
				"os.type",
				"linux",
				"build_number",
				"abcdef12"
			),
			AgentConfig.builder().build()
		);

		final AgentDescriptor descriptor = AgentDescriptorMapper.map(agentContext);

		assertEquals("MetricsHub Agent", descriptor.name());
		assertEquals("3.9.07", descriptor.version());
		assertEquals("Community", descriptor.edition());
		assertEquals("server-01", descriptor.hostName());
		assertEquals("linux", descriptor.osType());
		assertEquals(System.getProperty("os.arch"), descriptor.arch());
		assertEquals("abcdef12", descriptor.buildNumber());
		assertEquals("3.9.07", descriptor.attributes().get("version"));
	}

	@Test
	void shouldReportEnterpriseEditionAndConfiguredAttributes() {
		final AgentContext agentContext = context(
			Map.of("service.name", "MetricsHub Enterprise Agent", "version", "3.9.07"),
			AgentConfig.builder()
				.attributes(Map.of("site", "paris", "service.version", "3.9.07-custom"))
				.opamp(OpAmpConfig.builder().attributes(Map.of("site", "data-center-1")).build())
				.build()
		);

		final AgentDescriptor descriptor = AgentDescriptorMapper.map(agentContext);

		assertEquals("Enterprise", descriptor.edition());
		assertEquals("3.9.07-custom", descriptor.version(), "An explicit service.version wins over the built-in version");
		assertEquals("data-center-1", descriptor.attributes().get("site"), "Fleet attributes are merged last");
		assertNull(descriptor.hostName());
	}

	@Test
	void shouldToleratemissingAgentInfo() {
		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getAgentConfig()).thenReturn(AgentConfig.builder().build());

		final AgentDescriptor descriptor = AgentDescriptorMapper.map(agentContext);

		assertNull(descriptor.name());
		assertEquals("Enterprise", descriptor.edition(), "Without a service name the agent is not the Community build");
		assertNotNull(descriptor.attributes());
	}
}
