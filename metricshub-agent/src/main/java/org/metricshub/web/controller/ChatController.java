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
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.dto.chat.ChatErrorResponse;
import org.metricshub.web.dto.chat.ChatMessage;
import org.metricshub.web.dto.chat.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * Controller for handling chat AI requests.
 */
@RestController
@RequestMapping(value = "/api")
@Slf4j
public class ChatController {

	/**
	 * Placeholder value indicating the API key is not configured.
	 */
	private static final String UNCONFIGURED_API_KEY = "unconfigured";

	/**
	 * Timeout for SSE connections in milliseconds (5 minutes).
	 */
	private static final long SSE_TIMEOUT_MS = 300_000L;

	private final ChatClient chatClientOpenAi;
	private final ChatOpenAiConfigurationProperties chatConfig;

	/**
	 * Constructor for ChatController.
	 *
	 * @param chatClientOpenAi the ChatClient to use for chat operations (optional, may be null if API key not configured)
	 * @param chatConfig       the chat configuration properties
	 */
	public ChatController(final ChatClient chatClientOpenAi, final ChatOpenAiConfigurationProperties chatConfig) {
		this.chatClientOpenAi = chatClientOpenAi;
		this.chatConfig = chatConfig;
	}

	/**
	 * Streams chat responses using Server-Sent Events (SSE).
	 *
	 * @param request the chat request containing message and history
	 * @return SseEmitter for streaming responses
	 */
	@PostMapping(
		value = "/chat/stream",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public SseEmitter streamChat(@Valid @RequestBody final ChatRequest request) {
		// Validate API key is configured and ChatClient is available
		if (!hasApiKey() || chatClientOpenAi == null) {
			final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
			try {
				emitter.send(SseEmitter.event().name("error").data(new ChatErrorResponse("OpenAI API key is not configured")));
				emitter.complete();
			} catch (IOException e) {
				log.error("Failed to send error response", e);
				emitter.completeWithError(e);
			}
			return emitter;
		}

		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);

		// Send initial event to establish SSE connection
		try {
			emitter.send(SseEmitter.event().name("connected").data(""));
		} catch (IOException e) {
			log.error("Failed to send initial SSE event", e);
			emitter.completeWithError(e);
			return emitter;
		}

		// Build conversation history
		final List<Message> messages = buildMessageHistory(request.history());
		messages.add(new UserMessage(request.message()));

		// Stream the response - Flux.subscribe() is non-blocking
		log.info("Starting chat stream for message: {}", request.message());

		final Flux<String> responseStream = chatClientOpenAi.prompt().messages(messages).stream().content();

		// Subscribe directly - this is non-blocking and returns immediately
		responseStream
			.doOnSubscribe(subscription -> log.info("Flux subscribed, starting to receive chunks"))
			.doOnNext((String chunk) -> handleNextChunk(emitter, chunk))
			.doOnComplete(() -> handleComplete(emitter))
			.doOnError((Throwable error) -> handleError(emitter, error))
			.doOnCancel(() -> handleCancel(emitter))
			.subscribe();

		// Handle client disconnect
		emitter.onCompletion(() -> log.info("SSE connection completed"));
		emitter.onTimeout(() -> {
			log.warn("SSE connection timed out");
			emitter.complete();
		});
		emitter.onError((Throwable ex) -> log.error("SSE connection error", ex));

		return emitter;
	}

	/**
	 * Checks if the API key is not configured.
	 *
	 * @return true if the API key is missing or unconfigured, false otherwise
	 */
	private boolean hasApiKey() {
		final String apiKey = chatConfig.getApiKey();
		return StringHelper.nonNullNonBlank(apiKey) && !apiKey.equals(UNCONFIGURED_API_KEY);
	}

	/**
	 * Handles the next chunk of data from the chat stream.
	 * @param emitter the SseEmitter to send data to
	 * @param chunk   the chunk of data received
	 */
	private void handleNextChunk(final SseEmitter emitter, final String chunk) {
		try {
			log.debug("Received chunk: {}", chunk);
			emitter.send(SseEmitter.event().name("chunk").data(chunk));
		} catch (IOException e) {
			log.error("Failed to send chunk", e);
			emitter.completeWithError(e);
		}
	}

	/**
	 * Handles the completion of the chat stream.
	 * @param emitter the SseEmitter to complete
	 */
	private void handleComplete(final SseEmitter emitter) {
		try {
			log.info("Stream completed successfully");
			emitter.send(SseEmitter.event().name("done").data(""));
			emitter.complete();
		} catch (IOException e) {
			log.error("Failed to send completion event", e);
			emitter.completeWithError(e);
		}
	}

	/**
	 * Handles the cancellation of the chat stream.
	 * @param emitter the SseEmitter to complete with error
	 */
	private void handleCancel(final SseEmitter emitter) {
		log.info("Stream cancelled by client");
		emitter.completeWithError(new IOException("Client disconnected"));
	}

	/**
	 * Handles errors that occur during the chat stream.
	 * @param emitter the SseEmitter to send error to
	 * @param error   the error that occurred
	 */
	private void handleError(final SseEmitter emitter, Throwable error) {
		log.error("Error during chat streaming", error);
		try {
			emitter.send(
				SseEmitter
					.event()
					.name("error")
					.data(new ChatErrorResponse("Failed to generate response: " + error.getMessage()))
			);
			emitter.complete();
		} catch (IOException e) {
			log.error("Failed to send error event", e);
			emitter.completeWithError(e);
		}
	}

	/**
	 * Builds a list of Message objects from the chat history.
	 *
	 * @param history the chat history
	 * @return a list of Message objects
	 */
	private static List<Message> buildMessageHistory(final List<ChatMessage> history) {
		final List<Message> messages = new ArrayList<>();
		if (history != null) {
			for (final ChatMessage chatMessage : history) {
				if ("user".equalsIgnoreCase(chatMessage.role())) {
					messages.add(new UserMessage(chatMessage.content()));
				} else if ("assistant".equalsIgnoreCase(chatMessage.role())) {
					messages.add(new AssistantMessage(chatMessage.content()));
				} else {
					log.warn("Unknown message role: {}", chatMessage.role());
				}
			}
		}
		return messages;
	}
}
