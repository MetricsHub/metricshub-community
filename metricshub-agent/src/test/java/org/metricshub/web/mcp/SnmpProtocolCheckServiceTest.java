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
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.web.AgentContextHolder;

class SnmpProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private SnmpProtocolCheckService snmpProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private SnmpExtension snmpExtension;

	@BeforeEach
	void setUp() {
		snmpExtension = mock(SnmpExtension.class);
		doReturn("snmp").when(snmpExtension).getIdentifier();
		doReturn(true).when(snmpExtension).isSupportedConfigurationType("snmp");
		doReturn(true).when(snmpExtension).isValidConfiguration(any(SnmpConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(snmpExtension).checkProtocol(any());

		// Mock configuration
		SnmpConfiguration snmpConfig = SnmpConfiguration
			.builder()
			.hostname(HOSTNAME)
			.version(SnmpVersion.V2C)
			.community("public".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(snmpExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		snmpProtocolCheckService = new SnmpProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testSnmpProtocolIsReachable() {
		ProtocolCheckResponse result = snmpProtocolCheckService.checkSnmpProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testSnmpProtocolIsUnreachable() {
		// Simulate unreachable host
		doReturn(Optional.of(Boolean.FALSE)).when(snmpExtension).checkProtocol(any());

		ProtocolCheckResponse result = snmpProtocolCheckService.checkSnmpProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSnmpProtocolCheckReturnsEmptyOptional() {
		// Simulate extension not returning a result
		doReturn(Optional.empty()).when(snmpExtension).checkProtocol(any());

		ProtocolCheckResponse result = snmpProtocolCheckService.checkSnmpProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSnmpExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		SnmpProtocolCheckService missingExtService = new SnmpProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkSnmpProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("SNMP extension is not available", result.getErrorMessage());
	}
}
