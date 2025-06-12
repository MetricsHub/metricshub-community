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

		final PingResponse response = pingToolService.pingHost(hostname, 3L);

		assertNotNull(response, "Ping response should not be null");
		assertEquals(hostname, response.getHostname(), "Hostname should match the requested hostname");
		assertTrue(response.isReachable(), "Host should be reachable");
		assertNull(response.getErrorMessage(), "Error message should be null for successful ping");
		assertTrue(response.getDuration() >= 0, "Duration should be non-negative");
	}
}
