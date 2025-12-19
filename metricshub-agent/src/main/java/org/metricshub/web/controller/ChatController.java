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

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.web.config.ChatClientOpenAiConfiguration;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.dto.chat.ChatErrorResponse;
import org.metricshub.web.dto.chat.ChatMessage;
import org.metricshub.web.dto.chat.ChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(value = "/api")
@Slf4j
public class ChatController {

	private static final String UNCONFIGURED_API_KEY = "unconfigured";
	private static final long SSE_TIMEOUT_MS = 300_000L;

	private final OpenAIClient openAiClient;
	private final ChatOpenAiConfigurationProperties chatConfig;

	public ChatController(final OpenAIClient openAiClient, final ChatOpenAiConfigurationProperties chatConfig) {
		this.openAiClient = openAiClient;
		this.chatConfig = chatConfig;
	}

	@PostMapping(
		value = "/chat/stream",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public SseEmitter streamChat(@Valid @RequestBody final ChatRequest request) {
		if (!hasApiKey() || openAiClient == null) {
			return sendImmediateError("OpenAI API key is not configured");
		}

		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		final var terminated = new AtomicBoolean(false);
		final AtomicReference<StreamResponse<ResponseStreamEvent>> streamRef = new AtomicReference<>();

		log.info("Starting chat stream for message: {}", request.message());

		CompletableFuture.runAsync(() -> {
			sendEventSafely(emitter, terminated, "connected", "");

			final ResponseCreateParams params = ResponseCreateParams
				.builder()
				.model(chatConfig.getModel())
				.input(buildInput(request.history(), request.message()))
				.reasoning(Reasoning.builder().effort(ReasoningEffort.MEDIUM).summary(Reasoning.Summary.AUTO).build())
				.build();

			try (StreamResponse<ResponseStreamEvent> stream = openAiClient.responses().createStreaming(params)) {
				streamRef.set(stream);

				stream.stream().forEach((ResponseStreamEvent event) -> handleStreamEvent(emitter, terminated, event));

				// If the stream ends without emitting response.completed (rare), still finish gracefully.
				if (!terminated.get()) {
					sendEventSafely(emitter, terminated, "done", "");
					completeSafely(emitter, terminated);
				}
			} catch (Exception e) {
				log.error("Error during OpenAI streaming", e);
				sendEventSafely(
					emitter,
					terminated,
					"error",
					new ChatErrorResponse("Failed to generate response: " + e.getMessage())
				);
				completeSafely(emitter, terminated);
			} finally {
				streamRef.set(null);
			}
		});

		emitter.onCompletion(() -> {
			log.info("SSE connection completed");
			terminated.set(true);
			closeStreamQuietly(streamRef.get());
		});

		emitter.onTimeout(() -> {
			log.warn("SSE connection timed out");
			terminated.set(true);
			closeStreamQuietly(streamRef.get());
			completeSafely(emitter, terminated);
		});

		emitter.onError((Throwable th) -> {
			log.debug("SSE connection error (often client disconnect): {}", th.toString());
			terminated.set(true);
			closeStreamQuietly(streamRef.get());
			completeSafely(emitter, terminated);
		});

		return emitter;
	}

	private void handleStreamEvent(final SseEmitter emitter, final AtomicBoolean terminated, ResponseStreamEvent event) {
		if (terminated.get()) {
			return;
		}

		event
			.outputTextDelta()
			.ifPresent((ResponseTextDeltaEvent deltaEvent) -> {
				String delta = deltaEvent.delta();
				if (StringHelper.nonNullNonBlank(delta)) {
					sendEventSafely(emitter, terminated, "chunk", delta);
				}
			});

		event
			.reasoningTextDelta()
			.ifPresent((ResponseReasoningTextDeltaEvent deltaEvent) -> {
				String delta = deltaEvent.delta();
				if (StringHelper.nonNullNonBlank(delta)) {
					sendEventSafely(emitter, terminated, "reasoning", delta);
				}
			});

		event
			.reasoningSummaryTextDelta()
			.ifPresent((ResponseReasoningSummaryTextDeltaEvent deltaEvent) -> {
				String delta = deltaEvent.delta();
				if (StringHelper.nonNullNonBlank(delta)) {
					sendEventSafely(emitter, terminated, "reasoning_summary", delta);
				}
			});

		event
			.completed()
			.ifPresent((ResponseCompletedEvent done) -> {
				sendEventSafely(emitter, terminated, "done", "");
				completeSafely(emitter, terminated);
			});
	}

	private static ResponseCreateParams.Input buildInput(List<ChatMessage> history, String userMessage) {
		List<ResponseInputItem> items = new ArrayList<>();

		// system message
		items.add(
			ResponseInputItem.ofEasyInputMessage(
				EasyInputMessage
					.builder()
					.role(EasyInputMessage.Role.SYSTEM)
					.content(ChatClientOpenAiConfiguration.SYSTEM_PROMPT)
					.build()
			)
		);

		// history
		if (history != null) {
			for (ChatMessage m : history) {
				if (m == null || m.content() == null || m.content().isBlank()) {
					continue;
				}

				String role = m.role() == null ? "" : m.role().toLowerCase(Locale.getDefault());
				EasyInputMessage.Role r = "assistant".equals(role)
					? EasyInputMessage.Role.ASSISTANT
					: EasyInputMessage.Role.USER;

				items.add(
					ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder().role(r).content(m.content()).build())
				);
			}
		}

		// latest user message
		items.add(
			ResponseInputItem.ofEasyInputMessage(
				EasyInputMessage
					.builder()
					.role(EasyInputMessage.Role.USER)
					.content(userMessage == null ? "" : userMessage)
					.build()
			)
		);

		return ResponseCreateParams.Input.ofResponse(items);
	}

	private static void closeStreamQuietly(final StreamResponse<?> stream) {
		if (stream == null) {
			return;
		}
		try {
			stream.close();
		} catch (Exception ignored) {}
	}

	private SseEmitter sendImmediateError(final String message) {
		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		try {
			emitter.send(SseEmitter.event().name("error").data(new ChatErrorResponse(message)));
		} catch (IOException e) {
			log.debug("Unable to send immediate SSE error (client disconnected): {}", e.getMessage());
		} finally {
			try {
				emitter.complete();
			} catch (Exception ignored) {}
		}
		return emitter;
	}

	private boolean hasApiKey() {
		final String apiKey = chatConfig.getApiKey();
		return StringHelper.nonNullNonBlank(apiKey) && !apiKey.equals(UNCONFIGURED_API_KEY);
	}

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
			log.debug("Unable to send SSE event '{}' (client disconnected): {}", eventName, e.getMessage());
			completeSafely(emitter, terminated);
		} catch (Exception e) {
			terminated.set(true);
		}
	}

	private static void completeSafely(final SseEmitter emitter, final AtomicBoolean terminated) {
		if (!terminated.compareAndSet(false, true)) {
			return;
		}
		try {
			emitter.complete();
		} catch (Exception ignored) {}
	}
}
