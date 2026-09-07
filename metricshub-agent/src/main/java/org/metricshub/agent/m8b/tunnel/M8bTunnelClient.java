package org.metricshub.agent.m8b.tunnel;

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

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.HeartbeatPing;
import org.metricshub.agent.m8b.protocol.M8bMessage.HeartbeatPong;
import org.metricshub.agent.m8b.protocol.M8bMessage.ProtocolError;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.protocol.M8bMessage.Unknown;
import org.metricshub.opamp.client.http.OpampHttpTransport;
import org.metricshub.opamp.client.retry.RetrySchedule;

/**
 * Outbound WebSocket tunnel to the M8B Governor, built on the JDK {@link WebSocket}.
 * <p>
 * All state lives on one daemon thread: connection attempts, inbound frames, heartbeats, sends and
 * reconnections are serialized through {@link #executor}. Every asynchronous continuation carries the
 * connection generation it belongs to and is dropped when a newer connection superseded it, so a
 * late callback from a dead socket can never act on the live one.
 * </p>
 * <p>
 * Lifecycle: {@link #start()} connects and registers; a lost connection is retried with exponential
 * backoff and jitter; {@link #stop(String)} closes cleanly. The server acknowledges each registration
 * with the limits the agent must honor ({@link #limits()}).
 * </p>
 */
@Slf4j
public class M8bTunnelClient {

	/** Close code sent by the server when a newer session took over this agent's identity. */
	public static final int CLOSE_SUPERSEDED = 4001;
	/** Close code used by either side when heartbeats stopped. */
	public static final int CLOSE_HEARTBEAT_TIMEOUT = 4003;
	/** Standard close code for a frame type the protocol does not use (binary). */
	public static final int CLOSE_UNSUPPORTED_DATA = 1003;

