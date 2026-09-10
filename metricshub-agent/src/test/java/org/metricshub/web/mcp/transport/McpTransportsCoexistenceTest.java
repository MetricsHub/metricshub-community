package org.metricshub.web.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/**
 * Boots a minimal Spring Boot web application with the Spring AI MCP auto-configuration in {@code STREAMABLE} mode
 * plus {@link LegacySseMcpServerConfiguration}, and checks that both transports serve the same tools and that the
 * legacy SSE transport no longer hangs on uninitialized sessions.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = McpTransportTestSupport.TestApplication.class,
	properties = {
		"spring.main.banner-mode=off",
		"spring.ai.mcp.server.enabled=true",
		"spring.ai.mcp.server.stdio=false",
		"spring.ai.mcp.server.name=test-mcp-server",
		"spring.ai.mcp.server.type=sync",
		"spring.ai.mcp.server.protocol=STREAMABLE",
		"spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp",
		"spring.ai.mcp.server.sse-endpoint=/sse",
		"spring.ai.mcp.server.sse-message-endpoint=/mcp/message",
		"spring.ai.mcp.server.request-timeout=30s",
		// Fast keep-alive so that the ping round trip with a real client is exercised within the test
		"spring.ai.mcp.server.keep-alive-interval=200ms",
		"mcp.sse.enabled=true",
		"mcp.sse.message-timeout=30s",
		"mcp.sse.ping-timeout=5s"
	}
)
class McpTransportsCoexistenceTest {

	@LocalServerPort
	private int port;

	@Autowired
	private LegacySseMcpServer legacySseMcpServer;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void legacySseTransportShouldServeTheTools() {
		McpTransportTestSupport.assertToolsAreServed(
			HttpClientSseClientTransport.builder(baseUrl()).sseEndpoint("/sse").build()
		);
	}

	@Test
	void streamableHttpTransportShouldServeTheTools() {
		McpTransportTestSupport.assertToolsAreServed(
			HttpClientStreamableHttpTransport.builder(baseUrl()).endpoint("/mcp").build()
		);
	}

	@Test
	void legacySseTransportShouldRejectRequestsOnUninitializedSessionsInsteadOfHanging() throws Exception {
		McpTransportTestSupport.assertUninitializedSessionIsRejected(baseUrl());
	}

	@Test
	void legacySseSessionShouldSurviveKeepAlivePingsAnsweredByARealClient() throws Exception {
		McpTransportTestSupport.assertSessionSurvivesKeepAlivePings(
			HttpClientSseClientTransport.builder(baseUrl()).sseEndpoint("/sse").build(),
			legacySseMcpServer::activeSessionCount
		);
	}

	@Test
	void legacySseProviderShouldNotCompeteWithTheStreamableProviderBean() {
		assertNotNull(legacySseMcpServer);
		// The legacy provider is held by the holder bean only, so that Spring AI's single-provider injection stays
		// unambiguous and binds the auto-configured server to the Streamable HTTP transport
		assertTrue(applicationContext.getBeansOfType(LegacySseServerTransportProvider.class).isEmpty());
		final Map<String, McpServerTransportProviderBase> providers = applicationContext.getBeansOfType(
			McpServerTransportProviderBase.class
		);
		assertEquals(1, providers.size(), "Exactly one transport provider bean is expected: " + providers.keySet());
		assertTrue(providers.values().iterator().next() instanceof WebMvcStreamableServerTransportProvider);
	}

	private String baseUrl() {
		return "http://localhost:" + port;
	}
}
