package org.metricshub.web.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/**
 * {@code mcp.sse.enabled=false} together with {@code protocol=SSE}: the switch must not hand {@code /sse} back to the
 * SDK transport that hangs request threads. The hardened transport stays in place (the switch only applies alongside
 * Streamable HTTP).
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
		"mcp.sse.enabled=false"
	}
)
class McpLegacySseDisabledInSseModeTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void hardenedTransportShouldStillReplaceTheSdkTransport() {
		final Map<String, McpServerTransportProvider> providers = applicationContext.getBeansOfType(
			McpServerTransportProvider.class
		);
		assertEquals(1, providers.size(), "Exactly one transport provider is expected: " + providers.keySet());
		assertTrue(providers.values().iterator().next() instanceof LegacySseServerTransportProvider);
		assertTrue(applicationContext.getBeansOfType(WebMvcSseServerTransportProvider.class).isEmpty());
	}

	@Test
	void legacySseTransportShouldStillRejectRequestsOnUninitializedSessions() throws Exception {
		McpTransportTestSupport.assertUninitializedSessionIsRejected("http://localhost:" + port);
	}
}
