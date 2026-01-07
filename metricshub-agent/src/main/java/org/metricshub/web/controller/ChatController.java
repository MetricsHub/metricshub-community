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
import com.openai.core.JsonField;
import com.openai.core.http.StreamResponse;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseFunctionCallArgumentsDeltaEvent;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInProgressEvent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputItemAddedEvent;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.Tool;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.web.config.ChatClientOpenAiConfiguration;
import org.metricshub.web.config.ChatOpenAiConfigurationProperties;
import org.metricshub.web.dto.chat.ChatErrorResponse;
import org.metricshub.web.dto.chat.ChatMessage;
import org.metricshub.web.dto.chat.ChatRequest;
import org.metricshub.web.service.openai.ToolResponseManagerService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat controller handling chat requests with OpenAI integration.
 */
@RestController
@RequestMapping(value = "/api")
@Slf4j
@RequiredArgsConstructor
public class ChatController {

	/**
	 * Assistant role string constant.
	 */
	private static final String ASSISTANT_ROLE = "assistant";

	/**
	 * Function requested event code.
	 */
	private static final String FUNCTION_REQUESTED_EVENT_CODE = "function_call_requested";

	/**
	 * Reasoning summary event code.
	 */
	private static final String REASONING_SUMMARY_EVENT_CODE = "reasoning_summary";

	/**
	 * Reasoning event code.
	 */
	private static final String REASONING_EVENT_CODE = "reasoning";

	/**
	 * Chunk event code.
	 */
	private static final String CHUNK_EVENT_CODE = "chunk";

	/**
	 * Done event code.
	 */
	private static final String DONE_EVENT_CODE = "done";

	/**
	 * Connected event code.
	 */
	private static final String CONNECTED_EVENT_CODE = "connected";

	/**
	 * Error event code.
	 */
	private static final String ERROR_EVENT_CODE = "error";

	/**
	 * String constant for unconfigured API key.
	 */
	private static final String UNCONFIGURED_API_KEY = "unconfigured";

	/**
	 * SSE timeout in milliseconds (5 minutes).
	 */
	private static final long SSE_TIMEOUT_MS = 300_000L;

	/**
	 * OpenAI client for making API calls.
	 */
	private final OpenAIClient openAIClient;

	/**
	 * Chat configuration properties.
	 */
	private final ChatOpenAiConfigurationProperties chatConfig;

	/**
	 * List of tools available for the chat model.
	 */
	private final List<Tool> tools;

	/**
	 * Tool callback provider for executing tool functions.
	 */
	private final ToolCallbackProvider toolCallbackProvider;

	/**
	 * Adapts telemetry tool outputs to stay within OpenAI limits.
	 */
	private final ToolResponseManagerService toolResponseManagerService;

