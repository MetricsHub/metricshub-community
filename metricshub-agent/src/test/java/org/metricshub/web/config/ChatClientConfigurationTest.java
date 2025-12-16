package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Tests for ChatClientOpenAiConfiguration.
 * Note: This test verifies that the configuration can be created when API key is present.
 * Actual ChatClient creation requires a valid OpenAI API key, so we test the structure only.
 */
class ChatClientConfigurationTest {

	@Test
	void testShouldCreateChatModelWhenApiKeyPresent() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey("sk-test-key");
		chatConfig.setModel("gpt-4o-mini");

		final ToolCallbackProvider toolCallbackProvider = mock(ToolCallbackProvider.class);
		final ChatClientOpenAiConfiguration config = new ChatClientOpenAiConfiguration(chatConfig, toolCallbackProvider);

		// Execute
		final ChatModel chatModel = config.chatModelOpenAi();

		// Verify
		assertNotNull(chatModel, "ChatModel should not be null");
	}

	@Test
	void testShouldCreateChatClientWhenApiKeyPresent() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey("sk-test-key");
		chatConfig.setModel("gpt-4o-mini");

		final ToolCallbackProvider toolCallbackProvider = mock(ToolCallbackProvider.class);
		final ChatClientOpenAiConfiguration config = new ChatClientOpenAiConfiguration(chatConfig, toolCallbackProvider);

		final ChatModel chatModel = config.chatModelOpenAi();

		when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[] {});

		// Execute
		final ChatClient chatClient = config.chatClientOpenAi(chatModel);

		// Verify
		assertNotNull(chatClient, "ChatClient should not be null");
	}
}
