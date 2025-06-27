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
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiExtension;
import org.metricshub.web.AgentContextHolder;

class IpmiProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private IpmiProtocolCheckService ipmiProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private IpmiExtension ipmiExtension;

	@BeforeEach
	void setUp() {
		ipmiExtension = mock(IpmiExtension.class);
		doReturn("ipmi").when(ipmiExtension).getIdentifier();
		doReturn(true).when(ipmiExtension).isSupportedConfigurationType("ipmi");
		doReturn(true).when(ipmiExtension).isValidConfiguration(any(IpmiConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(ipmiExtension).checkProtocol(any());

		IpmiConfiguration ipmiConfig = IpmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("admin")
			.password("secret".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(ipmiExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		ipmiProtocolCheckService = new IpmiProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testIpmiProtocolIsReachable() {
		ProtocolCheckResponse result = ipmiProtocolCheckService.checkIpmiProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testIpmiProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(ipmiExtension).checkProtocol(any());

		ProtocolCheckResponse result = ipmiProtocolCheckService.checkIpmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testIpmiProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(ipmiExtension).checkProtocol(any());

		ProtocolCheckResponse result = ipmiProtocolCheckService.checkIpmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testIpmiExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		IpmiProtocolCheckService missingExtService = new IpmiProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkIpmiProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("IPMI extension is not available", result.getErrorMessage());
	}
}