	/**
	 * Handles streaming chat requests.
	 *
	 * @param request the chat request
	 * @return an SseEmitter for streaming responses
	 */
	@PostMapping(
		value = "/chat/stream",
		consumes = MediaType.APPLICATION_JSON_VALUE,
		produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public SseEmitter stream(@Valid @RequestBody final ChatRequest request) {
		if (!hasApiKey() || openAIClient == null) {
			return sendImmediateError("OpenAI API key is not configured");
		}

		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		final var terminated = new AtomicBoolean(false);
		final AtomicReference<StreamResponse<ResponseStreamEvent>> streamRef = new AtomicReference<>();

		log.info("Starting chat stream for message: {}", request.message());

		CompletableFuture.runAsync(() -> startStream(request, emitter, terminated, streamRef));

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

	/**
	 * Start the chat stream processing.
	 *
	 * @param request    The chat request from the user.
	 * @param emitter    The SseEmitter to send events to the client.
	 * @param terminated AtomicBoolean indicating if the stream has been terminated.
	 * @param streamRef  AtomicReference to hold the current StreamResponse for cleanup.
	 */
	private void startStream(
		final ChatRequest request,
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final AtomicReference<StreamResponse<ResponseStreamEvent>> streamRef
	) {
		// First event: connected
		sendEventSafely(emitter, terminated, CONNECTED_EVENT_CODE, "");

		// Build initial input (history + user message)
		final List<ResponseInputItem> initialInputs = buildInputItems(request.history(), request.message());

		// Build initial response parameters using
		// - OpenAI model from config
		// - Input items (history + user message)
		// - Medium reasoning effort with auto summary
		// - Available tools exposed to the model
		// - Store the response
		final ResponseCreateParams initialParams = ResponseCreateParams
			.builder()
			.store(true)
			.model(chatConfig.getModel())
			.input(ResponseCreateParams.Input.ofResponse(initialInputs))
			.reasoning(Reasoning.builder().effort(ReasoningEffort.MEDIUM).summary(Reasoning.Summary.AUTO).build())
			.tools(tools)
			.build();

		try {
			// Run the tool execution loop
			streamResponses(emitter, terminated, streamRef, initialParams);

			// If not already terminated, send done event and terminate stream safely
			if (!terminated.get()) {
				sendEventSafely(emitter, terminated, DONE_EVENT_CODE, "");
				completeSafely(emitter, terminated);
			}
		} catch (Exception e) {
			log.error("Error during OpenAI streaming", e);
			// Send error event to client
			sendEventSafely(
				emitter,
				terminated,
				ERROR_EVENT_CODE,
				new ChatErrorResponse("Failed to generate response: " + e.getMessage())
			);
			// Terminate stream safely
			completeSafely(emitter, terminated);
		} finally {
			// Cleanup the stream reference
			streamRef.set(null);
		}
	}

	/**
	 * Run the loop to stream responses and handle tool calls.
	 *
	 * @param emitter       The SseEmitter to send events to the client.
	 * @param terminated    AtomicBoolean indicating if the stream has been terminated.
	 * @param streamRef     AtomicReference to hold the current StreamResponse for cleanup.
	 * @param initialParams The initial response creation parameters.
	 */
	private void streamResponses(
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final AtomicReference<StreamResponse<ResponseStreamEvent>> streamRef,
		final ResponseCreateParams initialParams
	) {
		// Current response creation parameters
		ResponseCreateParams currentParams = initialParams;

		while (!terminated.get()) {
			final Map<Long, PendingFunctionCall> pendingCalls = new ConcurrentHashMap<>();
			final List<ToolCallData> toolCalls = new ArrayList<>();
			final AtomicReference<String> currentResponseId = new AtomicReference<>();
			final AtomicReference<String> completedResponseId = new AtomicReference<>();

			try (StreamResponse<ResponseStreamEvent> stream = openAIClient.responses().createStreaming(currentParams)) {
				streamRef.set(stream);

				stream
					.stream()
					.forEach((ResponseStreamEvent event) ->
						handleStreamEvent(
							emitter,
							terminated,
							pendingCalls,
							toolCalls,
							currentResponseId,
							completedResponseId,
							event
						)
					);
			}

			// If terminated or no tool calls, exit loop
			if (terminated.get() || toolCalls.isEmpty()) {
				return;
			}

			final String prevId;
			if (StringHelper.nonNullNonBlank(completedResponseId.get())) {
				prevId = completedResponseId.get();
			} else {
				prevId = currentResponseId.get();
			}

			if (!StringHelper.nonNullNonBlank(prevId)) {
				sendEventSafely(
					emitter,
					terminated,
					ERROR_EVENT_CODE,
					new ChatErrorResponse("Cannot run tool follow-up: missing previous_response_id.")
				);
				completeSafely(emitter, terminated);
				return;
			}

			final List<ResponseInputItem> followUpInputs;
			try {
				followUpInputs = callFunctions(emitter, terminated, toolCalls);
			} catch (Exception ex) {
				log.error("Error during tool function calls", ex);
				return;
			}

			currentParams =
				ResponseCreateParams
					.builder()
					.store(true)
					.model(chatConfig.getModel())
					.previousResponseId(prevId)
					.input(ResponseCreateParams.Input.ofResponse(followUpInputs))
					.reasoning(Reasoning.builder().effort(ReasoningEffort.MEDIUM).summary(Reasoning.Summary.AUTO).build())
					.tools(tools)
					.build();
		}
	}

	/**
	 * Call the functions (tools) as per the tool calls data.
	 *
	 * @param emitter    SseEmitter to send events to the client
	 * @param terminated AtomicBoolean indicating if the stream has been terminated
	 * @param toolCalls  List of ToolCallData representing the tool calls to execute
	 * @return List of ResponseInputItem representing the function call outputs
	 */
	private List<ResponseInputItem> callFunctions(
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final List<ToolCallData> toolCalls
	) {
		final List<ResponseInputItem> followUpInputs = new ArrayList<>();
		for (ToolCallData tc : toolCalls) {
			final var toolName = tc.getToolName();
			final var callback = findToolCallback(toolName);
			if (callback == null) {
				sendEventSafely(
					emitter,
					terminated,
					ERROR_EVENT_CODE,
					new ChatErrorResponse("No ToolCallback found for tool name: " + toolName)
				);
				completeSafely(emitter, terminated);
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}

			final String toolResultJson;
			try {
				toolResultJson = callback.call(tc.getArgsJson());
			} catch (Exception ex) {
				log.warn("Tool {} execution failed: {}", toolName, ex.getMessage(), ex);
				sendEventSafely(
					emitter,
					terminated,
					ERROR_EVENT_CODE,
					new ChatErrorResponse("Tool " + toolName + " execution failed: " + ex.getMessage())
				);
				completeSafely(emitter, terminated);
				throw new IllegalStateException("Tool " + toolName + " execution failed: " + ex.getMessage(), ex);
			}

			final String adaptedToolResultJson = toolResponseManagerService.adaptTelemetryToolOutputOrManifest(
				toolName,
				toolResultJson
			);

			followUpInputs.add(
				ResponseInputItem.ofFunctionCallOutput(
					ResponseInputItem.FunctionCallOutput
						.builder()
						.status(ResponseInputItem.FunctionCallOutput.Status.COMPLETED)
						.callId(tc.getCallId())
						.outputAsJson(JsonField.of(adaptedToolResultJson))
						.build()
				)
			);
		}

		return followUpInputs;
	}

	/**
	 * Handle a single stream event from OpenAI.
	 *
	 * @param emitter             The SseEmitter to send events to the client.
	 * @param terminated          AtomicBoolean indicating if the stream has been terminated.
	 * @param pendingCalls        Map of pending function calls.
	 * @param toolCalls           List to store tool call data.
	 * @param currentResponseId   AtomicReference to store the current response ID.
	 * @param completedResponseId AtomicReference to store the completed response ID.
	 * @param event               The ResponseStreamEvent to handle.
	 */
	private void handleStreamEvent(
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final Map<Long, PendingFunctionCall> pendingCalls,
		final List<ToolCallData> toolCalls,
		final AtomicReference<String> currentResponseId,
		final AtomicReference<String> completedResponseId,
		final ResponseStreamEvent event
	) {
		if (terminated.get()) {
			return;
		}

		// Handle ResponseInProgressEvent to store current response ID
		event
			.inProgress()
			.ifPresent((ResponseInProgressEvent inProgress) ->
				storeResponseId(currentResponseId, () -> inProgress.response().id())
			);

		// Handle ResponseCreatedEvent to store current response ID
		event
			.created()
			.ifPresent((ResponseCreatedEvent created) -> storeResponseId(currentResponseId, () -> created.response().id()));

		// Handle text delta events
		event
			.outputTextDelta()
			.ifPresent((ResponseTextDeltaEvent deltaEvent) ->
				sendEventSafely(emitter, terminated, CHUNK_EVENT_CODE, deltaEvent.delta())
			);

		// Handle output item added events with function calls
		event
			.outputItemAdded()
			.ifPresent((ResponseOutputItemAddedEvent added) ->
				handleAddedEventWithFunctionCall(emitter, terminated, pendingCalls, added)
			);

		// Handle function call arguments delta events
		event
			.functionCallArgumentsDelta()
			.ifPresent((ResponseFunctionCallArgumentsDeltaEvent deltaEvent) ->
				appendFunctionArgsDelta(pendingCalls, deltaEvent)
			);

		// Handle output item done events for function call
		event
			.outputItemDone()
			.ifPresent((ResponseOutputItemDoneEvent doneEvent) ->
				handleOutputItemDoneForFunctionCall(pendingCalls, toolCalls, doneEvent)
			);

		// Handle reasoning text delta events
		event
			.reasoningTextDelta()
			.ifPresent((ResponseReasoningTextDeltaEvent deltaEvent) ->
				sendEventSafely(emitter, terminated, REASONING_EVENT_CODE, deltaEvent.delta())
			);

		// Handle reasoning summary text delta events
		event
			.reasoningSummaryTextDelta()
			.ifPresent((ResponseReasoningSummaryTextDeltaEvent deltaEvent) ->
				sendEventSafely(emitter, terminated, REASONING_SUMMARY_EVENT_CODE, deltaEvent.delta())
			);

		// Handle completed event to store completed response ID
		event
			.completed()
			.ifPresent((ResponseCompletedEvent done) -> storeResponseId(completedResponseId, () -> done.response().id()));
	}

	/**
	 * Append function call arguments delta to the pending function call.
	 *
	 * @param pendingCalls pending function calls map
	 * @param deltaEvent   the function call arguments delta event
	 */
	private static void appendFunctionArgsDelta(
		final Map<Long, PendingFunctionCall> pendingCalls,
		final ResponseFunctionCallArgumentsDeltaEvent deltaEvent
	) {
		final long outputIndex = deltaEvent.outputIndex();
		final String delta = deltaEvent.delta();
		pendingCalls.computeIfAbsent(outputIndex, PendingFunctionCall::new).appendArgs(delta);
	}

	/**
	 * Handle output item done event for function call and store it in the toolCalls list.
	 *
	 * @param pendingCalls pending function calls map
	 * @param toolCalls    list to store tool call data
	 * @param doneEvent    the output item done event
	 */
	private static void handleOutputItemDoneForFunctionCall(
		final Map<Long, PendingFunctionCall> pendingCalls,
		final List<ToolCallData> toolCalls,
		ResponseOutputItemDoneEvent doneEvent
	) {
		ResponseOutputItem item = doneEvent.item();
		if (!item.isFunctionCall()) {
			return;
		}
		item
			.functionCall()
			.ifPresent((ResponseFunctionToolCall fc) -> {
				final PendingFunctionCall pending = pendingCalls.computeIfAbsent(
					doneEvent.outputIndex(),
					PendingFunctionCall::new
				);
				toolCalls.add(new ToolCallData(fc.name(), fc.callId(), pending.getArgsJson()));
			});
	}

	/**
	 * Handle added event with function call.
	 *
	 * @param emitter      The SseEmitter to send events to the client.
	 * @param terminated   AtomicBoolean indicating if the stream has been terminated.
	 * @param pendingCalls Map of pending function calls.
	 * @param added        The ResponseOutputItemAddedEvent to handle.
	 */
	private static void handleAddedEventWithFunctionCall(
		final SseEmitter emitter,
		final AtomicBoolean terminated,
		final Map<Long, PendingFunctionCall> pendingCalls,
		final ResponseOutputItemAddedEvent added
	) {
		final ResponseOutputItem item = added.item();
		final long outputIndex = added.outputIndex();
		if (item.isFunctionCall()) {
			item
				.functionCall()
				.ifPresent((ResponseFunctionToolCall fc) -> {
					pendingCalls.computeIfAbsent(outputIndex, PendingFunctionCall::new);
					sendEventSafely(emitter, terminated, FUNCTION_REQUESTED_EVENT_CODE, "\n\n* `" + fc.name() + "`\n\n");
				});
		}
	}

	/**
	 * Store the response ID from the in-progress event.
	 *
	 * @param currentResponseId  the atomic reference to store the current response ID
	 * @param responseIdSupplier the supplier to get the response ID
	 */
	private void storeResponseId(
		final AtomicReference<String> currentResponseId,
		final Supplier<String> responseIdSupplier
	) {
		try {
			final String id = responseIdSupplier.get();
			if (StringHelper.nonNullNonBlank(id)) {
				currentResponseId.set(id);
			}
		} catch (Exception ignored) {
			log.debug("Failed to get response ID from in-progress event");
		}
	}

	/**
	 * Find the ToolCallback by tool name.
	 * @param toolName the name of the tool
	 * @return the ToolCallback if found, null otherwise
	 */
	private ToolCallback findToolCallback(String toolName) {
		for (ToolCallback cb : toolCallbackProvider.getToolCallbacks()) {
			var def = cb.getToolDefinition();
			if (toolName.equals(def.name())) {
				return cb;
			}
		}
		return null;
	}

	/**
	 * Build input items from chat history and user message.
	 *
	 * @param history     list of chat messages representing the history
	 * @param userMessage the user's current message
	 * @return list of ResponseInputItem for the chat request
	 */
	private static List<ResponseInputItem> buildInputItems(List<ChatMessage> history, String userMessage) {
		List<ResponseInputItem> items = new ArrayList<>();

		items.add(
			ResponseInputItem.ofEasyInputMessage(
				EasyInputMessage
					.builder()
					.role(EasyInputMessage.Role.SYSTEM)
					.content(ChatClientOpenAiConfiguration.SYSTEM_PROMPT)
					.build()
			)
		);

		if (history != null) {
			for (ChatMessage message : history) {
				if (message == null || !StringHelper.nonNullNonBlank(message.content())) {
					continue;
				}

				EasyInputMessage.Role role;
				if (ASSISTANT_ROLE.equalsIgnoreCase(message.role())) {
					role = EasyInputMessage.Role.ASSISTANT;
				} else {
					role = EasyInputMessage.Role.USER;
				}

				items.add(
					ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder().role(role).content(message.content()).build())
				);
			}
		}

		items.add(
			ResponseInputItem.ofEasyInputMessage(
				EasyInputMessage
					.builder()
					.role(EasyInputMessage.Role.USER)
					.content(Optional.ofNullable(userMessage).orElse(""))
					.build()
			)
		);

		return items;
	}

	/**
	 * Close the stream quietly without throwing exceptions.
	 *
	 * @param stream the StreamResponse to close
	 */
	private static void closeStreamQuietly(final StreamResponse<?> stream) {
		if (stream == null) {
			return;
		}
		try {
			stream.close();
		} catch (Exception ignored) {}
	}

	/**
	 * Send an immediate SSE error message to the client.
	 *
	 * @param message the error message to send
	 * @return an SseEmitter with the error message sent
	 */
	private SseEmitter sendImmediateError(final String message) {
		final var emitter = new SseEmitter(SSE_TIMEOUT_MS);
		try {
			emitter.send(SseEmitter.event().name(ERROR_EVENT_CODE).data(new ChatErrorResponse(message)));
		} catch (IOException e) {
			log.debug("Unable to send immediate SSE error (client disconnected): {}", e.getMessage());
		} finally {
			try {
				emitter.complete();
			} catch (Exception ignored) {}
		}
		return emitter;
	}

	/**
	 * Check if the OpenAI API key is configured.
	 *
	 * @return true if the API key is configured, false otherwise
	 */
	private boolean hasApiKey() {
		final String apiKey = chatConfig.getApiKey();
		return StringHelper.nonNullNonBlank(apiKey) && !apiKey.equals(UNCONFIGURED_API_KEY);
	}

	/**
	 * Send an SSE event safely, handling exceptions and termination.
	 *
	 * @param emitter    the SseEmitter to send the event
	 * @param terminated the AtomicBoolean indicating if the stream is terminated
	 * @param eventName  the name of the event to send
	 * @param data       the data to send with the event
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

		if (data == null) {
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

	/**
	 * Complete the SseEmitter safely, handling exceptions.
	 *
	 * @param emitter    the SseEmitter to complete
	 * @param terminated the AtomicBoolean indicating if the stream is terminated
	 */
	private static void completeSafely(final SseEmitter emitter, final AtomicBoolean terminated) {
		if (!terminated.compareAndSet(false, true)) {
			return;
		}
		try {
			emitter.complete();
		} catch (Exception ignored) {}
	}

	/**
	 * Data class representing a tool call.
	 */
	static final class ToolCallData {

		@Getter
		private final String toolName;

		@Getter
		private final String callId;

		@Getter
		private final String argsJson;

		/**
		 * Constructor for ToolCallData.
		 *
		 * @param toolName The name of the tool (function) being called.
		 * @param callId   The unique identifier for the tool call.
		 * @param argsJson The JSON string of arguments for the tool call.
		 */
		ToolCallData(String toolName, String callId, String argsJson) {
			this.toolName = toolName;
			this.callId = callId;
			this.argsJson = argsJson;
		}
	}

	/**
	 * Class representing a pending function call with argument accumulation.
	 */
	static final class PendingFunctionCall {

		private final long outputIndex;
		private final StringBuilder args = new StringBuilder();

		/**
		 * Constructor for PendingFunctionCall.
		 *
		 * @param outputIndex the output index of the function call
		 */
		PendingFunctionCall(long outputIndex) {
			this.outputIndex = outputIndex;
		}

		/**
		 * Append argument delta to the function call.
		 *
		 * @param delta the argument delta to append
		 */
		synchronized void appendArgs(String delta) {
			if (delta != null) {
				args.append(delta);
			}
		}

		/**
		 * Get the accumulated arguments as a JSON string.
		 *
		 * @return the arguments JSON string
		 */
		synchronized String getArgsJson() {
			return args.toString();
		}

		/**
		 * Get the output index of the function call.
		 *
		 * @return the output index
		 */
		synchronized long getOutputIndex() {
			return outputIndex;
		}
	}
}
