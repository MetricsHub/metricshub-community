package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.ping.PingExtension;
import org.metricshub.web.AgentContextHolder;

class PingToolServiceTest {

	private PingToolService pingToolService;

	@BeforeEach
	void setup() throws Exception {
		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(new PingExtension()))
			.build();

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		pingToolService = new PingToolService(agentContextHolder);
	}

	@Test
	void testShouldReturnSuccessfulPingResponse() {
		final String hostname = "localhost";

		final ProtocolCheckResponse response = pingToolService.pingHost(hostname, 3L);

		assertNotNull(response, "Ping response should not be null");
		assertEquals(hostname, response.getHostname(), "Hostname should match the requested hostname");
		assertTrue(response.isReachable(), "Host should be reachable");
		assertNull(response.getErrorMessage(), "Error message should be null for successful ping");
		assertTrue(response.getResponseTime() >= 0, "Duration should be non-negative");
	}

	@Test
	void testShouldReturnSuccessfulPingResponsesForMultipleHosts() {
		final List<String> hostnames = List.of("localhost", "127.0.0.1");

		final List<MultiHostToolResponse<ProtocolCheckResponse>> responses = pingToolService.pingHosts(hostnames, 3L);

		assertEquals(hostnames.size(), responses.size(), "Responses should be returned for each hostname");

		final Map<String, ProtocolCheckResponse> responseByHost = responses
			.stream()
			.collect(
				Collectors.toMap(
					MultiHostToolResponse::getHostname,
					MultiHostToolResponse::getResponse,
					(existing, replacement) -> existing
				)
			);

		hostnames.forEach(hostname -> {
			final ProtocolCheckResponse response = responseByHost.get(hostname);
			assertNotNull(response, () -> "Response should be present for host " + hostname);
			assertTrue(response.isReachable(), () -> "Host should be reachable: " + hostname);
			assertNull(response.getErrorMessage(), "Error message should be null for successful ping");
		});
	}
}
