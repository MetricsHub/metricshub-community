package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.metricshub.web.mcp.PingToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Tests for ChatClientOpenAiConfiguration.
 * Note: This test verifies that the configuration can be created when API key is present.
 * Actual ChatClient creation requires a valid OpenAI API key, so we test the structure only.
 */
@SpringJUnitConfig(classes = { ToolCallbackConfiguration.class, ChatClientConfigurationTest.TestConfig.class })
class ChatClientConfigurationTest {

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	@Test
	void testShouldCreateOpenAiClient() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey("sk-test-key");

		final ChatClientOpenAiConfiguration config = new ChatClientOpenAiConfiguration();

		// Execute
		var client = config.openAIClient(chatConfig);

		// Verify
		assertNotNull(client, "OpenAI client should not be null");
	}

	@Test
	void testShouldOpenAiTools() {
		// Setup
		final ChatClientOpenAiConfiguration config = new ChatClientOpenAiConfiguration();

		// Execute
		var tools = config.openAiTools(toolCallbackProvider);

		// Verify
		assertEquals(1, tools.size(), "Tools should contain one tool (PingToolService)");
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
