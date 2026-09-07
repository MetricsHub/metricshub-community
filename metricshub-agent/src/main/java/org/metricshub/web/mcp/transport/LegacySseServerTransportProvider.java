package org.metricshub.web.mcp.transport;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.ServerResponse.SseBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Hardened, MetricsHub-owned fork of the MCP Java SDK 0.17.0
 * {@code io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider}
 * (Copyright 2024-2024 the original author or authors, MIT License).
 * <p>
 * It implements the legacy MCP "HTTP with SSE" transport on top of Spring WebMVC: clients open an SSE stream on the
 * SSE endpoint, receive an {@code endpoint} event pointing to the message endpoint of their session, and POST their
 * JSON-RPC messages there. The SDK implementation lets a Tomcat request thread block forever when a client sends a
 * request on a session that never completed the {@code initialize} / {@code notifications/initialized} handshake
 * (typically an SSE client that reconnected its event stream without re-initializing), and never detects SSE
 * connections silently dropped by the network. Both defects eventually exhaust Tomcat's connection limit and make the
 * port refuse connections while the JVM is alive.
 * </p>
 * <p>
 * Modifications compared to the SDK class:
 * </p>
 * <ul>
 * <li>Each session tracks its MCP lifecycle state. Requests and notifications sent on a session that is not
 * initialized are rejected with HTTP 400 and a JSON-RPC error instead of blocking the request thread until the exchange
 * is established. Pre-initialization {@code ping} requests are answered directly, as allowed by the specification.</li>
 * <li>The wait for the handling of a message is bounded by {@code messageTimeout}: an asynchronous completion that
 * does not arrive in time releases the request thread with HTTP 504 (the handling is not cancelled, a late result is
 * still written to the SSE stream). Tool executions run synchronously on the request thread and are not interrupted.</li>
 * <li>Keep-alive pings close the SSE session when they cannot be delivered or are not answered within
 * {@code pingTimeout}, so that connections dropped by the network are released from Tomcat's connection counter.</li>
 * <li>Sessions that never complete the handshake are closed after {@code initializationTimeout}.</li>
 * </ul>
 */
@Slf4j
public class LegacySseServerTransportProvider implements McpServerTransportProvider {

	/**
	 * Event type for JSON-RPC messages sent through the SSE connection.
	 */
	public static final String MESSAGE_EVENT_TYPE = "message";

	/**
	 * Event type for sending the message endpoint URI to clients.
	 */
	public static final String ENDPOINT_EVENT_TYPE = "endpoint";

	/**
	 * Query parameter carrying the session identifier on the message endpoint.
	 */
	public static final String SESSION_ID = "sessionId";

	/**
	 * Default SSE endpoint path as specified by the MCP transport specification.
	 */
	public static final String DEFAULT_SSE_ENDPOINT = "/sse";

	/**
	 * Default bound applied to the handling of a single JSON-RPC message.
	 */
	public static final Duration DEFAULT_MESSAGE_TIMEOUT = Duration.ofMinutes(5);

	/**
	 * Default time granted to a client to answer a keep-alive ping. Generous on purpose: a client that is merely
	 * suspended (laptop lid closed) must not lose its session, while a connection dropped by the network is still
	 * reclaimed within minutes.
	 */
	public static final Duration DEFAULT_PING_TIMEOUT = Duration.ofMinutes(5);

	/**
	 * Default time granted to a client to complete the initialization handshake.
	 */
	public static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofMinutes(2);

	/**
	 * Interval of the maintenance loop when keep-alive pings are disabled.
	 */
	private static final Duration DEFAULT_MAINTENANCE_INTERVAL = Duration.ofSeconds(30);

	private static final TypeRef<Object> OBJECT_TYPE_REF = new TypeRef<>() {};

	/**
	 * What an {@code initialize} request may do with the handshake of its session.
	 */
	enum HandshakeClaim {
		/**
		 * The request started the handshake and owns its outcome.
		 */
		CLAIMED,
		/**
		 * Another initialize owns a handshake whose outcome is not settled yet; the request is refused.
		 */
		PENDING,
		/**
		 * No handshake is pending; the request is left to the SDK session and cannot alter the lifecycle state.
		 */
		SETTLED
	}

	/**
	 * MCP lifecycle state of a session, as observed from the messages posted by the client.
	 */
	public enum SessionState {
		/**
		 * The SSE stream is open but no {@code initialize} request has been received.
		 */
		CREATED,
		/**
		 * The {@code initialize} request has been received but not the {@code notifications/initialized}
		 * notification.
		 */
		INITIALIZING,
		/**
		 * The handshake is complete; regular requests are accepted.
		 */
		INITIALIZED
	}

	private final McpJsonMapper jsonMapper;

	private final String messageEndpoint;

	private final String sseEndpoint;

	private final String baseUrl;

	private final Duration messageTimeout;

	private final Duration pingTimeout;

	private final Duration initializationTimeout;

	private final RouterFunction<ServerResponse> routerFunction;

	private McpServerSession.Factory sessionFactory;

	/**
	 * Map of active client sessions, keyed by session ID.
	 */
	private final ConcurrentHashMap<String, SseSession> sessions = new ConcurrentHashMap<>();

