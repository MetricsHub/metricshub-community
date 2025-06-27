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
import org.metricshub.extension.winrm.WinRmConfiguration;
import org.metricshub.extension.winrm.WinRmExtension;
import org.metricshub.web.AgentContextHolder;

class WinrmProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private WinrmProtocolCheckService winRmProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private WinRmExtension winRmExtension;

	@BeforeEach
	void setUp() {
		winRmExtension = mock(WinRmExtension.class);
		doReturn("winrm").when(winRmExtension).getIdentifier();
		doReturn(true).when(winRmExtension).isSupportedConfigurationType("winrm");
		doReturn(true).when(winRmExtension).isValidConfiguration(any(WinRmConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(winRmExtension).checkProtocol(any());

		WinRmConfiguration winRmConfig = WinRmConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("administrator")
			.password("admin123".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WinRmConfiguration.class, winRmConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(winRmExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		winRmProtocolCheckService = new WinrmProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testWinRmProtocolIsReachable() {
		ProtocolCheckResponse result = winRmProtocolCheckService.checkWinrmProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testWinRmProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(winRmExtension).checkProtocol(any());

		ProtocolCheckResponse result = winRmProtocolCheckService.checkWinrmProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWinRmProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(winRmExtension).checkProtocol(any());

		ProtocolCheckResponse result = winRmProtocolCheckService.checkWinrmProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWinRmExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		WinrmProtocolCheckService missingExtService = new WinrmProtocolCheckService(
			agentContextHolder,
			protocolCheckService
		);
		ProtocolCheckResponse result = missingExtService.checkWinrmProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("WinRM extension is not available", result.getErrorMessage());
	}
}
