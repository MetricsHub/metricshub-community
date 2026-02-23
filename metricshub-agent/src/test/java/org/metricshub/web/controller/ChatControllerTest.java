package org.metricshub.web.controller;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.config.ToolCallbackConfiguration;
import org.metricshub.web.dto.chat.ChatRequest;
import org.metricshub.web.mcp.PingToolService;
import org.metricshub.web.service.openai.ToolResponseManagerService;
import org.mockito.Mockito;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Tests for ChatController.
 */
@SpringJUnitConfig(classes = { ToolCallbackConfiguration.class, ChatControllerTest.TestConfig.class })
class ChatControllerTest {

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	@Test
	void testShouldReturnSseEmitterWhenApiKeyMissing() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey(""); // Empty API key
		final OpenAIClient client = mock(OpenAIClient.class);
		final ToolResponseManagerService toolResponseManagerService = mock(ToolResponseManagerService.class);
		final ObjectMapper objectMapper = new ObjectMapper();
		final ChatController controller = new ChatController(
			Optional.of(client),
			chatConfig,
			List.of(),
			toolCallbackProvider,
			toolResponseManagerService,
			objectMapper
		);

		final ChatRequest request = new ChatRequest("Hello", new ArrayList<>());

		// Execute
		final SseEmitter emitter = controller.stream(request);

		// Verify
		assertNotNull(emitter, "SseEmitter should not be null");
	}

	@Test
	void testShouldReturnSseEmitterWhenApiKeyConfigured() {
		// Setup config
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey("sk-test-key");
		chatConfig.setModel("gpt-5"); // or whatever default your props require

		// OpenAI client deep stubs
		final OpenAIClient client = mock(OpenAIClient.class, Mockito.RETURNS_DEEP_STUBS);

		// Mock StreamResponse + its stream() method
		@SuppressWarnings("unchecked")
		final StreamResponse<ResponseStreamEvent> streamResponse = mock(StreamResponse.class);

		// empty stream -> controller ends and sends "done"
		when(streamResponse.stream()).thenReturn(java.util.stream.Stream.empty());

		// Return our mocked stream response
		when(client.responses().createStreaming(any(ResponseCreateParams.class))).thenReturn(streamResponse);

		// Controller under test
		final List<Tool> tools = List.of();
		final ToolResponseManagerService toolResponseManagerService = mock(ToolResponseManagerService.class);
		final ObjectMapper objectMapper = new ObjectMapper();
		when(toolResponseManagerService.adaptToolOutput(any(), any())).thenAnswer(inv -> inv.getArgument(1));
		final ChatController controller = new ChatController(
			Optional.of(client),
			chatConfig,
			tools,
			toolCallbackProvider,
			toolResponseManagerService,
			objectMapper
		);

		final ChatRequest request = new ChatRequest("Hello", new ArrayList<>());

		// Execute
		final SseEmitter emitter = controller.stream(request);

		// Verify immediate return value
		assertNotNull(emitter, "SseEmitter should not be null");

		// Verify async call happened
		verify(client.responses(), timeout(1000)).createStreaming(any(ResponseCreateParams.class));

		// Verify the response stream was consumed and then closed by try-with-resources
		verify(streamResponse, timeout(1000)).stream();
		verify(streamResponse, timeout(1000)).close();
	}

	@Configuration
	static class TestConfig {

		@Bean
		public PingToolService pingToolService() {
			return mock(PingToolService.class);
		}
	}
}
