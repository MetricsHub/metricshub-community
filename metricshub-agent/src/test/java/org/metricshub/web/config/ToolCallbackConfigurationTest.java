package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.web.mcp.PingToolService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = { ToolCallbackConfiguration.class, ToolCallbackConfigurationTest.TestConfig.class })
class ToolCallbackConfigurationTest {

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	@Test
	void testShouldCreateToolCallbackProvider() {
		assertNotNull(toolCallbackProvider, "ToolCallbackProvider should not be null");
		final ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
		assertNotNull(toolCallbacks, "Tool callbacks should not be null");
		assertTrue(toolCallbacks.length > 0, "Tool callbacks should not be empty");
	}

	// Define a mock PingToolService for the context
	@Configuration
	static class TestConfig {

		@Bean
		public PingToolService pingToolService() {
			return mock(PingToolService.class);
		}
	}
}
