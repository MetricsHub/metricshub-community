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
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.JdbcExtension;
import org.metricshub.web.AgentContextHolder;

class JdbcProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private JdbcProtocolCheckService jdbcProtocolCheckService;
	private ProtocolCheckService protocolCheckService;
	private JdbcExtension jdbcExtension;

	@BeforeEach
	void setUp() {
		jdbcExtension = mock(JdbcExtension.class);
		doReturn("jdbc").when(jdbcExtension).getIdentifier();
		doReturn(true).when(jdbcExtension).isSupportedConfigurationType("jdbc");
		doReturn(true).when(jdbcExtension).isValidConfiguration(any(JdbcConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(jdbcExtension).checkProtocol(any());

		JdbcConfiguration jdbcConfig = JdbcConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("root")
			.password("secret".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(JdbcConfiguration.class, jdbcConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(jdbcExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
		jdbcProtocolCheckService = new JdbcProtocolCheckService(agentContextHolder, protocolCheckService);
	}

	@Test
	void testJdbcProtocolIsReachable() {
		ProtocolCheckResponse result = jdbcProtocolCheckService.checkJdbcProtocol(HOSTNAME, TIMEOUT);
		assertNotNull(result);
		assertTrue(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
	}

	@Test
	void testJdbcProtocolIsUnreachable() {
		doReturn(Optional.of(Boolean.FALSE)).when(jdbcExtension).checkProtocol(any());

		ProtocolCheckResponse result = jdbcProtocolCheckService.checkJdbcProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testJdbcProtocolCheckReturnsEmptyOptional() {
		doReturn(Optional.empty()).when(jdbcExtension).checkProtocol(any());

		ProtocolCheckResponse result = jdbcProtocolCheckService.checkJdbcProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals(HOSTNAME, result.getHostname());
		assertNull(result.getErrorMessage());
	}

	@Test
	void testJdbcExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of()).build());

		JdbcProtocolCheckService missingExtService = new JdbcProtocolCheckService(agentContextHolder, protocolCheckService);
		ProtocolCheckResponse result = missingExtService.checkJdbcProtocol(HOSTNAME, TIMEOUT);

		assertNotNull(result);
		assertFalse(result.isReachable());
		assertEquals("JDBC extension is not available", result.getErrorMessage());
	}
}
