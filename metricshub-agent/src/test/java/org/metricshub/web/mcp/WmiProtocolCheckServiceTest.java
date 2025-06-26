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
import org.metricshub.extension.wmi.WmiConfiguration;
import org.metricshub.extension.wmi.WmiExtension;
import org.metricshub.web.AgentContextHolder;

class WmiProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private WmiProtocolCheckService wmiProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private WmiExtension wmiExtension;

	@BeforeEach
	void setUp() {
		wmiExtension = mock(WmiExtension.class);
		doReturn("wmi").when(wmiExtension).getIdentifier();
		doReturn(true).when(wmiExtension).isSupportedConfigurationType("wmi");
		doReturn(true).when(wmiExtension).isValidConfiguration(any(WmiConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(wmiExtension).checkProtocol(any());

		WmiConfiguration wmiConfig = WmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("wmi-user")
			.password("wmi-pass".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WmiConfiguration.class, wmiConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(wmiExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		wmiProtocolCheckService = new WmiProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testWmiProtocolIsReachable() {
		ProtocolCheckResponse result = wmiProtocolCheckService.checkWmiProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testWmiProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(wmiExtension).checkProtocol(any());

		ProtocolCheckResponse result = wmiProtocolCheckService.checkWmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWmiProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(wmiExtension).checkProtocol(any());

		ProtocolCheckResponse result = wmiProtocolCheckService.checkWmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testWmiExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		WmiProtocolCheckService missingExtService = new WmiProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkWmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("WMI extension is not available", result.getErrorMessage());
	}
}