	private final McpTransportContextExtractor<ServerRequest> contextExtractor;

	/**
	 * Flag indicating if the transport is shutting down.
	 */
	private volatile boolean isClosing = false;

	private final boolean keepAliveEnabled;

	private Disposable maintenanceTask;

	private LegacySseServerTransportProvider(
		final McpJsonMapper jsonMapper,
		final String baseUrl,
		final String messageEndpoint,
		final String sseEndpoint,
		final Duration keepAliveInterval,
		final Duration messageTimeout,
		final Duration pingTimeout,
		final Duration initializationTimeout,
		final McpTransportContextExtractor<ServerRequest> contextExtractor
	) {
		Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
		Assert.notNull(baseUrl, "Message base URL must not be null");
		Assert.notNull(messageEndpoint, "Message endpoint must not be null");
		Assert.notNull(sseEndpoint, "SSE endpoint must not be null");
		Assert.notNull(messageTimeout, "Message timeout must not be null");
		Assert.notNull(pingTimeout, "Ping timeout must not be null");
		Assert.notNull(initializationTimeout, "Initialization timeout must not be null");
		Assert.notNull(contextExtractor, "Context extractor must not be null");

		this.jsonMapper = jsonMapper;
		this.baseUrl = baseUrl;
		this.messageEndpoint = messageEndpoint;
		this.sseEndpoint = sseEndpoint;
		this.messageTimeout = messageTimeout;
		this.pingTimeout = pingTimeout;
		this.initializationTimeout = initializationTimeout;
		this.contextExtractor = contextExtractor;
		this.keepAliveEnabled = keepAliveInterval != null;
		this.routerFunction = RouterFunctions.route()
			.GET(this.sseEndpoint, this::handleSseConnection)
			.POST(this.messageEndpoint, this::handleMessage)
			.build();

		startMaintenanceLoop(keepAliveInterval != null ? keepAliveInterval : DEFAULT_MAINTENANCE_INTERVAL);
	}

	@Override
	public List<String> protocolVersions() {
		return List.of(ProtocolVersions.MCP_2024_11_05);
	}

