package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemExtension;
import org.metricshub.web.AgentContextHolder;

class WbemProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private WbemProtocolCheckService wbemProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private WbemExtension wbemExtension;

	@BeforeEach
	void setUp() {
		wbemExtension = mock(WbemExtension.class);
		doReturn("wbem").when(wbemExtension).getIdentifier();
		doReturn(true).when(wbemExtension).isSupportedConfigurationType("wbem");
		doReturn(true).when(wbemExtension).isValidConfiguration(any(WbemConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(wbemExtension).checkProtocol(any());

		WbemConfiguration wbemConfig = WbemConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("admin")
			.password("secret".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WbemConfiguration.class, wbemConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(wbemExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		wbemProtocolCheckService = new WbemProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testWbemProtocolIsReachable() {
		ProtocolCheckResponse result = wbemProtocolCheckService.checkWbemProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testWbemProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(wbemExtension).checkProtocol(any());

		ProtocolCheckResponse result = wbemProtocolCheckService.checkWbemProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWbemProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(wbemExtension).checkProtocol(any());

		ProtocolCheckResponse result = wbemProtocolCheckService.checkWbemProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWbemExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		WbemProtocolCheckService missingExtService = new WbemProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkWbemProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("WBEM extension is not available", result.getErrorMessage());
	}
}
