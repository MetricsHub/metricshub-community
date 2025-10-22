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
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.web.AgentContextHolder;

class ProtocolCheckServiceTest {

	private static final String HOSTNAME = "example-host";
	private static final Long TIMEOUT = 10L;

	private ProtocolCheckService protocolCheckService;
	private HttpExtension httpExtension;

	@BeforeEach
	void setUp() {
		httpExtension = mock(HttpExtension.class);
		doReturn("http").when(httpExtension).getIdentifier();
		doReturn(true).when(httpExtension).isSupportedConfigurationType(any());
		doReturn(true).when(httpExtension).isValidConfiguration(any(HttpConfiguration.class));
		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any());

		// Mock configuration
		HttpConfiguration httpConfig = HttpConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("user")
			.password("pass".toCharArray())
			.build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(HttpConfiguration.class, httpConfig))
			.build();

		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		AgentContext agentContext = mock(AgentContext.class);
		ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(httpExtension))
			.build();

		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("resourceGroupId", Map.of("host-id", telemetryManager)));

		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
	}

	@Test
	void testHttpProtocolIsReachable() {
		ProtocolCheckResponse result = checkProtocol(protocolCheckService, httpExtension.getIdentifier())
			.getHosts()
			.get(0)
			.getResponse();
		assertNotNull(result, "Result should not be null");
		assertTrue(result.isReachable(), "Protocol should be reachable");
		assertEquals(HOSTNAME, result.getHostname(), "Hostname should match");
	}

	@Test
	void testHttpProtocolIsUnreachable() {
		// Simulate unreachable host
		doReturn(Optional.of(Boolean.FALSE)).when(httpExtension).checkProtocol(any());

		ProtocolCheckResponse result = checkProtocol(protocolCheckService, httpExtension.getIdentifier())
			.getHosts()
			.get(0)
			.getResponse();

		assertNotNull(result, "Result should not be null");
		assertFalse(result.isReachable(), "Protocol should be unreachable");
		assertEquals(HOSTNAME, result.getHostname(), "Hostname should match");
		assertNull(result.getErrorMessage(), "Error message should be null for unreachable protocol");
	}

	@Test
	void testHttpProtocolCheckReturnsEmptyOptional() {
		// Simulate extension not returning a result
		doReturn(Optional.empty()).when(httpExtension).checkProtocol(any());

		ProtocolCheckResponse result = checkProtocol(protocolCheckService, httpExtension.getIdentifier())
			.getHosts()
			.get(0)
			.getResponse();

		assertNotNull(result, "Result should not be null");
		assertFalse(result.isReachable(), "Protocol should not be reachable");
		assertEquals(HOSTNAME, result.getHostname(), "Hostname should match");
		assertNull(result.getErrorMessage(), "Error message should be null for empty result");
	}

	@Test
	void testHttpExtensionIsMissing() {
		AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		AgentContext agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(java.util.List.of()).build());

		ProtocolCheckService missingExtService = new ProtocolCheckService(agentContextHolder);
		MultiHostToolResponse<ProtocolCheckResponse> result = checkProtocol(
			missingExtService,
			httpExtension.getIdentifier()
		);

		assertEquals("http extension is not available", result.getErrorMessage());
		assertTrue(result.getHosts().isEmpty(), "No host responses should be returned when extension missing");
	}

	private MultiHostToolResponse<ProtocolCheckResponse> checkProtocol(
		final ProtocolCheckService service,
		final String protocolIdentifier
	) {
		return service.checkProtocol(List.of(HOSTNAME), protocolIdentifier, TIMEOUT, null);
	}
}
