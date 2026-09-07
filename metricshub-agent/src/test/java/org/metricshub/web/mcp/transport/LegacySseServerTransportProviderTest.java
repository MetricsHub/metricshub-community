package org.metricshub.web.mcp.transport;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;

class LegacySseServerTransportProviderTest {

	private static final String MESSAGE_ENDPOINT = "/mcp/message";
	private static final Pattern SESSION_ID_PATTERN = Pattern.compile("sessionId=([\\w-]+)");

	private static final String INITIALIZE =
		"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"," +
		"\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
	private static final String INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
	private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
	private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}";
	private static final String CANCELLED_NOTIFICATION =
		"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":1}}";
	private static final String PING_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{}}";

	private LegacySseServerTransportProvider provider;

	@AfterEach
	void tearDown() {
		if (provider != null) {
			provider.closeGracefully().block(Duration.ofSeconds(5));
		}
	}

	@Test
	void shouldRejectRequestOnUninitializedSession() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		final long start = System.nanoTime();
		final MvcResult result = postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isBadRequest()).andReturn();
		final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertTrue(elapsedMs < 5_000, "The rejection must be immediate, took " + elapsedMs + " ms");
		final String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("\"code\":-32600"), body);
		assertTrue(body.contains("not initialized"), body);
		assertTrue(body.contains("\"id\":2"), body);
		assertTrue(session.handled.isEmpty(), "The message must not reach the session");
		assertEquals(Optional.of(LegacySseServerTransportProvider.SessionState.CREATED), provider.sessionState(sessionId));
	}

	@Test
	void shouldDropNotificationOnUninitializedSessionButForwardResponses() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// A notification cannot be acted upon before the handshake: accepted and dropped, never a failed POST
		postMessage(mockMvc, sessionId, CANCELLED_NOTIFICATION).andExpect(status().isAccepted());
		assertTrue(session.handled.isEmpty());

		postMessage(mockMvc, sessionId, PING_RESPONSE).andExpect(status().isOk());
		assertEquals(1, session.handled.size());
		assertTrue(session.handled.get(0) instanceof JSONRPCResponse);
	}

	@Test
	void shouldAnswerPingBeforeInitializationWithoutInvolvingTheSession() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final SseConnection sse = openSse(mockMvc);

		postMessage(mockMvc, sse.sessionId(), PING).andExpect(status().isOk());

		assertTrue(session.handled.isEmpty(), "The ping must be answered by the transport itself");
		final String stream = sse.result().getResponse().getContentAsString();
		assertTrue(stream.contains("\"id\":3"), stream);
		assertTrue(stream.contains("\"result\":{}"), stream);
	}

	@Test
	void shouldForwardMessagesOnceTheHandshakeIsComplete() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		assertEquals(
			Optional.of(LegacySseServerTransportProvider.SessionState.INITIALIZING),
			provider.sessionState(sessionId)
		);

		// Still not initialized: regular requests are rejected
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isBadRequest());

		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
		assertEquals(
			Optional.of(LegacySseServerTransportProvider.SessionState.INITIALIZED),
			provider.sessionState(sessionId)
		);

		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());

		assertEquals(3, session.handled.size());
		assertEquals("initialize", ((JSONRPCRequest) session.handled.get(0)).method());
		assertEquals("notifications/initialized", ((JSONRPCNotification) session.handled.get(1)).method());
		assertEquals("tools/list", ((JSONRPCRequest) session.handled.get(2)).method());
	}

	@Test
	void shouldNotConsiderTheHandshakeCompleteWithoutAnInitializeRequest() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// notifications/initialized on a fresh session: forwarded, but the session stays uninitialized
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
		assertEquals(Optional.of(LegacySseServerTransportProvider.SessionState.CREATED), provider.sessionState(sessionId));
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isBadRequest());
		assertEquals(1, session.handled.size());
	}

	@Test
	void shouldAcceptAnInitializedNotificationPostedWhileTheInitializeResponseIsStillInFlight() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// The client receives the initialize response over SSE and posts the notification before the initialize POST
		// returns: simulated by posting it from within the session's handling of initialize
		session.onHandle = () -> {
			try {
				postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		session.onHandle = null;

		assertEquals(
			Optional.of(LegacySseServerTransportProvider.SessionState.INITIALIZED),
			provider.sessionState(sessionId)
		);
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());
	}

	@Test
	void shouldResetTheHandshakeWhenInitializeIsRejectedWithAJsonRpcError() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// The SDK session answers initialize with a JSON-RPC error over SSE and completes normally
		session.onHandle = () -> {
			session.onHandle = null;
			session.transport
				.get()
				.sendMessage(
					new JSONRPCResponse(
						McpSchema.JSONRPC_VERSION,
						1,
						null,
						new JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS, "Unsupported protocol version", null)
					)
				)
				.block();
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		assertEquals(Optional.of(LegacySseServerTransportProvider.SessionState.CREATED), provider.sessionState(sessionId));

		// The rejected handshake cannot be completed by the notification
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
		assertEquals(Optional.of(LegacySseServerTransportProvider.SessionState.CREATED), provider.sessionState(sessionId));
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isBadRequest());
	}

	@Test
	void shouldCloseTheSessionWhenInitializeTimesOutAfterAnEarlyInitializedNotification() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().messageTimeout(Duration.ofMillis(200))
		);
		final SseConnection sse = openSse(mockMvc);

		// A buggy client posts the notification while its initialize is still pending, and the initialize then times out
		session.onHandle = () -> {
			session.onHandle = null;
			try {
				postMessage(mockMvc, sse.sessionId(), INITIALIZED).andExpect(status().isOk());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			// The initialize itself never completes
			session.handleResult = Mono.never();
		};
		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isGatewayTimeout());

		// The pending SDK handling may still complete later: the session is closed rather than reused
		assertEquals(0, provider.activeSessionCount());
		assertStreamCompleted(sse);
		postMessage(mockMvc, sse.sessionId(), TOOLS_LIST).andExpect(status().isNotFound());
	}

	@Test
	void shouldResetTheHandshakeWhenInitializeIsRejectedDespiteAnEarlyInitializedNotification() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// A buggy client posts the notification before the (error) response to its initialize is written
		session.onHandle = () -> {
			session.onHandle = null;
			try {
				postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
			session.transport
				.get()
				.sendMessage(
					new JSONRPCResponse(
						McpSchema.JSONRPC_VERSION,
						1,
						null,
						new JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS, "Unsupported protocol version", null)
					)
				)
				.block();
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());

		assertEquals(Optional.of(LegacySseServerTransportProvider.SessionState.CREATED), provider.sessionState(sessionId));
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isBadRequest());
	}

	@Test
	void shouldRefuseASecondInitializeWhileTheHandshakeIsPending() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		// The second initialize is posted while the first one is still being handled
		session.onHandle = () -> {
			session.onHandle = null;
			try {
				postMessage(mockMvc, sessionId, INITIALIZE.replace("\"id\":1", "\"id\":9")).andExpect(status().isConflict());
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());

		// Only the first initialize reached the session, and the handshake completes normally
		assertEquals(1, session.handled.size());
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());
	}

	@Test
	void shouldKeepAnInitializedSessionWhenADuplicateInitializeIsRejected() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();
		// The SDK session answers the initialize (success) over SSE, like the real one always does
		session.onHandle = () -> {
			session.onHandle = null;
			session.transport.get().sendMessage(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, 1, Map.of(), null)).block();
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());

		// A second initialize is rejected by the SDK with a JSON-RPC error and must not disturb the valid session
		session.onHandle = () -> {
			session.onHandle = null;
			session.transport
				.get()
				.sendMessage(
					new JSONRPCResponse(
						McpSchema.JSONRPC_VERSION,
						1,
						null,
						new JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INVALID_REQUEST, "Already initialized", null)
					)
				)
				.block();
		};
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());

		assertEquals(
			Optional.of(LegacySseServerTransportProvider.SessionState.INITIALIZED),
			provider.sessionState(sessionId)
		);
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());

		// Even a failing duplicate initialize leaves the valid session open
		session.handleResult = Mono.error(new IllegalStateException("duplicate initialize"));
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isInternalServerError());
		assertEquals(1, provider.activeSessionCount());
		session.handleResult = Mono.empty();
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());
	}

	@Test
	void shouldCloseTheSessionWhenInitializeFails() throws Exception {
		final RecordingSession session = new RecordingSession();
		session.handleResult = Mono.error(new IllegalStateException("initialize rejected"));
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final SseConnection sse = openSse(mockMvc);

		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isInternalServerError());

		// The SDK session is in an unknown state: the stream is closed and the client has to reconnect
		assertEquals(0, provider.activeSessionCount());
		assertTrue(provider.sessionState(sse.sessionId()).isEmpty());
		assertStreamCompleted(sse);
		postMessage(mockMvc, sse.sessionId(), INITIALIZED).andExpect(status().isNotFound());
	}

	@Test
	void shouldReleaseTheRequestThreadWhenTheSessionNeverCompletes() throws Exception {
		final RecordingSession session = new RecordingSession();
		session.handleResult = Mono.never();
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().messageTimeout(Duration.ofMillis(200))
		);
		final String sessionId = openSse(mockMvc).sessionId();

		final long start = System.nanoTime();
		final MvcResult result = postMessage(mockMvc, sessionId, INITIALIZE)
			.andExpect(status().isGatewayTimeout())
			.andReturn();
		final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertTrue(elapsedMs < 5_000, "The request thread must be released by the timeout, took " + elapsedMs + " ms");
		final String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("did not complete"), body);
		assertTrue(body.contains("\"id\":1"), body);
	}

	@Test
	void shouldCloseSessionsThatNeverCompleteTheHandshake() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().initializationTimeout(Duration.ofMinutes(2))
		);
		final Instant opened = Instant.now();
		final SseConnection sse = openSse(mockMvc);
		assertEquals(1, provider.activeSessionCount());

		// The initialization timeout is not elapsed yet: the session is kept
		provider.runMaintenance(opened.plus(Duration.ofMinutes(1)));
		assertEquals(1, provider.activeSessionCount());

		provider.runMaintenance(opened.plus(Duration.ofMinutes(3)));

		assertEquals(0, provider.activeSessionCount());
		assertTrue(provider.sessionState(sse.sessionId()).isEmpty());
		assertStreamCompleted(sse);
		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isNotFound());
	}

	@Test
	void shouldCloseSessionWhenTheKeepAlivePingFails() throws Exception {
		final RecordingSession session = new RecordingSession();
		session.pingResult = Mono.error(new IOException("Broken pipe"));
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().keepAliveInterval(Duration.ofMinutes(10))
		);
		final SseConnection sse = openSse(mockMvc);
		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sse.sessionId(), INITIALIZED).andExpect(status().isOk());

		provider.runMaintenance();

		assertEquals("ping", session.lastRequestMethod.get());
		assertEquals(0, provider.activeSessionCount());
		assertStreamCompleted(sse);
	}

	@Test
	void shouldKeepSessionWhoseClientAnswersTheKeepAlivePingWithAnError() throws Exception {
		final RecordingSession session = new RecordingSession();
		// A client without a ping handler answers with a JSON-RPC error: it is alive nonetheless
		session.pingResult = Mono.error(
			new McpError(new JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND, "Method not found", null))
		);
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().keepAliveInterval(Duration.ofMinutes(10))
		);
		final String sessionId = openSse(mockMvc).sessionId();
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());

		provider.runMaintenance();

		assertEquals("ping", session.lastRequestMethod.get());
		assertEquals(1, provider.activeSessionCount());
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());
	}

	@Test
	void shouldBroadcastNotificationsOnlyToInitializedSessions() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final SseConnection sse = openSse(mockMvc);

		provider.notifyClients("notifications/tools/list_changed", null).block(Duration.ofSeconds(5));
		assertTrue(session.notified.isEmpty(), "An uninitialized session must not receive broadcasts");

		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sse.sessionId(), INITIALIZED).andExpect(status().isOk());
		provider.notifyClients("notifications/tools/list_changed", null).block(Duration.ofSeconds(5));

		assertEquals(List.of("notifications/tools/list_changed"), session.notified);
	}

	@Test
	void shouldCloseSessionWhenTheKeepAlivePingIsNotAnswered() throws Exception {
		final RecordingSession session = new RecordingSession();
		session.pingResult = Mono.never();
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder()
				.keepAliveInterval(Duration.ofMinutes(10))
				.pingTimeout(Duration.ofMillis(100))
		);
		final SseConnection sse = openSse(mockMvc);
		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sse.sessionId(), INITIALIZED).andExpect(status().isOk());

		provider.runMaintenance();

		await()
			.atMost(Duration.ofSeconds(5))
			.until(() -> provider.activeSessionCount() == 0);
		assertStreamCompleted(sse);
	}

	@Test
	void shouldKeepInitializedSessionsWhoseKeepAlivePingIsAnswered() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(
			session,
			LegacySseServerTransportProvider.builder().keepAliveInterval(Duration.ofMinutes(10))
		);
		final String sessionId = openSse(mockMvc).sessionId();
		postMessage(mockMvc, sessionId, INITIALIZE).andExpect(status().isOk());
		postMessage(mockMvc, sessionId, INITIALIZED).andExpect(status().isOk());

		provider.runMaintenance();

		assertEquals("ping", session.lastRequestMethod.get());
		assertEquals(1, provider.activeSessionCount());
		postMessage(mockMvc, sessionId, TOOLS_LIST).andExpect(status().isOk());
	}

	@Test
	void shouldRejectUnknownOrMissingSessions() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());

		postMessage(mockMvc, "does-not-exist", INITIALIZE).andExpect(status().isNotFound());
		mockMvc
			.perform(post(MESSAGE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
			.andExpect(status().isBadRequest());
	}

	@Test
	void shouldRejectMalformedMessages() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final String sessionId = openSse(mockMvc).sessionId();

		postMessage(mockMvc, sessionId, "{\"jsonrpc\":\"2.0\",\"foo\":").andExpect(status().isBadRequest());
		assertTrue(session.handled.isEmpty());
	}

	@Test
	void shouldForgetSessionsWhoseStreamCompletes() throws Exception {
		final RecordingSession session = new RecordingSession();
		final MockMvc mockMvc = setUp(session, LegacySseServerTransportProvider.builder());
		final SseConnection sse = openSse(mockMvc);
		assertEquals(1, provider.activeSessionCount());

		// Complete the SSE stream, then let the container run the completion callbacks
		session.transport.get().close();
		mockMvc.perform(asyncDispatch(sse.result()));

		assertEquals(0, provider.activeSessionCount());
		postMessage(mockMvc, sse.sessionId(), INITIALIZE).andExpect(status().isNotFound());
	}

	private MockMvc setUp(final RecordingSession session, final LegacySseServerTransportProvider.Builder builder) {
		provider = builder.messageEndpoint(MESSAGE_ENDPOINT).build();
		provider.setSessionFactory(transport -> {
			session.transport.set(transport);
			return session;
		});
		return MockMvcBuilders.routerFunctions(provider.getRouterFunction()).build();
	}

	private static SseConnection openSse(final MockMvc mockMvc) throws Exception {
		final MvcResult result = mockMvc.perform(get("/sse")).andReturn();
		final String stream = result.getResponse().getContentAsString();
		assertTrue(stream.contains("event:endpoint"), stream);
		final Matcher matcher = SESSION_ID_PATTERN.matcher(stream);
		assertTrue(matcher.find(), stream);
		return new SseConnection(matcher.group(1), result);
	}

	private static ResultActions postMessage(final MockMvc mockMvc, final String sessionId, final String body)
		throws Exception {
		return mockMvc.perform(
			post(MESSAGE_ENDPOINT).param("sessionId", sessionId).contentType(MediaType.APPLICATION_JSON).content(body)
		);
	}

	/**
	 * Under MockMvc the completion of the SSE stream dispatches the async request; the async result is only available
	 * once that dispatch happened.
	 */
	private static void assertStreamCompleted(final SseConnection sse) {
		assertDoesNotThrow(() -> sse.result().getAsyncResult(1_000), "The SSE stream must be completed");
	}

	private record SseConnection(String sessionId, MvcResult result) {}

	/**
	 * SDK session stub recording the messages handed over by the transport provider.
	 */
	private static final class RecordingSession extends McpServerSession {

		private static final String ID = "session-under-test";

		final List<JSONRPCMessage> handled = new CopyOnWriteArrayList<>();
		final AtomicReference<McpServerTransport> transport = new AtomicReference<>();
		final AtomicReference<String> lastRequestMethod = new AtomicReference<>();
		final List<String> notified = new CopyOnWriteArrayList<>();
		volatile Mono<Void> handleResult = Mono.empty();
		volatile Runnable onHandle;
		volatile Mono<Object> pingResult = Mono.just(Map.of());

		RecordingSession() {
			super(ID, Duration.ofSeconds(5), new NoopTransport(), null, Map.of(), Map.of());
		}

		@Override
		public Mono<Void> handle(final JSONRPCMessage message) {
			handled.add(message);
			final Runnable hook = onHandle;
			if (hook != null && message instanceof JSONRPCRequest) {
				hook.run();
			}
			return handleResult;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Mono<T> sendRequest(final String method, final Object requestParams, final TypeRef<T> typeRef) {
			lastRequestMethod.set(method);
			return (Mono<T>) pingResult;
		}

		@Override
		public Mono<Void> sendNotification(final String method, final Object params) {
			notified.add(method);
			return Mono.empty();
		}

		@Override
		public Mono<Void> closeGracefully() {
			final McpServerTransport sessionTransport = transport.get();
			return sessionTransport == null ? Mono.empty() : sessionTransport.closeGracefully();
		}

		@Override
		public void close() {
			final McpServerTransport sessionTransport = transport.get();
			if (sessionTransport != null) {
				sessionTransport.close();
			}
		}
	}

	private static final class NoopTransport implements McpServerTransport {

		@Override
		public Mono<Void> sendMessage(final JSONRPCMessage message) {
			return Mono.empty();
		}

		@Override
		public <T> T unmarshalFrom(final Object data, final TypeRef<T> typeRef) {
			return null;
		}

		@Override
		public Mono<Void> closeGracefully() {
			return Mono.empty();
		}
	}
}
