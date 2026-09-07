package org.metricshub.web.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/**
 * Boots the minimal web application with {@code spring.ai.mcp.server.protocol=SSE}: the hardened transport must
 * replace the Spring AI SSE transport provider and be bound to the auto-configured MCP server.
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
		"spring.ai.mcp.server.protocol=SSE",
		"spring.ai.mcp.server.sse-endpoint=/sse",
		"spring.ai.mcp.server.sse-message-endpoint=/mcp/message",
		"spring.ai.mcp.server.request-timeout=30s",
		"mcp.sse.enabled=true",
		"mcp.sse.message-timeout=30s"
	}
)
class McpLegacySseOnlyModeTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void hardenedTransportShouldReplaceTheSdkTransport() {
		final Map<String, McpServerTransportProvider> providers = applicationContext.getBeansOfType(
			McpServerTransportProvider.class
		);
		assertEquals(1, providers.size(), "Exactly one transport provider is expected: " + providers.keySet());
		assertTrue(providers.values().iterator().next() instanceof LegacySseServerTransportProvider);
		assertTrue(applicationContext.getBeansOfType(WebMvcSseServerTransportProvider.class).isEmpty());
		assertFalse(applicationContext.containsBean("legacySseMcpServer"));
	}

	@Test
	void legacySseTransportShouldServeTheTools() {
		McpTransportTestSupport.assertToolsAreServed(
			HttpClientSseClientTransport.builder(baseUrl()).sseEndpoint("/sse").build()
		);
	}

	@Test
	void legacySseTransportShouldRejectRequestsOnUninitializedSessionsInsteadOfHanging() throws Exception {
		McpTransportTestSupport.assertUninitializedSessionIsRejected(baseUrl());
	}

	@Test
	void streamableHttpEndpointShouldNotBeServed() throws Exception {
		assertEquals(404, McpTransportTestSupport.postJsonRpc(baseUrl() + "/mcp"));
	}

	private String baseUrl() {
		return "http://localhost:" + port;
	}
}
