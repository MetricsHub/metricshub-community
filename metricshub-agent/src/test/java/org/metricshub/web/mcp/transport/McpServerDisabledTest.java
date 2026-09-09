package org.metricshub.web.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Disabling the MCP server (a supported {@code web} override) must still let the web application start: the legacy
 * SSE configuration depends on Spring AI beans that only exist when the MCP server is enabled, so it must back off.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.MOCK,
	classes = McpTransportTestSupport.TestApplication.class,
	properties = { "spring.main.banner-mode=off", "spring.ai.mcp.server.enabled=false", "mcp.sse.enabled=true" }
)
class McpServerDisabledTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void legacySseConfigurationShouldBackOffWhenTheMcpServerIsDisabled() {
		assertTrue(applicationContext.getBeansOfType(LegacySseMcpServer.class).isEmpty());
		assertTrue(applicationContext.getBeansOfType(LegacySseServerTransportProvider.class).isEmpty());
		assertTrue(applicationContext.getBeansOfType(McpServerTransportProviderBase.class).isEmpty());
	}
}
