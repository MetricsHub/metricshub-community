package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.web.AgentContextHolder;

class MCPConfigHelperTest {

	@Test
	void testResolveAllHostConfigurationsFromContext_returnsMatchingConfigurations() {
		// Given
		String hostname = "my-host";

		HttpConfiguration httpConfig = HttpConfiguration
			.builder()
			.hostname(hostname)
			.username("username")
			.password("password".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(hostname)
			.configurations(Map.of(HttpConfiguration.class, httpConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		Map<String, Map<String, TelemetryManager>> telemetryManagers = Map.of("group", Map.of("host-id", telemetryManager));

		AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getTelemetryManagers()).thenReturn(telemetryManagers);

		AgentContextHolder contextHolder = mock(AgentContextHolder.class);
		when(contextHolder.getAgentContext()).thenReturn(agentContext);

		// When
		Set<IConfiguration> result = MCPConfigHelper.resolveAllHostConfigurationsFromContext(hostname, contextHolder);

		// Then
		assertEquals(1, result.size());
		assertTrue(result.contains(httpConfig));
	}

	@Test
	void testResolveAllHostConfigurationsFromContext_returnsEmptyIfNoMatch() {
		// Given
		String hostname = "non-existent-host";

		HttpConfiguration httpConfig = HttpConfiguration
			.builder()
			.hostname("different-host")
			.username("user")
			.password("pass".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("different-host")
			.configurations(Map.of(HttpConfiguration.class, httpConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		Map<String, Map<String, TelemetryManager>> telemetryManagers = Map.of("group", Map.of("host-id", telemetryManager));

		AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getTelemetryManagers()).thenReturn(telemetryManagers);

		AgentContextHolder contextHolder = mock(AgentContextHolder.class);
		when(contextHolder.getAgentContext()).thenReturn(agentContext);

		// When
		Set<IConfiguration> result = MCPConfigHelper.resolveAllHostConfigurationsFromContext(hostname, contextHolder);

		// Then
		assertTrue(result.isEmpty());
	}
}
