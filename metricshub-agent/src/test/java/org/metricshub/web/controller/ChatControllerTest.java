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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.dto.chat.ChatRequest;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * Tests for ChatController.
 */
class ChatControllerTest {

	@Test
	void testShouldReturnSseEmitterWhenApiKeyMissing() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey(""); // Empty API key
		final ChatClient chatClient = mock(ChatClient.class);
		final ChatController controller = new ChatController(chatClient, chatConfig);

		final ChatRequest request = new ChatRequest("Hello", new ArrayList<>());

		// Execute
		final SseEmitter emitter = controller.streamChat(request);

		// Verify
		assertNotNull(emitter, "SseEmitter should not be null");
	}

	@Test
	void testShouldReturnSseEmitterWhenApiKeyConfigured() {
		// Setup
		final ChatOpenAiConfigurationProperties chatConfig = new ChatOpenAiConfigurationProperties();
		chatConfig.setApiKey("sk-test-key");

		// Deep-stub the fluent API: prompt().messages(...).stream().content()
		final ChatClient chatClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);

		when(chatClient.prompt().messages(anyList()).stream().content())
			.thenReturn(Flux.just("hello").concatWith(Flux.empty()));

		final ChatController controller = new ChatController(chatClient, chatConfig);
		final ChatRequest request = new ChatRequest("Hello", new ArrayList<>());

		// Execute
		final SseEmitter emitter = controller.streamChat(request);

		// Verify
		assertNotNull(emitter, "SseEmitter should not be null");
	}
}
