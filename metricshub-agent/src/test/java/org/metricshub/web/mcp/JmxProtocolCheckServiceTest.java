package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jmx.JmxConfiguration;
import org.metricshub.extension.jmx.JmxExtension;
import org.metricshub.web.AgentContextHolder;

class JmxProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private JmxProtocolCheckService jmxProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private JmxExtension jmxExtension;

	@BeforeEach
	void setUp() {
		jmxExtension = mock(JmxExtension.class);
		doReturn("jmx").when(jmxExtension).getIdentifier();
		doReturn(true).when(jmxExtension).isSupportedConfigurationType("jmx");
		doReturn(true).when(jmxExtension).isValidConfiguration(any(JmxConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(jmxExtension).checkProtocol(any());

		JmxConfiguration jmxConfig = JmxConfiguration
			.builder()
			.hostname(HOSTNAME)
			.port(9999)
			.username("monitorRole")
			.password("monitorPass".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(JmxConfiguration.class, jmxConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(jmxExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		jmxProtocolCheckService = new JmxProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testJmxProtocolIsReachable() {
		ProtocolCheckResponse result = jmxProtocolCheckService.checkJmxProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testJmxProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(jmxExtension).checkProtocol(any());

		ProtocolCheckResponse result = jmxProtocolCheckService.checkJmxProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testJmxProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(jmxExtension).checkProtocol(any());

		ProtocolCheckResponse result = jmxProtocolCheckService.checkJmxProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testJmxExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		JmxProtocolCheckService missingExtService = new JmxProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkJmxProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("JMX extension is not available", result.getErrorMessage());
	}
}