	static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
	static final Duration STOP_TIMEOUT = Duration.ofSeconds(5);
	/** How long a registered connection must last before the reconnection backoff is reset. */
	static final Duration STABLE_CONNECTION = Duration.ofSeconds(60);
	/** How long a close frame may take to leave before the socket is aborted anyway. */
	static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);
	/** Multiple of the heartbeat interval after which a silent peer is considered gone. */
	static final double IDLE_FACTOR = 2.5;
	/** The JDK's hard limit on a close reason, measured in encoded bytes rather than characters. */
	static final int MAX_CLOSE_REASON_BYTES = 123;

	private final M8bTunnelSettings settings;
	private final M8bTunnelListener listener;
	private final HttpClient httpClient;
	private final ScheduledExecutorService executor;
	private final RetrySchedule retrySchedule;
	// Set by the thread factory when the executor creates its single thread
	private volatile Thread tunnelThread;

	// Everything below is written on the tunnel thread only; the two volatile fields are also read by
	// other threads through isConnected() and limits()
	private long generation;
	private boolean started;
	private boolean stopped;
	private volatile WebSocket webSocket;
	private CompletableFuture<WebSocket> sendChain;
	private volatile AgentRegistered limits;
	private Instant lastInbound;
	private ScheduledFuture<?> registrationDeadline;
	private ScheduledFuture<?> heartbeat;
	private ScheduledFuture<?> reconnect;
	private ScheduledFuture<?> stability;

	/**
	 * @param settings connection settings
	 * @param listener the agent-side reactions
	 */
	public M8bTunnelClient(final M8bTunnelSettings settings, final M8bTunnelListener listener) {
		this.settings = settings;
		this.listener = listener;
		this.httpClient = createHttpClient(settings);
		this.retrySchedule = new RetrySchedule(BASE_BACKOFF, settings.maxBackoff());
		this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-m8b-tunnel");
			thread.setDaemon(true);
			tunnelThread = thread;
			return thread;
		});
	}

	private static HttpClient createHttpClient(final M8bTunnelSettings settings) {
		try {
			final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(settings.connectTimeout());
			if (settings.certificateFile() != null && !settings.certificateFile().isBlank()) {
				builder.sslContext(OpampHttpTransport.createSslContext(settings.certificateFile()));
			}
			return builder.build();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize the M8B tunnel client: " + e.getMessage(), e);
		}
	}

	/**
	 * Connects in the background. Calling it twice has no effect.
	 */
	public void start() {
		executor.execute(() -> {
			if (started || stopped) {
				return;
			}
			started = true;
			log.info("M8B tunnel starting toward {}.", settings.endpoint());
			connect(++generation);
		});
	}

	/**
	 * Closes the tunnel and releases the thread. Blocks briefly for the close frame to leave.
	 *
	 * @param reason logged and sent as the close reason
	 */
	public void stop(final String reason) {
		if (executor.isShutdown()) {
			return;
		}
		final Runnable closing = () -> {
			if (stopped) {
				return;
			}
			stopped = true;
			cancelTimers();
			final WebSocket socket = webSocket;
			if (socket != null && !socket.isOutputClosed()) {
				log.info("M8B tunnel stopping. Reason: {}.", reason);
				// Abort whatever the close does: a rejected or slow close frame would otherwise
				// leave the server with a live session nobody reads any more.
				socket
					.sendClose(WebSocket.NORMAL_CLOSURE, closeReason(reason))
					.orTimeout(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
					.whenComplete((ws, error) -> socket.abort());
			}
			webSocket = null;
		};
		if (Thread.currentThread() == tunnelThread) {
			closing.run();
		} else {
			try {
				executor.submit(closing).get(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException | TimeoutException e) {
				log.debug("The M8B tunnel close frame could not be delivered.", e);
			}
		}
		executor.shutdownNow();
	}

	/**
	 * @return whether the current connection is registered with the server
	 */
	public boolean isConnected() {
		return limits != null && webSocket != null;
	}

	/**
	 * @return the limits imposed by the server at registration, {@code null} until registered
	 */
	public AgentRegistered limits() {
		return limits;
	}

	/**
	 * Sends a message on the current connection. Silently dropped when not connected or already
	 * stopped: the caller's request is already lost on the server side in that case.
	 *
	 * @param message the message
	 */
	public void send(final M8bMessage message) {
		dispatch(() -> sendNow(message));
	}

	// ---- tunnel thread ----

	private void connect(final long connectGeneration) {
		if (stopped || connectGeneration != generation) {
			return;
		}
		log.debug("M8B tunnel connecting (generation {}).", connectGeneration);
		final WebSocket.Builder builder = httpClient
			.newWebSocketBuilder()
			.connectTimeout(settings.connectTimeout())
			.header(M8bTunnelSettings.AGENT_UID_HEADER, settings.agentUid());
		settings.headers().forEach(builder::header);
		builder
			.buildAsync(settings.endpoint(), new FrameListener(connectGeneration))
			.whenComplete((socket, error) -> {
				if (!dispatch(() -> onConnected(connectGeneration, socket, error)) && socket != null) {
					// stop() ran while the handshake was in flight: the tunnel thread is gone and
					// nothing would ever close this authenticated socket. Asking the executor is the
					// only check without a window after it -- isShutdown() can still turn true
					// between the answer and the submission.
					socket.abort();
				}
			});
	}

	private void onConnected(final long connectGeneration, final WebSocket socket, final Throwable error) {
		if (connectGeneration != generation || stopped) {
			if (socket != null) {
				socket.abort();
			}
			return;
		}
		if (error != null) {
			final Throwable cause =
				error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
			log.warn("M8B tunnel connection to {} failed: {}", settings.endpoint(), cause.getMessage());
			log.debug("M8B tunnel connection failed:", cause);
			scheduleReconnect(connectGeneration);
			return;
		}
		webSocket = socket;
		sendChain = CompletableFuture.completedFuture(socket);
		lastInbound = Instant.now();
		limits = null;
		sendNow(listener.buildRegistration());
		registrationDeadline = executor.schedule(
			() -> {
				if (connectGeneration == generation && limits == null) {
					log.warn("M8B tunnel: no registration acknowledgement within {}.", settings.connectTimeout());
					dropConnection(connectGeneration, CLOSE_HEARTBEAT_TIMEOUT, "Registration timeout");
				}
			},
			settings.connectTimeout().toMillis(),
			TimeUnit.MILLISECONDS
		);
	}

	private void handleFrame(final long frameGeneration, final String text) {
		if (frameGeneration != generation || stopped) {
			return;
		}
		lastInbound = Instant.now();
		final M8bMessage message;
		try {
			message = M8bJson.read(text);
		} catch (Exception e) {
			log.warn("M8B tunnel: dropping an unreadable frame: {}", e.getMessage());
			sendNow(new ProtocolError("MALFORMED_MESSAGE", e.getMessage()));
			return;
		}
		switch (message) {
			case AgentRegistered registered -> onRegistered(registered);
			case HeartbeatPong pong -> log.trace("M8B tunnel heartbeat acknowledged.");
			case ToolInvoke invoke -> safely("onInvoke", () -> listener.onInvoke(invoke));
			case ProtocolError protocolError -> log.warn(
				"M8B server reported an error: {} - {}",
				protocolError.code(),
				protocolError.message()
			);
			case Unknown unknown -> {
				log.debug("M8B tunnel: ignoring an unknown message type '{}'.", unknown.type());
				sendNow(new ProtocolError("UNKNOWN_MESSAGE_TYPE", "Unknown message type: " + unknown.type()));
			}
			default -> {
				log.debug("M8B tunnel: ignoring an unexpected '{}' message from the server.", message.type());
				sendNow(new ProtocolError("UNEXPECTED_MESSAGE", "Unexpected message type: " + message.type()));
			}
		}
	}

	private void onRegistered(final AgentRegistered registered) {
		cancel(registrationDeadline);
		registrationDeadline = null;
		limits = registered;
		// The backoff is reset only once the connection has proven durable. Registering is not
		// enough: two agents sharing a uid each register before the other supersedes them, and
		// resetting here would put them in a tight loop stealing the session from each other.
		cancel(stability);
		final long registeredGeneration = generation;
		stability = executor.schedule(
			() -> {
				if (registeredGeneration == generation && !stopped) {
					retrySchedule.reset();
				}
			},
			STABLE_CONNECTION.toMillis(),
			TimeUnit.MILLISECONDS
		);
		final Duration interval =
			registered.heartbeatIntervalSeconds() > 0
				? Duration.ofSeconds(registered.heartbeatIntervalSeconds())
				: settings.heartbeatInterval();
		cancel(heartbeat);
		final long currentGeneration = generation;
		heartbeat = executor.scheduleAtFixedRate(
			() -> heartbeatTick(currentGeneration, interval),
			interval.toMillis(),
			interval.toMillis(),
			TimeUnit.MILLISECONDS
		);
		log.info(
			"M8B tunnel registered with {} (heartbeat {}s, max payload {} bytes, max in-flight {}).",
			settings.endpoint(),
			interval.toSeconds(),
			registered.maxPayloadBytes(),
			registered.maxInFlight()
		);
		safely("onRegistered", () -> listener.onRegistered(registered));
	}

	private void heartbeatTick(final long tickGeneration, final Duration interval) {
		if (tickGeneration != generation || stopped) {
			return;
		}
		final Duration silence = Duration.between(lastInbound, Instant.now());
		if (silence.toMillis() > interval.toMillis() * IDLE_FACTOR) {
			log.warn("M8B tunnel: no frame received for {}; reconnecting.", silence);
			dropConnection(tickGeneration, CLOSE_HEARTBEAT_TIMEOUT, "Heartbeat timeout");
			return;
		}
		sendNow(new HeartbeatPing());
	}

	private void onClosed(final long closedGeneration, final int code, final String reason) {
		if (closedGeneration != generation) {
			return;
		}
		log.info("M8B tunnel closed by the server (code {}): {}", code, reason);
		disconnected(closedGeneration, code, reason);
	}

	private void onTransportError(final long errorGeneration, final Throwable error) {
		if (errorGeneration != generation) {
			return;
		}
		log.warn("M8B tunnel transport error: {}", error.getMessage());
		log.debug("M8B tunnel transport error:", error);
		disconnected(errorGeneration, -1, error.getMessage());
	}

	private void dropConnection(final long dropGeneration, final int code, final String reason) {
		if (dropGeneration != generation) {
			return;
		}
		final WebSocket socket = webSocket;
		if (socket != null) {
			// Aborting fails a close frame still in flight, and the server would record an abnormal
			// disconnect instead of the code we mean. Let the close leave first, with a bounded
			// fallback in case the peer never completes it.
			final ScheduledFuture<?> abortFallback = executor.schedule(
				socket::abort,
				CLOSE_TIMEOUT.toMillis(),
				TimeUnit.MILLISECONDS
			);
			socket
				.sendClose(wireCloseCode(code), closeReason(reason))
				.whenComplete((ws, error) ->
					dispatch(() -> {
						abortFallback.cancel(false);
						socket.abort();
					})
				);
		}
		disconnected(dropGeneration, code, reason);
	}

	private void disconnected(final long lostGeneration, final int code, final String reason) {
		cancelTimers();
		webSocket = null;
		sendChain = null;
		final boolean wasRegistered = limits != null;
		limits = null;
		if (wasRegistered) {
			safely("onDisconnected", () -> listener.onDisconnected(code, reason));
		}
		if (stopped) {
			return;
		}
		if (code == CLOSE_SUPERSEDED) {
			log.warn(
				"M8B tunnel: another agent instance registered with the same uid ({}); check for a duplicated identity file.",
				settings.agentUid()
			);
		}
		scheduleReconnect(lostGeneration);
	}

	private void scheduleReconnect(final long fromGeneration) {
		if (stopped || fromGeneration != generation) {
			return;
		}
		final Duration delay = retrySchedule.nextDelayAfterFailure(null);
		final long nextGeneration = ++generation;
		log.info("M8B tunnel: next connection attempt in {}.", delay);
		reconnect = executor.schedule(() -> connect(nextGeneration), delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	private void sendNow(final M8bMessage message) {
		if (webSocket == null || sendChain == null || stopped) {
			log.debug("M8B tunnel not connected: dropping a '{}' message.", message.type());
			return;
		}
		final String text = M8bJson.write(message);
		final long sendGeneration = generation;
		// The JDK WebSocket refuses concurrent sends: chain them on the tunnel thread
		sendChain = sendChain
			.thenCompose(socket -> socket.sendText(text, true))
			.exceptionally(error -> {
				executor.execute(() -> {
					if (sendGeneration == generation) {
						log.warn("M8B tunnel send failed: {}", error.getMessage());
						disconnected(sendGeneration, -1, error.getMessage());
					}
				});
				return webSocket;
			});
	}

	private void cancelTimers() {
		cancel(registrationDeadline);
		cancel(heartbeat);
		cancel(reconnect);
		cancel(stability);
		registrationDeadline = null;
		heartbeat = null;
		reconnect = null;
		stability = null;
	}

	/**
	 * @return the number of consecutive failed or short-lived connections currently driving the
	 *         reconnection backoff
	 */
	int retryFailureCount() {
		return retrySchedule.getFailureCount();
	}

	/**
	 * Hands a transport callback to the tunnel thread. After {@link #stop(String)} the executor is
	 * gone and late callbacks from the closing socket are simply dropped.
	 *
	 * @param task the callback
	 * @return whether the tunnel thread accepted it
	 */
	private boolean dispatch(final Runnable task) {
		try {
			executor.execute(task);
			return true;
		} catch (RejectedExecutionException e) {
			log.trace("M8B tunnel stopped: dropping a late transport callback.");
			return false;
		}
	}

	/**
	 * The code actually put on the wire. A JDK client may only send {@code 1000} or a code in
	 * {@code [3000, 4999]}: {@code 1003} and its neighbours are reserved for the endpoint itself.
	 * Sending one anyway fails the close frame, and the server would then record an abnormal
	 * disconnect rather than the reason we mean -- which is precisely what the reason text carries.
	 */
	static int wireCloseCode(final int code) {
		return code == WebSocket.NORMAL_CLOSURE || (code >= 3000 && code <= 4999) ? code : WebSocket.NORMAL_CLOSURE;
	}

	/**
	 * A close reason must fit in 123 UTF-8 bytes or the JDK refuses to send the frame, and one
	 * character can encode as four of them. The encoder is what makes the cut safe: it stops on a
	 * character boundary, so the result never ends in half a code point.
	 */
	static String closeReason(final String reason) {
		if (reason == null) {
			return "";
		}
		if (reason.length() <= MAX_CLOSE_REASON_BYTES / 4) {
			return reason;
		}
		final ByteBuffer encoded = ByteBuffer.allocate(MAX_CLOSE_REASON_BYTES);
		StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(reason), encoded, true);
		return new String(encoded.array(), 0, encoded.position(), StandardCharsets.UTF_8);
	}

	private static void cancel(final ScheduledFuture<?> future) {
		if (future != null) {
			future.cancel(false);
		}
	}

	private static void safely(final String name, final Runnable callback) {
		try {
			callback.run();
		} catch (VirtualMachineError e) {
			throw e;
		} catch (Throwable t) {
			log.error("M8B tunnel listener '{}' failed: {}", name, t.getMessage());
			log.debug("M8B tunnel listener failed:", t);
		}
	}

	/**
	 * JDK WebSocket callbacks run on the HTTP client threads; each one only reassembles text frames
	 * and hands the complete frame to the tunnel thread, tagged with its connection generation.
	 */
	private final class FrameListener implements WebSocket.Listener {

		private final long frameGeneration;
		private final StringBuilder partial = new StringBuilder();

		private FrameListener(final long frameGeneration) {
			this.frameGeneration = frameGeneration;
		}

		@Override
		public void onOpen(final WebSocket socket) {
			socket.request(1);
		}

		@Override
		public CompletionStage<?> onText(final WebSocket socket, final CharSequence data, final boolean last) {
			partial.append(data);
			if (last) {
				final String text = partial.toString();
				partial.setLength(0);
				dispatch(() -> handleFrame(frameGeneration, text));
			}
			socket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(final WebSocket socket, final ByteBuffer data, final boolean last) {
			// Binary frames are not part of the protocol: the peer is incompatible, close as the spec says
			dispatch(() -> dropConnection(frameGeneration, CLOSE_UNSUPPORTED_DATA, "Binary frames are not supported"));
			return null;
		}

		@Override
		public CompletionStage<?> onClose(final WebSocket socket, final int statusCode, final String reason) {
			dispatch(() -> onClosed(frameGeneration, statusCode, reason));
			return null;
		}

		@Override
		public void onError(final WebSocket socket, final Throwable error) {
			dispatch(() -> onTransportError(frameGeneration, error));
		}
	}
}
