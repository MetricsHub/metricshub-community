package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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

		final MultiHostToolResponse<ProtocolCheckResponse> responses = pingToolService.pingHost(
			List.of(hostname),
			3L,
			null
		);

		assertNotNull(responses, "Ping responses should not be null");
		assertTrue(responses.getErrorMessage() == null || responses.getErrorMessage().isEmpty(), "Error should be empty");
		assertEquals(1, responses.getHosts().size(), "Only one response should be returned");

		final HostToolResponse<ProtocolCheckResponse> responseWrapper = responses.getHosts().get(0);
		assertEquals(hostname, responseWrapper.getHostname(), "Hostname should match the requested hostname");

		final ProtocolCheckResponse response = responseWrapper.getResponse();
		assertNotNull(response, "Ping response should not be null");
		assertEquals(hostname, response.getHostname(), "Response should contain the requested hostname");
		assertTrue(response.isReachable(), "Host should be reachable");
		assertNull(response.getErrorMessage(), "Error message should be null for successful ping");
		assertTrue(response.getResponseTime() >= 0, "Duration should be non-negative");
	}

	@Test
	void testShouldReturnResponsesForMultipleHosts() {
		final List<String> hostnames = List.of("localhost", "127.0.0.1");

		final MultiHostToolResponse<ProtocolCheckResponse> responses = pingToolService.pingHost(hostnames, 3L, 5);

		assertNotNull(responses, "Ping responses should not be null");
		assertEquals(hostnames.size(), responses.getHosts().size(), "A response should be returned for each hostname");

		responses
			.getHosts()
			.forEach(responseWrapper -> {
				assertNotNull(responseWrapper.getResponse(), "Each response wrapper should contain a response");
				assertTrue(
					hostnames.contains(responseWrapper.getHostname()),
					"Hostname should be one of the requested hostnames"
				);
				assertEquals(
					responseWrapper.getHostname(),
					responseWrapper.getResponse().getHostname(),
					"Response should contain the matching hostname"
				);
				assertTrue(responseWrapper.getResponse().getResponseTime() >= 0, "Duration should be non-negative");
			});
	}
}