	@Override
	public void setSessionFactory(final McpServerSession.Factory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Broadcasts a notification to all connected clients through their SSE connections. Errors on one session are
	 * logged and do not prevent the delivery to the other sessions.
	 *
	 * @param method The method name for the notification
	 * @param params The parameters for the notification
	 * @return A Mono that completes when the broadcast attempt is finished
	 */
	@Override
	public Mono<Void> notifyClients(final String method, final Object params) {
		if (sessions.isEmpty()) {
			log.debug("No active MCP SSE session to broadcast {} to", method);
			return Mono.empty();
		}

		log.debug("Broadcasting {} to {} active MCP SSE sessions", method, sessions.size());

		return Flux.fromIterable(sessions.values())
			// The server must not send anything but pings and logging to a client that has not completed the handshake
			.filter(sseSession -> sseSession.state().get() == SessionState.INITIALIZED)
			.flatMap(sseSession ->
				sseSession
					.session()
					.sendNotification(method, params)
					.doOnError(e ->
						log.error("Failed to send {} to MCP SSE session {}: {}", method, sseSession.id(), e.getMessage())
					)
					.onErrorComplete()
			)
			.then();
	}

	/**
	 * Initiates a graceful shutdown of the transport: stops the maintenance loop, refuses new connections, closes all
	 * active SSE connections and forgets the sessions.
	 *
	 * @return A Mono that completes when all cleanup operations are finished
	 */
	@Override
	public Mono<Void> closeGracefully() {
		return Flux.fromIterable(sessions.values())
			.doFirst(() -> {
				this.isClosing = true;
				if (this.maintenanceTask != null) {
					this.maintenanceTask.dispose();
				}
				log.debug(
					"Initiating graceful shutdown of the legacy MCP SSE transport with {} active sessions",
					sessions.size()
				);
			})
			.flatMap(sseSession -> sseSession.session().closeGracefully())
			.then()
			.doOnSuccess(v -> {
				log.debug("Graceful shutdown of the legacy MCP SSE transport completed");
				sessions.clear();
			});
	}

	/**
	 * Returns the RouterFunction that defines the HTTP endpoints for this transport: {@code GET} on the SSE endpoint
	 * to establish the event stream and {@code POST} on the message endpoint to receive JSON-RPC messages.
	 *
	 * @return The configured RouterFunction for handling HTTP requests
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return this.routerFunction;
	}

	/**
	 * @return the number of SSE sessions currently tracked by this transport
	 */
	public int activeSessionCount() {
		return sessions.size();
	}

	/**
	 * Returns the lifecycle state of a session.
	 *
	 * @param sessionId the session identifier
	 * @return the state, or an empty optional when the session is unknown
	 */
	public Optional<SessionState> sessionState(final String sessionId) {
		final SseSession sseSession = sessions.get(sessionId);
		return sseSession == null ? Optional.empty() : Optional.of(sseSession.state().get());
	}

	/**
	 * Handles new SSE connection requests from clients by creating a new session and establishing an SSE connection,
	 * then sending the initial {@code endpoint} event that tells the client where to POST its messages.
	 *
	 * @param request The incoming server request
	 * @return A ServerResponse configured for SSE communication, or an error response if the server is shutting down
	 */
	private ServerResponse handleSseConnection(final ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		return ServerResponse.sse(
			sseBuilder -> {
				final WebMvcMcpSessionTransport sessionTransport = new WebMvcMcpSessionTransport(sseBuilder);
				final McpServerSession session = sessionFactory.create(sessionTransport);
				final String sessionId = session.getId();
				final SseSession sseSession = new SseSession(sessionId, session, sessionTransport);
				sessionTransport.attach(sseSession);

				sseBuilder.onComplete(() -> forgetSession(sseSession, "completed"));
				sseBuilder.onTimeout(() -> forgetSession(sseSession, "timed out"));
				sseBuilder.onError(e -> forgetSession(sseSession, "failed: " + e.getMessage()));
				this.sessions.put(sessionId, sseSession);
				if (this.isClosing) {
					// The shutdown started between the check above and the registration: do not leave a stream open
					closeSession(sseSession, "the server is shutting down");
					return;
				}
				log.debug(
					"Opened MCP SSE session {} from {}. Active sessions: {}",
					sessionId,
					describeRemote(request),
					sessions.size()
				);

				try {
					sessionTransport.sendEndpointEvent(buildEndpointUrl(sessionId));
				} catch (Exception e) {
					log.error("Failed to send the initial endpoint event to MCP SSE session {}: {}", sessionId, e.getMessage());
					forgetSession(sseSession, "failed to send the endpoint event");
					sseBuilder.error(e);
				}
			},
			Duration.ZERO
		);
	}

	/**
	 * Constructs the full message endpoint URL by combining the base URL, message path and the session identifier.
	 *
	 * @param sessionId the unique session identifier
	 * @return the fully qualified endpoint URL as a string
	 */
	private String buildEndpointUrl(final String sessionId) {
		return UriComponentsBuilder.fromUriString(this.baseUrl)
			.path(this.messageEndpoint)
			.queryParam(SESSION_ID, sessionId)
			.build()
			.toUriString();
	}

	/**
	 * Handles incoming JSON-RPC messages from clients: resolves the session, enforces the MCP lifecycle, then
	 * processes the message through the session with a bounded wait.
	 *
	 * @param request The incoming server request containing the JSON-RPC message
	 * @return A ServerResponse indicating success (200 OK) or the appropriate error status
	 */
	private ServerResponse handleMessage(final ServerRequest request) {
		if (this.isClosing) {
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
		}

		final Optional<String> sessionIdParam = request.param(SESSION_ID);
		if (sessionIdParam.isEmpty()) {
			return ServerResponse.badRequest().body(new McpError("Session ID missing in message endpoint"));
		}

		final String sessionId = sessionIdParam.get();
		final SseSession sseSession = sessions.get(sessionId);
		if (sseSession == null) {
			return ServerResponse.status(HttpStatus.NOT_FOUND).body(new McpError("Session not found: " + sessionId));
		}

		final JSONRPCMessage message;
		try {
			message = McpSchema.deserializeJsonRpcMessage(jsonMapper, request.body(String.class));
		} catch (IllegalArgumentException | IOException | ServletException e) {
			log.error("Failed to deserialize the MCP message posted on SSE session {}: {}", sessionId, e.getMessage());
			return ServerResponse.badRequest().body(new McpError("Invalid message format"));
		}

		if (
			message instanceof JSONRPCRequest initializeRequest &&
			McpSchema.METHOD_INITIALIZE.equals(initializeRequest.method()) &&
			sseSession.claimHandshake(initializeRequest.id()) == HandshakeClaim.PENDING
		) {
			// A single handshake at a time: the initialize that lost the claim would race the owning one for the SDK
			// session and make the outcome of the owning request meaningless
			log.warn(
				"Rejected a second MCP initialize request posted on SSE session {} from {} while its handshake is pending",
				sseSession.id(),
				describeRemote(request)
			);
			return jsonResponse(
				HttpStatus.CONFLICT,
				new JSONRPCResponse(
					McpSchema.JSONRPC_VERSION,
					initializeRequest.id(),
					null,
					new JSONRPCResponse.JSONRPCError(
						McpSchema.ErrorCodes.INVALID_REQUEST,
						String.format("MCP session %s already has an initialize request in progress", sseSession.id()),
						null
					)
				)
			);
		}

		if (!trackLifecycle(sseSession, message)) {
			if (message instanceof JSONRPCRequest pingRequest && McpSchema.METHOD_PING.equals(pingRequest.method())) {
				return answerPing(sseSession, pingRequest);
			}
			if (message instanceof JSONRPCNotification notification) {
				// Nothing can be done with a notification before the handshake; drop it silently like the SDK does for
				// notifications without a handler, rather than failing the client's POST.
				log.debug(
					"Dropped MCP notification '{}' posted on uninitialized SSE session {} (state {})",
					notification.method(),
					sseSession.id(),
					sseSession.state().get()
				);
				return ServerResponse.accepted().build();
			}
			return rejectUninitialized(request, sseSession, message);
		}

		return dispatch(request, sseSession, message);
	}

	/**
	 * Updates the lifecycle state of the session according to the message and tells whether the message may be
	 * handed over to the session.
	 *
	 * @param sseSession the session the message was posted on
	 * @param message    the JSON-RPC message
	 * @return {@code true} when the session may process the message, {@code false} when the session is not
	 *         initialized and the message is neither part of the handshake nor a response
	 */
	private static boolean trackLifecycle(final SseSession sseSession, final JSONRPCMessage message) {
		final AtomicReference<SessionState> state = sseSession.state();

		// Responses to server-initiated requests (pings, sampling...) never wait on the initialization.
		if (message instanceof JSONRPCResponse) {
			return true;
		}

		if (message instanceof JSONRPCRequest request && McpSchema.METHOD_INITIALIZE.equals(request.method())) {
			// The handshake was claimed atomically in handleMessage (see SseSession.claimHandshake) before the
			// dispatch: the client receives the initialize response over the SSE stream and may post
			// notifications/initialized before the request thread returns. A rejected initialize resets the state
			// (see SseSession.onResponseSent); a failed or timed-out one closes the session (see dispatch).
			return true;
		}

		if (
			message instanceof JSONRPCNotification notification &&
			McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(notification.method())
		) {
			// Only a handshake that actually started can complete: an initialized notification on a fresh session (or
			// after a rejected initialize) is dropped, so that the SDK session is never marked initialized without a
			// successful initialize and later requests keep being refused
			return sseSession.onInitializedNotification();
		}

		return state.get() == SessionState.INITIALIZED;
	}

	/**
	 * Answers a {@code ping} request received before the initialization handshake is complete. The SDK session would
	 * wait for the handshake before answering, which is exactly the situation we must never block on.
	 *
	 * @param sseSession  the session the ping was posted on
	 * @param pingRequest the ping request
	 * @return 200 OK once the response has been written to the SSE stream
	 */
	private ServerResponse answerPing(final SseSession sseSession, final JSONRPCRequest pingRequest) {
		log.debug("Answering a pre-initialization ping on MCP SSE session {}", sseSession.id());
		sseSession
			.transport()
			.sendMessage(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, pingRequest.id(), Map.of(), null))
			.block();
		return ServerResponse.ok().build();
	}

