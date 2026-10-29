package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for ChatClientOpenAiConfiguration.
 * Note: This test verifies that the configuration can be created when API key is present.
 * Actual ChatClient creation requires a valid OpenAI API key, so we test the structure only.
 */
class ChatClientConfigurationTest {

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
}
