package org.metricshub.web.service;

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

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.config.M8bConfigurationProperties;
import org.metricshub.web.dto.m8b.M8bWebSocketMessage;
import org.metricshub.web.security.EphemeralApiKeyFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket client for connecting to the M8B Control Plane.
 * <p>
 * This service handles:
 * </p>
 * <ul>
 *   <li>Establishing WebSocket connection to the M8B server</li>
 *   <li>Agent registration on connect</li>
 *   <li>Ping/pong heartbeat handling</li>
 *   <li>Automatic reconnection on disconnect</li>
 * </ul>
 */
@Slf4j
public class M8bControlPlaneClient extends TextWebSocketHandler {

	private final M8bConfigurationProperties config;
	private final AgentContextHolder agentContextHolder;
	private final ObjectMapper objectMapper;
	private final EphemeralApiKeyService ephemeralApiKeyService;
	private final WebClient webClient;
	private final int serverPort;

	private WebSocketSession session;
	private ScheduledExecutorService scheduler;
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private final AtomicBoolean connecting = new AtomicBoolean(false);
	private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
	private String agentId;

	/**
	 * Creates a new M8B Control Plane client.
	 *
	 * @param config                 the M8B configuration properties
	 * @param agentContextHolder     the agent context holder for accessing agent info
	 * @param objectMapper           the JSON object mapper
	 * @param ephemeralApiKeyService the ephemeral API key service for proxy authentication
	 * @param webClient              the WebClient for executing proxy requests
	 * @param serverPort             the local server port
	 */
	public M8bControlPlaneClient(
		M8bConfigurationProperties config,
		AgentContextHolder agentContextHolder,
		ObjectMapper objectMapper,
		EphemeralApiKeyService ephemeralApiKeyService,
		WebClient webClient,
		int serverPort
	) {
		this.config = config;
		this.agentContextHolder = agentContextHolder;
		this.objectMapper = objectMapper;
		this.ephemeralApiKeyService = ephemeralApiKeyService;
		this.webClient = webClient;
		this.serverPort = serverPort;
	}