	/**
	 * Rejects a message posted on a session that has not completed the initialization handshake.
	 *
	 * @param request    the HTTP request
	 * @param sseSession the session the message was posted on
	 * @param message    the rejected JSON-RPC message
	 * @return 400 Bad Request carrying a JSON-RPC error
	 */
	private ServerResponse rejectUninitialized(
		final ServerRequest request,
		final SseSession sseSession,
		final JSONRPCMessage message
	) {
		final String method = methodOf(message);
		final Object id = message instanceof JSONRPCRequest jsonRpcRequest ? jsonRpcRequest.id() : null;

		log.warn(
			"Rejected MCP message '{}' posted on SSE session {} in state {} from {}: the session is not initialized. " +
				"The client probably reconnected its SSE stream without re-initializing (send 'initialize' and " +
				"'notifications/initialized' first).",
			method,
			sseSession.id(),
			sseSession.state().get(),
			describeRemote(request)
		);

		final JSONRPCResponse errorResponse = new JSONRPCResponse(
			McpSchema.JSONRPC_VERSION,
			id,
			null,
			new JSONRPCResponse.JSONRPCError(
				McpSchema.ErrorCodes.INVALID_REQUEST,
				String.format(
					"MCP session %s is not initialized: send 'initialize' and 'notifications/initialized' before '%s'",
					sseSession.id(),
					method
				),
				null
			)
		);

		return jsonResponse(HttpStatus.BAD_REQUEST, errorResponse);
	}

