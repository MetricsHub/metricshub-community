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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.List;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for creating ChatClient with OpenAI and MetricsHub tools.
 */
@Configuration
@EnableConfigurationProperties(ChatOpenAiConfigurationProperties.class)
public class ChatClientOpenAiConfiguration {

	/**
	 * System prompt defining the behavior and rules for the chat assistant.
	 */
	private static final String SYSTEM_PROMPT = ResourceHelper.getClassPathResourceContent("system-prompt.md");

	private final ChatOpenAiConfigurationProperties chatConfig;
	private final ToolCallbackProvider toolCallbackProvider;

	/**
	 * Constructor for ChatClientOpenAiConfiguration.
	 *
	 * @param chatConfig           the chat configuration properties
	 * @param toolCallbackProvider the tool callback provider from ToolCallbackConfiguration
	 */
	public ChatClientOpenAiConfiguration(
		final ChatOpenAiConfigurationProperties chatConfig,
		final ToolCallbackProvider toolCallbackProvider
	) {
		this.chatConfig = chatConfig;
		this.toolCallbackProvider = toolCallbackProvider;
	}

	/**
	 * Creates a ChatModel bean configured with OpenAI.
	 *
	 * @return the configured ChatModel
	 */
	@Bean
	@Primary
	public ChatModel chatModelOpenAi() {
		final var openAiApi = OpenAiApi.builder().apiKey(chatConfig.getApiKey()).build();

		// Ensure streaming is enabled in options
		final var options = OpenAiChatOptions.builder().model(chatConfig.getModel()).streamUsage(true).build();

		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
	}

	/**
	 * Creates a ChatClient bean configured with the ChatModel and tools.
	 *
	 * @param chatModelOpenAi the ChatModel to use (OpenAi)
	 * @return the configured ChatClient
	 */
	@Bean
	@Primary
	public ChatClient chatClientOpenAi(final ChatModel chatModelOpenAi) {
		final ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
		return ChatClient
			.builder(chatModelOpenAi)
			.defaultSystem(SYSTEM_PROMPT)
			.defaultToolCallbacks(List.of(toolCallbacks))
			.build();
	}
}
