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
import java.util.concurrent.CancellationException;
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
	/** Standard close code for a message above what the peer said it would accept. */
	static final int CLOSE_MESSAGE_TOO_BIG = 1009;
	/**
	 * The largest message accepted before the server has said otherwise.
	 *
	 * <p>A frame can arrive as an unbounded series of fragments, and the peer decides how many. Until
	 * {@code agent.registered} names a real cap this is the one in force, because "no limit yet" and
	 * "no limit" must not be the same thing.
	 */
	static final long DEFAULT_MAX_INBOUND_BYTES = 8L * 1024 * 1024;
	/**
	 * How many pieces one message may arrive in.
	 *
	 * <p>A size bound alone is not enough: an empty non-final fragment is legal and adds nothing to
	 * it, so a peer could send them forever — each one costing a callback and a task on the tunnel
	 * thread's queue — without the message ever growing. A message needing more pieces than this is
	 * a peer that is not really sending a message.
	 */
	static final int MAX_INBOUND_FRAGMENTS = 4_096;

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
			final WebSocket socket = webSocket;
			if (socket != null && !socket.isOutputClosed()) {
				log.info("M8B tunnel stopping. Reason: {}.", reason);
				// Waited for, not merely started: stop() is what a shutdown hook calls, and a JVM that
				// exits before the frame is flushed leaves the server recording an abnormal
				// disconnect instead of the clean 1000 this is here to send. The wait is bounded by
				// the orTimeout inside, which aborts rather than hanging.
				safely("close", () -> closeThenAbort(socket, WebSocket.NORMAL_CLOSURE, reason, CLOSE_TIMEOUT).join());
			}
			// The same cleanup every other loss gets. A stop ends a session as thoroughly as a
			// dropped connection does: the listener has to hear that its in-flight work was
			// discarded, and limits() must stop describing a session that no longer exists.
			disconnected(generation, WebSocket.NORMAL_CLOSURE, reason);
		};
		if (Thread.currentThread() == tunnelThread) {
			closing.run();
		} else {
			try {
				// Another thread may have shut the executor down between the check above and here, or
				// while this task waits: a second stop is meant to be harmless, not to throw.
				executor.submit(closing).get(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			} catch (RejectedExecutionException | CancellationException e) {
				log.debug("The M8B tunnel was already stopping.");
				abandonSession();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				// Same reason as below: the queued close never ran, and shutdownNow() is about to
				// discard it.
				abandonSession();
			} catch (ExecutionException | TimeoutException e) {
				log.debug("The M8B tunnel close frame could not be delivered.", e);
				// The tunnel thread never reached the close -- it is busy in a listener callback --
				// and the shutdownNow() below discards the queued task. Whatever is left of the
				// session has to be taken down from here, or the stop returns having left an
				// authenticated socket open with nothing able to close it.
				abandonSession();
			}
		}
		executor.shutdownNow();
	}

	/**
	 * Takes a session down without the tunnel thread, which is the only situation this is for.
	 *
	 * <p>An abort rather than a close: there is nobody left to wait for a close frame to leave, and
	 * an abandoned socket the server still believes in is worse than an abrupt disconnect it can
	 * see. The published state is cleared with it, so {@link #limits()} stops describing a session
	 * that is gone.
	 *
	 * <p>The listener is told, and told from THIS thread rather than the tunnel's, which is the one
	 * place the contract is bent. It is bent knowingly: the tunnel thread is the reason we are here,
	 * and a listener whose whole job is to discard work that can no longer be answered is better
	 * called on the wrong thread than not called at all.
	 */
	private void abandonSession() {
		final WebSocket socket = webSocket;
		if (socket != null) {
			socket.abort();
		}
		final boolean wasRegistered = limits != null;
		webSocket = null;
		limits = null;
		if (wasRegistered) {
			final long abandoned = generation;
			safely("onDisconnected", () -> listener.onDisconnected(WebSocket.NORMAL_CLOSURE, "Forced shutdown", abandoned));
		}
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
		dispatch(() -> sendOnRegistered(message));
	}

	/**
	 * Sends an answer, but only if the connection that asked for it is still the current one.
	 *
	 * <p>An answer is only meaningful to the session that issued its request id. Checking that
	 * anywhere but here would be a check followed by a send, with a reconnection free to happen in
	 * between; the comparison runs on the tunnel thread, which is also the thread that changes the
	 * generation, so the two cannot interleave.
	 *
	 * @param message          the answer
	 * @param answerGeneration the connection the request arrived on
	 */
	public void send(final M8bMessage message, final long answerGeneration) {
		dispatch(() -> {
			if (answerGeneration == generation && !stopped) {
				sendOnRegistered(message);
			} else {
				log.debug("M8B tunnel: dropping an answer from a connection that is gone (generation {}).", answerGeneration);
			}
		});
	}

	/**
	 * Drops the current connection so the next one re-registers.
	 *
	 * <p>The only way to publish a changed identity: {@code agent.register} is the one frame that
	 * carries a descriptor, and the protocol already says a reconnection rebuilds the registration
	 * from the current context. Cheap, because it happens on a configuration reload and not
	 * otherwise.
	 *
	 * @param reason logged, and sent as the close reason
	 */
	public void reconnect(final String reason) {
		dispatch(() -> {
			if (!stopped && webSocket != null) {
				log.info("M8B tunnel reconnecting. Reason: {}.", reason);
				// Neither scheduled nor counted: nothing failed. A scheduled attempt would be left
				// stale by the connect below, and a counted failure would meet the next genuine
				// outage with a backoff it did not earn.
				dropConnection(generation, WebSocket.NORMAL_CLOSURE, reason, false);
				connect(++generation);
			}
		});
	}

	// ---- tunnel thread ----

	/**
	 * Sends what a caller handed us, but not before the session is registered.
	 *
	 * <p>A socket exists from the moment the handshake completes; the server has agreed to nothing
	 * until it answers {@code agent.registered}. Anything sent in between belongs to no session the
	 * server recognises — which is exactly what a late answer from a previous connection would be.
	 * The registration frame itself does not come through here.
	 *
	 * @param message what to send
	 */
	private void sendOnRegistered(final M8bMessage message) {
		if (limits == null) {
			log.debug("M8B tunnel: dropping a '{}' sent before the session was registered.", message.type());
			return;
		}
		sendNow(message);
	}

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

	/**
	 * The largest message this connection will accept, in characters.
	 *
	 * <p>Characters rather than bytes because that is what has actually been buffered at the point
	 * the question is asked; it is the conservative reading of a byte cap, since a character never
	 * encodes to less than one byte.
	 *
	 * @return the cap the server imposed, or the default until it has imposed one
	 */
	private long maxInboundBytes() {
		final AgentRegistered current = limits;
		return current == null ? DEFAULT_MAX_INBOUND_BYTES : current.maxPayloadBytes();
	}

	/**
	 * The UTF-8 length of a fragment, counted rather than encoded.
	 *
	 * <p>Counted because the alternative is allocating a byte array per fragment purely to measure
	 * it, and the point of measuring is to refuse the frames that would be expensive.
	 *
	 * <p>Characters would be the wrong unit outright: a character is a LOWER bound on its encoded
	 * size, so 4 096 CJK characters slip past a 4 096-byte cap while occupying some 12 KiB on the
	 * wire.
	 *
	 * @param text the fragment as delivered
	 * @return how many bytes it occupies once encoded
	 */
	private static int utf8Length(final CharSequence text) {
		int bytes = 0;
		for (int index = 0; index < text.length(); index++) {
			final char character = text.charAt(index);
			if (character < 0x80) {
				bytes += 1;
			} else if (character < 0x800) {
				bytes += 2;
			} else if (Character.isHighSurrogate(character) && index + 1 < text.length()) {
				// A surrogate pair is one code point, and four bytes for the pair rather than each
				bytes += 4;
				index++;
			} else {
				bytes += 3;
			}
		}
		return bytes;
	}

	/**
	 * Runs an invocation, but not before the server has acknowledged the session.
	 *
	 * <p>Running it earlier would be worse than refusing it: the tool executes -- on a monitored
	 * host, with real side effects -- and its answer is then dropped, because there is no registered
	 * session to send it on. A peer that asks out of order gets told so instead, which is the only
	 * outcome in which nothing happens that nobody hears about.
	 *
	 * @param frameGeneration the connection it arrived on
	 * @param invoke          the invocation
	 */
	private void onInvoke(final long frameGeneration, final ToolInvoke invoke) {
		if (limits == null) {
			log.warn("M8B tunnel: refusing a tool.invoke that arrived before agent.registered.");
			// sendNow, not send: the whole point is that this session is not registered yet, and the
			// gate on the public path would drop the very message that says so.
			sendNow(new ProtocolError("MALFORMED_MESSAGE", "tool.invoke arrived before agent.registered"));
			return;
		}
		safely("onInvoke", () -> listener.onInvoke(invoke, frameGeneration));
	}

	/**
	 * Records that something arrived on this connection.
	 *
	 * <p>Anything: a ping, a pong, one fragment of a long text frame. The protocol says any inbound
	 * frame is proof the peer is alive, and it has to, because a peer that only ever pings would
	 * otherwise be closed as silent while it is demonstrably talking.
	 *
	 * @param inboundGeneration the connection the frame arrived on
	 */
	private void touched(final long inboundGeneration) {
		if (inboundGeneration == generation && !stopped) {
			lastInbound = Instant.now();
		}
	}

	private void handleFrame(final long frameGeneration, final String text) {
		touched(frameGeneration);
		if (frameGeneration != generation || stopped) {
			return;
		}
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
			case ToolInvoke invoke -> onInvoke(frameGeneration, invoke);
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
		dropConnection(dropGeneration, code, reason, true);
	}

	private void dropConnection(final long dropGeneration, final int code, final String reason, final boolean retry) {
		if (dropGeneration != generation) {
			return;
		}
		final WebSocket socket = webSocket;
		if (socket != null) {
			closeThenAbort(socket, wireCloseCode(code), reason, CLOSE_TIMEOUT);
		}
		disconnected(dropGeneration, code, reason, retry);
	}

	private void disconnected(final long lostGeneration, final int code, final String reason) {
		disconnected(lostGeneration, code, reason, true);
	}

	/**
	 * Cleans up after a lost connection, and optionally arranges the next one.
	 *
	 * @param lostGeneration the connection that ended
	 * @param code           its close code
	 * @param reason         its close reason
	 * @param retry          whether to schedule a reconnection through the retry schedule. False for
	 *                       a reconnection we asked for: scheduling one would leave a stale attempt
	 *                       behind, and counting it as a failure would inflate the backoff a genuine
	 *                       outage is then met with
	 */
	private void disconnected(final long lostGeneration, final int code, final String reason, final boolean retry) {
		cancelTimers();
		webSocket = null;
		sendChain = null;
		final boolean wasRegistered = limits != null;
		limits = null;
		if (wasRegistered) {
			safely("onDisconnected", () -> listener.onDisconnected(code, reason, lostGeneration));
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
		if (retry) {
			scheduleReconnect(lostGeneration);
		}
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
		if (!withinPayloadCap(message, text)) {
			return;
		}
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

	/**
	 * Refuses a frame the server has said it will not accept.
	 *
	 * <p>The last gate before the socket, and the only one every frame passes: a tool answer is
	 * already measured by the bridge, but a registration and a {@code hosts.updated} are not, and a
	 * fleet monitoring thousands of hosts or advertising large schemas can produce either above the
	 * cap. Sending it anyway costs the whole tunnel (close {@code 1009}), so what is lost by
	 * refusing is one frame rather than the session.
	 *
	 * <p>Refusing a registration is the awkward case, and still the right one: the alternative is a
	 * connect-and-1009 loop. Dropped, the socket simply fails its registration deadline, which is
	 * slower, quieter, and leaves the error log the only thing that explains either.
	 *
	 * @param message what is being sent, for the log line
	 * @param text    its serialized form
	 * @return whether it may go
	 */
	private boolean withinPayloadCap(final M8bMessage message, final String text) {
		final AgentRegistered current = limits;
		if (current == null) {
			// The server has not said yet, and the frame that asks it cannot wait for the answer.
			return true;
		}
		final int size = text.getBytes(StandardCharsets.UTF_8).length;
		if (size <= current.maxPayloadBytes()) {
			return true;
		}
		log.error(
			"M8B tunnel: refusing to send a '{}' of {} bytes, above the {} bytes this server accepts. " +
				"Sending it would close the tunnel; it is dropped instead.",
			message.type(),
			size,
			current.maxPayloadBytes()
		);
		return false;
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
	 * Sends a close frame and aborts the socket once it has left, or once it is clear it never
	 * will.
	 *
	 * <p>Aborting outright would fail a close frame still in flight and leave the server recording
	 * an abnormal disconnect instead of the code we mean, so the close goes first. The fallback is
	 * {@code orTimeout}'s own timer rather than a task on the tunnel executor, because
	 * {@link #stop(String)} shuts that executor down: a scheduled fallback would be cancelled with
	 * it, and this very callback would find its dispatch refused, leaving an authenticated socket
	 * alive with nothing left to close it.
	 *
	 * @param socket        the socket to close
	 * @param code          a close code the JDK client accepts
	 * @param reason        the close reason, truncated to what a frame may carry
	 * @param abortDeadline how long the close frame is given
	 * @return when the frame has left, or been given up on
	 */
	private static CompletableFuture<WebSocket> closeThenAbort(
		final WebSocket socket,
		final int code,
		final String reason,
		final Duration abortDeadline
	) {
		return socket
			.sendClose(code, closeReason(reason))
			.orTimeout(abortDeadline.toMillis(), TimeUnit.MILLISECONDS)
			.whenComplete((ws, error) -> socket.abort());
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
		private long partialBytes;
		private int fragments;

		private FrameListener(final long frameGeneration) {
			this.frameGeneration = frameGeneration;
		}

		@Override
		public void onOpen(final WebSocket socket) {
			socket.request(1);
		}

		@Override
		public CompletionStage<?> onText(final WebSocket socket, final CharSequence data, final boolean last) {
			final long cap = maxInboundBytes();
			partialBytes += utf8Length(data);
			fragments++;
			if (partialBytes > cap || fragments > MAX_INBOUND_FRAGMENTS) {
				// Deliberately WITHOUT requesting more, which is the part that matters: the
				// backpressure below only engages once a message is COMPLETE, so a peer that never
				// sets `last` would otherwise buffer the agent into an OutOfMemoryError one fragment
				// at a time, and asking for the next fragment is what lets it.
				//
				// Both bounds, because a peer chooses both how big a message is and how many pieces
				// it arrives in: an empty non-final fragment is legal and adds nothing to the size,
				// so the size bound alone would never trip on a stream of them.
				final String why =
					partialBytes > cap
						? "Message above " + cap + " bytes"
						: "Message split into more than " + MAX_INBOUND_FRAGMENTS + " fragments";
				reset();
				dispatch(() -> dropConnection(frameGeneration, CLOSE_MESSAGE_TOO_BIG, why));
				return null;
			}
			partial.append(data);
			if (!last) {
				// Fragments are always asked for immediately: the message has to be allowed to
				// finish. And a frame long enough to arrive in pieces must not be mistaken for
				// silence while it is still arriving.
				socket.request(1);
				dispatch(() -> touched(frameGeneration));
				return null;
			}
			final String text = partial.toString();
			reset();
			// DEMAND is what holds the peer back, and it is asked for only once this message has been
			// handled. The returned stage is not enough on its own: the JDK is explicit that it has
			// nothing to do with the invocation counter, so a server sending faster than the agent
			// reads -- faulty, or deliberate -- would fill the tunnel thread's unbounded queue with
			// frames nobody has looked at yet. Not requesting is the thing that actually stops it.
			final CompletableFuture<Void> handled = new CompletableFuture<>();
			final boolean taken = dispatch(() -> {
				try {
					handleFrame(frameGeneration, text);
				} finally {
					handled.complete(null);
				}
			});
			if (!taken) {
				handled.complete(null);
			}
			handled.thenRun(() -> socket.request(1));
			return handled;
		}

		/** Forgets a message, whether it completed or was refused. */
		private void reset() {
			partial.setLength(0);
			partialBytes = 0;
			fragments = 0;
		}

		@Override
		public CompletionStage<?> onPing(final WebSocket socket, final ByteBuffer message) {
			// The JDK answers the Pong itself; what it cannot know is that this counts as liveness.
			dispatch(() -> touched(frameGeneration));
			socket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onPong(final WebSocket socket, final ByteBuffer message) {
			dispatch(() -> touched(frameGeneration));
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