	/**
	 * Hands the message over to the session and waits for its completion for at most {@code messageTimeout}. The
	 * handling itself is never cancelled: a synchronous tool execution runs on this thread until it returns, and an
	 * asynchronous completion that arrives after the deadline is still written to the SSE stream.
	 *
	 * @param request    the HTTP request
	 * @param sseSession the session the message was posted on
	 * @param message    the JSON-RPC message
	 * @return 200 OK, 504 when the message timed out or 500 on any other failure
	 */
	private ServerResponse dispatch(
		final ServerRequest request,
		final SseSession sseSession,
		final JSONRPCMessage message
	) {
		final String method = methodOf(message);
		try {
			final McpTransportContext transportContext = this.contextExtractor.extract(request);

			// Watchdog rather than Mono.timeout(): the handling is never cancelled, so a completion that arrives after
			// the deadline is still written to the SSE stream. Synchronous tool executions run inline on this thread
			// (immediateExecution) and are therefore never interrupted; the deadline only bounds asynchronous waits.
			final Sinks.Empty<Void> completion = Sinks.empty();
			sseSession
				.session()
				.handle(message)
				.contextWrite(ctx -> ctx.put(McpTransportContext.KEY, transportContext))
				.subscribe(ignored -> {}, completion::tryEmitError, completion::tryEmitEmpty);
			completion.asMono().timeout(messageTimeout).block();

			return ServerResponse.ok().build();
		} catch (Exception e) {
			// A failed or timed-out initialize leaves the SDK session in an unknown state (its handling is not cancelled
			// and may still complete later), so the session cannot be reused: close the stream, the client reconnects
			// and starts the handshake again on a fresh session.
			if (
				message instanceof JSONRPCRequest failed &&
				McpSchema.METHOD_INITIALIZE.equals(failed.method()) &&
				sseSession.ownsPendingHandshake(failed.id())
			) {
				closeSession(sseSession, "its initialize request failed or timed out (" + e.getMessage() + ")");
			}
			if (Exceptions.unwrap(e) instanceof TimeoutException) {
				log.error(
					"MCP message '{}' posted on SSE session {} did not complete within {}; releasing the request thread " +
						"(a late result is still delivered on the SSE stream)",
					method,
					sseSession.id(),
					messageTimeout
				);
				final Object id = message instanceof JSONRPCRequest jsonRpcRequest ? jsonRpcRequest.id() : null;
				final JSONRPCResponse errorResponse = new JSONRPCResponse(
					McpSchema.JSONRPC_VERSION,
					id,
					null,
					new JSONRPCResponse.JSONRPCError(
						McpSchema.ErrorCodes.INTERNAL_ERROR,
						String.format("MCP message '%s' did not complete within %s", method, messageTimeout),
						null
					)
				);
				return jsonResponse(HttpStatus.GATEWAY_TIMEOUT, errorResponse);
			}

			log.error(
				"Error handling MCP message '{}' posted on SSE session {}: {}",
				method,
				sseSession.id(),
				e.getMessage()
			);
			return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new McpError(e.getMessage()));
		}
	}

	/**
	 * Serializes a JSON-RPC response as the body of an HTTP response.
	 *
	 * @param status   the HTTP status
	 * @param response the JSON-RPC response
	 * @return the HTTP response
	 */
	private ServerResponse jsonResponse(final HttpStatus status, final JSONRPCResponse response) {
		try {
			return ServerResponse.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(jsonMapper.writeValueAsString(response));
		} catch (IOException e) {
			return ServerResponse.status(status).body(new McpError(response.error().message()));
		}
	}

	/**
	 * @param message a JSON-RPC message
	 * @return the method of the message, or {@code response} for responses
	 */
	private static String methodOf(final JSONRPCMessage message) {
		if (message instanceof JSONRPCRequest request) {
			return request.method();
		}
		if (message instanceof JSONRPCNotification notification) {
			return notification.method();
		}
		return "response";
	}

	/**
	 * @param request the HTTP request
	 * @return a printable description of the remote peer
	 */
	private static String describeRemote(final ServerRequest request) {
		return request.remoteAddress().map(Object::toString).orElse("unknown remote");
	}

	/**
	 * Starts the maintenance loop that pings the connected clients and evicts the sessions that never completed the
	 * initialization handshake.
	 *
	 * @param interval the loop interval
	 */
	private void startMaintenanceLoop(final Duration interval) {
		this.maintenanceTask = Flux.interval(interval, interval, Schedulers.boundedElastic()).subscribe(
			tick -> runMaintenance(),
			error -> log.error("The maintenance loop of the legacy MCP SSE transport failed: {}", error.getMessage(), error)
		);
	}

	/**
	 * Runs one maintenance pass: closes the sessions that did not complete the initialization handshake in time and,
	 * when keep-alive is enabled, pings the other sessions. A session whose ping fails or times out is closed so that
	 * the connection is released from Tomcat's connection counter.
	 */
	void runMaintenance() {
		runMaintenance(Instant.now());
	}

	/**
	 * Runs one maintenance pass at the given instant.
	 *
	 * @param now the current time, injectable for tests
	 */
	void runMaintenance(final Instant now) {
		if (this.isClosing) {
			return;
		}

		for (final SseSession sseSession : sessions.values()) {
			// Never let one session break the loop: an exception escaping here would terminate the Flux.interval
			// subscription and silently disable the keep-alive for the lifetime of the process.
			try {
				maintain(sseSession, now);
			} catch (Exception e) {
				log.warn("Maintenance of MCP SSE session {} failed: {}", sseSession.id(), e.getMessage(), e);
			}
		}
	}

	/**
	 * Runs one maintenance pass on one session.
	 *
	 * @param sseSession the session
	 * @param now        the current time
	 */
	private void maintain(final SseSession sseSession, final Instant now) {
		if (
			sseSession.state().get() != SessionState.INITIALIZED &&
			sseSession.createdAt().plus(initializationTimeout).isBefore(now)
		) {
			closeSession(
				sseSession,
				String.format(
					"the client did not complete the MCP initialization handshake within %s (state %s)",
					initializationTimeout,
					sseSession.state().get()
				)
			);
		} else if (keepAliveEnabled) {
			ping(sseSession);
		}
	}

	/**
	 * Sends a keep-alive ping to the client of the session and closes the session when the ping is not answered within
	 * {@code pingTimeout} or cannot be delivered. A JSON-RPC error answer counts as an answer.
	 *
	 * @param sseSession the session to ping
	 */
	private void ping(final SseSession sseSession) {
		sseSession
			.session()
			.sendRequest(McpSchema.METHOD_PING, null, OBJECT_TYPE_REF)
			// The timeout signal closes the SSE stream (servlet I/O): keep it off Reactor's non-blocking parallel threads
			.timeout(pingTimeout, Schedulers.boundedElastic())
			.subscribe(
				result -> log.trace("Keep-alive ping answered by MCP SSE session {}", sseSession.id()),
				error -> {
					if (Exceptions.unwrap(error) instanceof McpError) {
						// The client answered, even if with a JSON-RPC error (e.g. a client without a ping handler):
						// the connection is alive, which is all the keep-alive needs to know.
						log.debug(
							"MCP SSE session {} answered the keep-alive ping with an error ({}); keeping the session",
							sseSession.id(),
							error.getMessage()
						);
						return;
					}
					closeSession(sseSession, "the keep-alive ping was not answered: " + error.getMessage());
				}
			);
	}

	/**
	 * Closes the SSE stream of a session and forgets it.
	 *
	 * @param sseSession the session to close
	 * @param reason     why the session is closed, for the logs
	 */
	private void closeSession(final SseSession sseSession, final String reason) {
		if (sessions.remove(sseSession.id(), sseSession)) {
			log.warn("Closing MCP SSE session {} because {}. Active sessions: {}", sseSession.id(), reason, sessions.size());
			sseSession.transport().close();
		}
	}

	/**
	 * Forgets a session whose SSE stream has ended.
	 *
	 * @param sseSession the session
	 * @param cause      what happened to the stream, for the logs
	 */
	private void forgetSession(final SseSession sseSession, final String cause) {
		if (sessions.remove(sseSession.id(), sseSession)) {
			log.debug("MCP SSE stream of session {} {}. Active sessions: {}", sseSession.id(), cause, sessions.size());
		}
	}

	/**
	 * A tracked SSE session: the SDK session, its transport, its lifecycle state and its creation time.
	 *
	 * @param id        the session identifier
	 * @param session   the SDK session
	 * @param transport the SSE transport of the session
	 * @param state     the lifecycle state
	 * @param createdAt when the SSE stream was opened
	 */
	record SseSession(
		String id,
		McpServerSession session,
		WebMvcMcpSessionTransport transport,
		AtomicReference<SessionState> state,
		AtomicReference<Object> pendingInitializeId,
		Instant createdAt
	) {
		SseSession(final String id, final McpServerSession session, final WebMvcMcpSessionTransport transport) {
			this(id, session, transport, new AtomicReference<>(SessionState.CREATED), new AtomicReference<>(), Instant.now());
		}

		/**
		 * Atomically claims the handshake for the given initialize request; the outcome of a claimed handshake is
		 * settled by {@link #onResponseSent}.
		 *
		 * @param initializeRequestId the JSON-RPC id of the initialize request
		 * @return what the request may do, see {@link HandshakeClaim}
		 */
		HandshakeClaim claimHandshake(final Object initializeRequestId) {
			// Only the attempt that wins the CREATED -> INITIALIZING transition owns the handshake; the outcome of the
			// atomic operation is used rather than a fresh read, which could report a state the loser never observed.
			if (state.compareAndExchange(SessionState.CREATED, SessionState.INITIALIZING) == SessionState.CREATED) {
				pendingInitializeId.set(initializeRequestId);
				return HandshakeClaim.CLAIMED;
			}
			// Another initialize owns the handshake until its response has been observed, even if an early initialized
			// notification already moved the state on: a concurrent attempt is refused rather than racing it in the
			// SDK. Once the handshake is settled, a duplicate initialize is left to the SDK and cannot alter the state.
			return pendingInitializeId.get() != null ? HandshakeClaim.PENDING : HandshakeClaim.SETTLED;
		}

		/**
		 * Completes the handshake if one is in progress. The pending initialize stays tracked until its response is
		 * observed: a notification posted before an error response must not leave the session initialized.
		 *
		 * @return whether the notification completed a pending handshake (a notification that did not is dropped)
		 */
		boolean onInitializedNotification() {
			return state.compareAndSet(SessionState.INITIALIZING, SessionState.INITIALIZED);
		}

		/**
		 * @param initializeRequestId the JSON-RPC id of an initialize request
		 * @return whether that request is the one that started the pending handshake
		 */
		boolean ownsPendingHandshake(final Object initializeRequestId) {
			final Object pending = pendingInitializeId.get();
			return pending != null && pending.equals(initializeRequestId);
		}

		/**
		 * Records the outcome of the handshake from the response the SDK session writes to the client: an initialize
		 * request rejected at the JSON-RPC level (error response) has not started a handshake.
		 *
		 * @param response the JSON-RPC response about to be written to the SSE stream
		 */
		void onResponseSent(final JSONRPCResponse response) {
			final Object initializeRequestId = pendingInitializeId.get();
			if (initializeRequestId != null && initializeRequestId.equals(response.id())) {
				pendingInitializeId.compareAndSet(initializeRequestId, null);
				if (response.error() != null) {
					// Unconditional on purpose: only the request that started the handshake gets here, and an early
					// initialized notification may already have moved the state on; the rejected handshake wins
					state.set(SessionState.CREATED);
				}
			}
		}
	}

	/**
	 * Implementation of McpServerTransport for WebMVC SSE sessions. This class handles the transport-level
	 * communication for a specific client session.
	 */
	class WebMvcMcpSessionTransport implements McpServerTransport {

		private final SseBuilder sseBuilder;

		/**
		 * Lock to ensure thread-safe access to the SSE builder when sending messages. This prevents concurrent
		 * modifications that could lead to corrupted SSE events.
		 */
		private final ReentrantLock sseBuilderLock = new ReentrantLock();

		/**
		 * Set once the SSE stream has been completed by this transport; nothing must be written afterwards.
		 */
		private volatile boolean closed;

		/**
		 * The tracked session this transport belongs to, informed of the responses written to the client.
		 */
		private volatile SseSession sseSession;

		/**
		 * Attaches the tracked session once it exists (the SDK session is created with the transport).
		 *
		 * @param owner the tracked session
		 */
		void attach(final SseSession owner) {
			this.sseSession = owner;
		}

		/**
		 * Creates a new session transport with the specified SSE builder.
		 *
		 * @param sseBuilder The SSE builder for sending server events to the client
		 */
		WebMvcMcpSessionTransport(final SseBuilder sseBuilder) {
			this.sseBuilder = sseBuilder;
		}

		/**
		 * Writes the initial {@code endpoint} event, under the same lock as the messages so that a concurrent
		 * broadcast or keep-alive ping cannot interleave with it.
		 *
		 * @param endpointUrl the message endpoint URL of the session
		 * @throws IOException when the event cannot be written
		 */
		void sendEndpointEvent(final String endpointUrl) throws IOException {
			sseBuilderLock.lock();
			try {
				sseBuilder.event(ENDPOINT_EVENT_TYPE).data(endpointUrl);
			} finally {
				sseBuilderLock.unlock();
			}
		}

		/**
		 * Sends a JSON-RPC message to the client through the SSE connection.
		 *
		 * @param message The JSON-RPC message to send
		 * @return A Mono that completes when the message has been sent
		 */
		@Override
		public Mono<Void> sendMessage(final JSONRPCMessage message) {
			return Mono.fromRunnable(() -> {
				sseBuilderLock.lock();
				try {
					if (closed) {
						log.debug("Dropped an MCP message for a closed SSE stream");
						return;
					}
					final String jsonText;
					try {
						jsonText = jsonMapper.writeValueAsString(message);
					} catch (IOException e) {
						log.error("Failed to serialize an MCP message for the SSE stream: {}", e.getMessage());
						return;
					}
					final SseSession owner = sseSession;
					if (owner != null && message instanceof JSONRPCResponse response) {
						owner.onResponseSent(response);
					}
					try {
						sseBuilder.event(MESSAGE_EVENT_TYPE).data(jsonText);
					} catch (IOException e) {
						// The client is gone (connection aborted): end the stream quietly so that Tomcat releases the
						// connection, rather than failing the async request, which Tomcat logs as a SEVERE error with
						// a full stack trace for every abrupt client disconnect.
						log.debug("MCP SSE stream write failed ({}); completing the stream", e.getMessage());
						close();
					} catch (Exception e) {
						log.error("Failed to send an MCP message over SSE: {}", e.getMessage());
						sseBuilder.error(e);
					}
				} finally {
					sseBuilderLock.unlock();
				}
			});
		}

		/**
		 * Converts data from one type to another using the configured McpJsonMapper.
		 *
		 * @param data    The source data object to convert
		 * @param typeRef The target type reference
		 * @param <T>     The target type
		 * @return The converted object of type T
		 */
		@Override
		public <T> T unmarshalFrom(final Object data, final TypeRef<T> typeRef) {
			return jsonMapper.convertValue(data, typeRef);
		}

		/**
		 * Initiates a graceful shutdown of the transport.
		 *
		 * @return A Mono that completes when the shutdown is complete
		 */
		@Override
		public Mono<Void> closeGracefully() {
			return Mono.fromRunnable(this::close);
		}

		/**
		 * Closes the transport immediately by completing the SSE stream.
		 */
		@Override
		public void close() {
			sseBuilderLock.lock();
			try {
				if (closed) {
					return;
				}
				closed = true;
				sseBuilder.complete();
			} catch (Exception e) {
				log.warn("Failed to complete the SSE stream: {}", e.getMessage());
			} finally {
				sseBuilderLock.unlock();
			}
		}
	}

	/**
	 * Creates a new Builder instance for configuring and creating instances of LegacySseServerTransportProvider.
	 *
	 * @return A new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating instances of LegacySseServerTransportProvider.
	 */
	public static class Builder {

		private McpJsonMapper jsonMapper;

		private String baseUrl = "";

		private String messageEndpoint;

		private String sseEndpoint = DEFAULT_SSE_ENDPOINT;

		private Duration keepAliveInterval;

		private Duration messageTimeout = DEFAULT_MESSAGE_TIMEOUT;

		private Duration pingTimeout = DEFAULT_PING_TIMEOUT;

		private Duration initializationTimeout = DEFAULT_INITIALIZATION_TIMEOUT;

		private McpTransportContextExtractor<ServerRequest> contextExtractor = serverRequest -> McpTransportContext.EMPTY;

		/**
		 * Sets the JSON mapper to use for message serialization/deserialization.
		 *
		 * @param jsonMapper The JSON mapper to use
		 * @return This builder instance for method chaining
		 */
		public Builder jsonMapper(final McpJsonMapper jsonMapper) {
			Assert.notNull(jsonMapper, "McpJsonMapper must not be null");
			this.jsonMapper = jsonMapper;
			return this;
		}

		/**
		 * Sets the base URL advertised to clients in the endpoint event.
		 *
		 * @param baseUrl The base URL to use
		 * @return This builder instance for method chaining
		 */
		public Builder baseUrl(final String baseUrl) {
			Assert.notNull(baseUrl, "Base URL must not be null");
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Sets the endpoint path where clients will send their messages.
		 *
		 * @param messageEndpoint The message endpoint path
		 * @return This builder instance for method chaining
		 */
		public Builder messageEndpoint(final String messageEndpoint) {
			Assert.hasText(messageEndpoint, "Message endpoint must not be empty");
			this.messageEndpoint = messageEndpoint;
			return this;
		}

		/**
		 * Sets the endpoint path where clients will establish SSE connections. Defaults to
		 * {@link #DEFAULT_SSE_ENDPOINT}.
		 *
		 * @param sseEndpoint The SSE endpoint path
		 * @return This builder instance for method chaining
		 */
		public Builder sseEndpoint(final String sseEndpoint) {
			Assert.hasText(sseEndpoint, "SSE endpoint must not be empty");
			this.sseEndpoint = sseEndpoint;
			return this;
		}

		/**
		 * Sets the interval of the keep-alive pings. Keep-alive pings are disabled when not specified.
		 *
		 * @param keepAliveInterval The interval duration for keep-alive pings, or {@code null} to disable them
		 * @return This builder instance for method chaining
		 */
		public Builder keepAliveInterval(final Duration keepAliveInterval) {
			this.keepAliveInterval = keepAliveInterval;
			return this;
		}

		/**
		 * Sets the bound applied to the handling of one JSON-RPC message. Defaults to
		 * {@link #DEFAULT_MESSAGE_TIMEOUT}.
		 *
		 * @param messageTimeout the message timeout
		 * @return This builder instance for method chaining
		 */
		public Builder messageTimeout(final Duration messageTimeout) {
			Assert.notNull(messageTimeout, "Message timeout must not be null");
			this.messageTimeout = messageTimeout;
			return this;
		}

		/**
		 * Sets the time granted to a client to answer a keep-alive ping before its session is closed. Defaults to
		 * {@link #DEFAULT_PING_TIMEOUT}.
		 *
		 * @param pingTimeout the ping timeout
		 * @return This builder instance for method chaining
		 */
		public Builder pingTimeout(final Duration pingTimeout) {
			Assert.notNull(pingTimeout, "Ping timeout must not be null");
			this.pingTimeout = pingTimeout;
			return this;
		}

		/**
		 * Sets the time granted to a client to complete the initialization handshake before its session is closed.
		 * Defaults to {@link #DEFAULT_INITIALIZATION_TIMEOUT}.
		 *
		 * @param initializationTimeout the initialization timeout
		 * @return This builder instance for method chaining
		 */
		public Builder initializationTimeout(final Duration initializationTimeout) {
			Assert.notNull(initializationTimeout, "Initialization timeout must not be null");
			this.initializationTimeout = initializationTimeout;
			return this;
		}

		/**
		 * Sets the context extractor that lets the MCP feature implementations inspect HTTP transport level
		 * metadata of the request being processed.
		 *
		 * @param contextExtractor The contextExtractor to fill in a {@link McpTransportContext}
		 * @return this builder instance
		 */
		public Builder contextExtractor(final McpTransportContextExtractor<ServerRequest> contextExtractor) {
			Assert.notNull(contextExtractor, "contextExtractor must not be null");
			this.contextExtractor = contextExtractor;
			return this;
		}

		/**
		 * Builds a new instance of LegacySseServerTransportProvider with the configured settings.
		 *
		 * @return A new LegacySseServerTransportProvider instance
		 * @throws IllegalStateException if messageEndpoint is not set
		 */
		public LegacySseServerTransportProvider build() {
			if (messageEndpoint == null) {
				throw new IllegalStateException("MessageEndpoint must be set");
			}
			return new LegacySseServerTransportProvider(
				jsonMapper == null ? McpJsonMapper.getDefault() : jsonMapper,
				baseUrl,
				messageEndpoint,
				sseEndpoint,
				keepAliveInterval,
				messageTimeout,
				pingTimeout,
				initializationTimeout,
				contextExtractor
			);
		}
	}
}
