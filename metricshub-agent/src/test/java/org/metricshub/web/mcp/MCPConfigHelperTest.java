package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
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
		Set<IConfiguration> result = MCPConfigHelper.resolveAllHostConfigurationCopiesFromContext(hostname, contextHolder);

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
		Set<IConfiguration> result = MCPConfigHelper.resolveAllHostConfigurationCopiesFromContext(hostname, contextHolder);

		// Then
		assertTrue(result.isEmpty());
	}

	@Test
	void testConvertConfigurationForProtocol_realHttpExtension() throws InvalidConfigurationException {
		HttpConfiguration sourceConfig = HttpConfiguration
			.builder()
			.username("admin")
			.password("pass123".toCharArray())
			.timeout(10L)
			.build();

		OsCommandExtension osCommandExtension = mock(OsCommandExtension.class);

		doCallRealMethod().when(osCommandExtension).buildConfiguration(eq("ssh"), any(JsonNode.class), any());
		IConfiguration result = MCPConfigHelper.convertConfigurationForProtocol(sourceConfig, "ssh", osCommandExtension);

		assertNotNull(result);
		assertTrue(result instanceof SshConfiguration);
		SshConfiguration sshResult = (SshConfiguration) result;
		assertEquals("admin", sshResult.getUsername());
		assertArrayEquals("pass123".toCharArray(), sshResult.getPassword());
	}

	@Test
	void testConvertConfigurationForProtocol_returnsNullIfInvalidConfig() throws InvalidConfigurationException {
		HttpConfiguration sourceConfig = HttpConfiguration.builder().build();

		OsCommandExtension osCommandExtension = mock(OsCommandExtension.class);
		doThrow(InvalidConfigurationException.class)
			.when(osCommandExtension)
			.buildConfiguration(eq("ssh"), any(JsonNode.class), any());

		IConfiguration result = MCPConfigHelper.convertConfigurationForProtocol(sourceConfig, "ssh", osCommandExtension);

		assertNull(result);
	}
}