	/**
	 * Initializes the client and connects to the M8B server if configuration is valid.
	 */
	@PostConstruct
	public void init() {
		if (!config.isValid()) {
			log.info("M8B Control Plane integration is disabled or not configured");
			return;
		}

		this.agentId = resolveAgentId();
		this.scheduler =
			Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "m8b-reconnect");
				t.setDaemon(true);
				return t;
			});

		log.info("M8B Control Plane integration enabled. Connecting to: {}", config.getUrl());
		connect();
	}

	/**
	 * Cleans up resources and disconnects from the M8B server.
	 */
	@PreDestroy
	public void destroy() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
		disconnect();
	}

	/**
	 * Resolves the agent ID from configuration or generates one based on hostname.
	 *
	 * @return the agent ID to use for registration
	 */
	private String resolveAgentId() {
		if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
			return config.getAgentId();
		}

		// Generate agent ID from hostname
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext != null && agentContext.getAgentInfo() != null) {
			final var hostname = agentContext.getAgentInfo().getAttributes().get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY);
			if (hostname != null && !hostname.isBlank() && !"unknown".equals(hostname)) {
				return "agent-" + hostname;
			}
		}

		return "agent-" + System.currentTimeMillis();
	}

	/**
	 * Initiates connection to the M8B server.
	 */
	public void connect() {
		if (connected.get() || connecting.get()) {
			return;
		}

		connecting.set(true);

		try {
			final var client = new StandardWebSocketClient();

			// Build WebSocket URI with API key
			final var wsUri = URI.create(config.getUrl() + "?apiKey=" + config.getApiKey());

			// Connect
			final var headers = new WebSocketHttpHeaders();
			client.execute(this, headers, wsUri).get(30, TimeUnit.SECONDS);

			log.info("Connected to M8B Control Plane");
		} catch (Exception e) {
			log.error("Failed to connect to M8B Control Plane: {}", e.getMessage());
			connecting.set(false);
			scheduleReconnect();
		}
	}

	/**
	 * Disconnects from the M8B server.
	 */
	public void disconnect() {
		connected.set(false);
		if (session != null && session.isOpen()) {
			try {
				session.close();
			} catch (IOException e) {
				log.warn("Error closing WebSocket session: {}", e.getMessage());
			}
		}
		session = null;
	}

	/**
	 * Schedules a reconnection attempt.
	 */
	private void scheduleReconnect() {
		if (scheduler == null || scheduler.isShutdown()) {
			return;
		}

		final int maxAttempts = config.getMaxReconnectAttempts();
		final int attempts = reconnectAttempts.incrementAndGet();

		if (maxAttempts > 0 && attempts > maxAttempts) {
			log.error("Max reconnect attempts ({}) reached, giving up", maxAttempts);
			return;
		}

		final int delay = config.getReconnectIntervalSeconds();
		log.info("Scheduling reconnect attempt {} in {} seconds", attempts, delay);

		scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		this.session = session;
		this.connected.set(true);
		this.connecting.set(false);
		this.reconnectAttempts.set(0);

		log.info("WebSocket connection established to M8B Control Plane");

		// Send registration message
		sendRegisterMessage();
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		final String payload = message.getPayload();
		log.debug("Received message from M8B: {}", payload);

		try {
			final var wsMessage = objectMapper.readValue(payload, M8bWebSocketMessage.class);
			handleMessage(wsMessage);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse message from M8B: {}", e.getMessage());
		}
	}

	/**
	 * Handles incoming WebSocket messages.
	 *
	 * @param message the received message
	 */
	private void handleMessage(M8bWebSocketMessage message) {
		if (message instanceof M8bWebSocketMessage.Ping ping) {
			handlePing(ping);
		} else if (message instanceof M8bWebSocketMessage.Request request) {
			handleProxyRequest(request);
		} else if (message instanceof M8bWebSocketMessage.Error error) {
			log.error("Received error from M8B: {} - {}", error.getCode(), error.getMessage());
		} else {
			log.warn("Unhandled message type: {}", message.getClass().getSimpleName());
		}
	}

	/**
	 * Handles ping messages by sending a pong response.
	 *
	 * @param ping the ping message
	 */
	private void handlePing(M8bWebSocketMessage.Ping ping) {
		final var pong = new M8bWebSocketMessage.Pong();
		pong.setTimestamp(ping.getTimestamp());
		sendMessage(pong);
		log.debug("Responded to ping with pong");
	}

	/**
	 * Handles proxy requests by executing them against the local REST API.
	 * <p>
	 * Uses WebClient with an ephemeral token for authentication.
	 * </p>
	 *
	 * @param request the proxy request
	 */
	private void handleProxyRequest(M8bWebSocketMessage.Request request) {
		log.info("Received proxy request: {} {}", request.getMethod(), request.getPath());

		try {
			// Generate ephemeral token for this request
			final String ephemeralToken = ephemeralApiKeyService.generateToken();

			// Decode path to avoid double-encoding
			final String decodedPath = URLDecoder.decode(request.getPath(), StandardCharsets.UTF_8);

			// Build local URL (use https if TLS is enabled)
			final String protocol = "https";
			final String localUrl = protocol + "://localhost:" + serverPort + decodedPath;

			// Build WebClient request
			WebClient.RequestBodySpec requestSpec = webClient
				.method(HttpMethod.valueOf(request.getMethod()))
				.uri(localUrl)
				.header(EphemeralApiKeyFilter.EPHEMERAL_TOKEN_HEADER, ephemeralToken);

			// Add headers from request
			if (request.getHeaders() != null) {
				request
					.getHeaders()
					.forEach((key, value) -> {
						// Skip host header to avoid issues
						if (!"host".equalsIgnoreCase(key)) {
							requestSpec.header(key, value);
						}
					});
			}

			// Add body if present
			WebClient.ResponseSpec responseSpec;
			if (request.getBody() != null) {
				responseSpec = requestSpec.contentType(MediaType.APPLICATION_JSON).bodyValue(request.getBody()).retrieve();
			} else {
				responseSpec = requestSpec.retrieve();
			}

			// Execute and handle response
			responseSpec
				.toEntity(Object.class)
				.subscribe(
					entity -> {
						final var response = new M8bWebSocketMessage.Response();
						response.setRequestId(request.getRequestId());
						response.setStatus(entity.getStatusCode().value());

						// Convert headers
						final Map<String, String> headers = new HashMap<>();
						entity
							.getHeaders()
							.forEach((key, values) -> {
								if (!values.isEmpty()) {
									headers.put(key, values.get(0));
								}
							});
						response.setHeaders(headers);
						response.setBody(entity.getBody());

						sendMessage(response);
						log.debug("Sent proxy response for request {}: {}", request.getRequestId(), entity.getStatusCode());
					},
					error -> {
						log.error("Proxy request failed: {}", error.getMessage());
						sendProxyError(request.getRequestId(), error);
					}
				);
		} catch (Exception e) {
			log.error("Failed to execute proxy request: {}", e.getMessage());
			sendProxyError(request.getRequestId(), e);
		}
	}

	/**
	 * Sends an error response for a failed proxy request.
	 *
	 * @param requestId the request ID
	 * @param error     the error that occurred
	 */
	private void sendProxyError(String requestId, Throwable error) {
		final var response = new M8bWebSocketMessage.Response();
		response.setRequestId(requestId);
		response.setStatus(500);
		response.setHeaders(Map.of("Content-Type", "application/json"));
		response.setBody(Map.of("error", error.getMessage() != null ? error.getMessage() : "Internal server error"));
		sendMessage(response);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		log.info("WebSocket connection closed: {}", status);
		this.connected.set(false);
		this.connecting.set(false);
		this.session = null;

		// Schedule reconnect unless shutdown
		if (!CloseStatus.NORMAL.equals(status)) {
			scheduleReconnect();
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.error("WebSocket transport error: {}", exception.getMessage());
		this.connected.set(false);
		this.connecting.set(false);

		scheduleReconnect();
	}

	/**
	 * Sends the REGISTER message to the M8B server.
	 */
	private void sendRegisterMessage() {
		final var register = new M8bWebSocketMessage.Register();
		register.setAgentId(agentId);

		// Get agent info
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext != null && agentContext.getAgentInfo() != null) {
			final AgentInfo agentInfo = agentContext.getAgentInfo();
			register.setHostname(agentInfo.getAttributes().get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY));
			register.setVersion(agentInfo.getApplicationProperties().project().version());

			// Add metadata
			final Map<String, String> metadata = new HashMap<>();
			metadata.put("osType", agentInfo.getAttributes().get("os.type"));
			register.setMetadata(metadata);
		}

		sendMessage(register);
		log.info("Sent REGISTER message with agentId: {}", agentId);
	}

	/**
	 * Sends a WebSocket message to the M8B server.
	 *
	 * @param message the message to send
	 */
	private void sendMessage(M8bWebSocketMessage message) {
		if (session == null || !session.isOpen()) {
			log.warn("Cannot send message: WebSocket session is not open");
			return;
		}

		try {
			final String json = objectMapper.writeValueAsString(message);
			session.sendMessage(new TextMessage(json));
		} catch (IOException e) {
			log.error("Failed to send message: {}", e.getMessage());
		}
	}

	/**
	 * Returns whether the client is currently connected.
	 *
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return connected.get();
	}
}
