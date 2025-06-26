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
import org.metricshub.extension.snmpv3.SnmpV3Configuration;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.AuthType;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.Privacy;
import org.metricshub.extension.snmpv3.SnmpV3Extension;
import org.metricshub.web.AgentContextHolder;

class Snmpv3ProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private SnmpV3ProtocolCheckService snmpV3ProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private SnmpV3Extension snmpV3Extension;

	@BeforeEach
	void setUp() {
		snmpV3Extension = mock(SnmpV3Extension.class);
		doReturn("snmpv3").when(snmpV3Extension).getIdentifier();
		doReturn(true).when(snmpV3Extension).isSupportedConfigurationType("snmpv3");
		doReturn(true).when(snmpV3Extension).isValidConfiguration(any(SnmpV3Configuration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(snmpV3Extension).checkProtocol(any());

		SnmpV3Configuration snmpV3Config = SnmpV3Configuration
			.builder()
			.hostname(HOSTNAME)
			.authType(AuthType.MD5)
			.username("username")
			.password("password".toCharArray())
			.privacy(Privacy.AES)
			.privacyPassword("privacyPass".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpV3Configuration.class, snmpV3Config))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(snmpV3Extension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		snmpV3ProtocolCheckService = new SnmpV3ProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testSnmpv3ProtocolIsReachable() {
		ProtocolCheckResponse result = snmpV3ProtocolCheckService.checkSnmpV3Protocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testSnmpv3ProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(snmpV3Extension).checkProtocol(any());

		ProtocolCheckResponse result = snmpV3ProtocolCheckService.checkSnmpV3Protocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSnmpv3ProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(snmpV3Extension).checkProtocol(any());

		ProtocolCheckResponse result = snmpV3ProtocolCheckService.checkSnmpV3Protocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSnmpv3ExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		SnmpV3ProtocolCheckService missingExtService = new SnmpV3ProtocolCheckService(
			agentContextHolder,
			protocolCheckService
		);
		ProtocolCheckResponse result = missingExtService.checkSnmpV3Protocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("SNMPv3 extension is not available", result.getErrorMessage());
	}
}
