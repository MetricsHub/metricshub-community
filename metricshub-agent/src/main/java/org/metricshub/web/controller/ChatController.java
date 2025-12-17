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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.dto.chat.ChatErrorResponse;
import org.metricshub.web.dto.chat.ChatMessage;
import org.metricshub.web.dto.chat.ChatRequest;
import org.reactivestreams.Subscription;
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
			return sendImmediateError("OpenAI API key is not configured");
		}

		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		final var terminated = new AtomicBoolean(false);

		// Build conversation history
		final List<Message> messages = buildMessageHistory(request.history());
		messages.add(new UserMessage(request.message()));

		log.info("Starting chat stream for message: {}", request.message());

		final Flux<String> responseStream = chatClientOpenAi.prompt().messages(messages).stream().content();

		// Subscribe and keep a handle so we can cancel generation if client disconnects / times out
		final var disposable = responseStream
			.doOnSubscribe((Subscription sub) -> {
				log.info("Flux subscribed, starting to receive chunks");
				// Initial SSE "connected" event
				sendEventSafely(emitter, terminated, "connected", "");
			})
			.doOnNext(chunk -> sendEventSafely(emitter, terminated, "chunk", chunk))
			.doOnComplete(() -> {
				// Try to send "done", but if the client is already gone, don't escalate
				sendEventSafely(emitter, terminated, "done", "");
				completeSafely(emitter, terminated);
			})
			.doOnError((Throwable error) -> {
				log.error("Error during chat streaming", error);
				sendEventSafely(
					emitter,
					terminated,
					"error",
					new ChatErrorResponse("Failed to generate response: " + error.getMessage())
				);
				completeSafely(emitter, terminated);
			})
			.subscribe();

		// If the client disconnects or we time out, stop generating immediately
		emitter.onCompletion(() -> {
			log.info("SSE connection completed");
			disposable.dispose();
			terminated.set(true);
		});

		emitter.onTimeout(() -> {
			log.warn("SSE connection timed out");
			disposable.dispose();
			completeSafely(emitter, terminated);
		});

		emitter.onError((Throwable th) -> {
			// Most commonly triggered when client disconnects mid-stream.
			log.debug("SSE connection error (often client disconnect): {}", th.toString());
			disposable.dispose();
			completeSafely(emitter, terminated);
		});

		return emitter;
	}

	/**
	 * Sends an immediate error message via SSE and completes the emitter.
	 * @param message the error message to send
	 * @return the SseEmitter with the error sent
	 */
	private SseEmitter sendImmediateError(final String message) {
		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		try {
			emitter.send(SseEmitter.event().name("error").data(new ChatErrorResponse(message)));
		} catch (IOException e) {
			log.debug("Unable to send immediate SSE error (client disconnected): {}", e.getMessage());
		} finally {
			try {
				emitter.complete();
			} catch (Exception ignored) {
				// no-op
			}
		}
		return emitter;
	}

	/**
	 * Checks if the API key is configured properly.
	 * @return true if the API key is set and not equal to the unconfigured placeholder
	 */
	private boolean hasApiKey() {
		final String apiKey = chatConfig.getApiKey();
		return StringHelper.nonNullNonBlank(apiKey) && !apiKey.equals(UNCONFIGURED_API_KEY);
	}

	/**
	 * Sends an SSE event; if the client is already disconnected, this will throw IOException.
	 * We treat that as a normal termination and complete the emitter without "completeWithError".
	 */
	private static void sendEventSafely(
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final String eventName,
		final Object data
	) {
		if (terminated.get()) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(eventName).data(data));
		} catch (IOException e) {
			// Client disconnected / connection reset: normal in streaming UIs
			log.debug("Unable to send SSE event '{}' (client disconnected): {}", eventName, e.getMessage());
			completeSafely(emitter, terminated);
		} catch (Exception e) {
			// Emitter already completed
			terminated.set(true);
		}
	}

	/**
	 * Completes the SseEmitter safely, ensuring it's only done once.
	 * @param emitter    the SseEmitter to complete
	 * @param terminated atomic boolean tracking termination state
	 */
	private static void completeSafely(final SseEmitter emitter, final AtomicBoolean terminated) {
		if (!terminated.compareAndSet(false, true)) {
			return;
		}
		try {
			emitter.complete();
		} catch (Exception ignored) {
			// no-op
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
