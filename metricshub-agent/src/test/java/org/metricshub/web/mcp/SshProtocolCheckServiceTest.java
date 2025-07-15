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
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;

class SshProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private SshProtocolCheckService sshProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private OsCommandExtension sshExtension;

	@BeforeEach
	void setUp() {
		sshExtension = mock(OsCommandExtension.class);
		doReturn("ssh").when(sshExtension).getIdentifier();
		doReturn(true).when(sshExtension).isSupportedConfigurationType("ssh");
		doReturn(true).when(sshExtension).isValidConfiguration(any(SshConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(sshExtension).checkProtocol(any());

		SshConfiguration sshConfig = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username("root")
			.password("secret".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(sshExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		sshProtocolCheckService = new SshProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testSshProtocolIsReachable() {
		ProtocolCheckResponse result = sshProtocolCheckService.checkSshProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testSshProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(sshExtension).checkProtocol(any());

		ProtocolCheckResponse result = sshProtocolCheckService.checkSshProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSshProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(sshExtension).checkProtocol(any());

		ProtocolCheckResponse result = sshProtocolCheckService.checkSshProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testSshExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		SshProtocolCheckService missingExtService = new SshProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkSshProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("SSH extension is not available", result.getErrorMessage());
	}
}
