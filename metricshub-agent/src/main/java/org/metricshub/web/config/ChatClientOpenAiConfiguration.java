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

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Tool;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.web.service.openai.SpringToolsToOpenAiTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for creating ChatClient with OpenAI and MetricsHub tools.
 */
@Configuration
@EnableConfigurationProperties(ChatOpenAiConfigurationProperties.class)
@RequiredArgsConstructor
public class ChatClientOpenAiConfiguration {

	/**
	 * System prompt defining the behavior and rules for the chat assistant.
	 */
	public static final String SYSTEM_PROMPT = ResourceHelper.getClassPathResourceContent("system-prompt.md");

	/**
	 * Creates a ChatClient bean configured with the ChatModel and tools.
	 *
	 * @param chatConfig the chat configuration properties
	 * @return the configured ChatClient
	 */
	@Bean
	public OpenAIClient openAIClient(ChatOpenAiConfigurationProperties chatConfig) {
		return OpenAIOkHttpClient.builder().apiKey(chatConfig.getApiKey()).build();
	}

	/**
	 * Creates a list of OpenAI tools from the provided ToolCallbackProvider.
	 * @param toolCallbackProvider the provider of tool callbacks
	 * @return the list of OpenAI tools
	 */
	@Bean
	public List<Tool> openAiTools(final ToolCallbackProvider toolCallbackProvider) {
		return SpringToolsToOpenAiTools.buildOpenAiTools(toolCallbackProvider.getToolCallbacks());
	}
}
