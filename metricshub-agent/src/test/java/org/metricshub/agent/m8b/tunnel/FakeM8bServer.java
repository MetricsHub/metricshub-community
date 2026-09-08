package org.metricshub.agent.m8b.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.HeartbeatPong;

/**
 * Minimal M8B Governor for the tunnel tests: records handshakes and frames, and answers
 * {@code agent.register} and {@code heartbeat.ping} like the real server unless told otherwise.
 */
public class FakeM8bServer extends WebSocketServer {

	private final CountDownLatch started = new CountDownLatch(1);
	private final BlockingQueue<Map<String, String>> handshakes = new LinkedBlockingQueue<>();
	private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
	private final BlockingQueue<Integer> closeCodes = new LinkedBlockingQueue<>();
	private final List<WebSocket> connections = new CopyOnWriteArrayList<>();
	private final boolean secure;

	public volatile boolean autoRegister = true;
	public volatile boolean autoPong = true;
	public volatile AgentRegistered limits = new AgentRegistered(1, 1_048_576L, 2);

	public FakeM8bServer() {
		this(null);
	}

	public FakeM8bServer(final SSLContext sslContext) {
		super(new InetSocketAddress("127.0.0.1", 0));
		setReuseAddr(true);
		this.secure = sslContext != null;
		if (secure) {
			setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
		}
	}

	public void startAndAwait() throws InterruptedException {
		start();
		if (!started.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("The fake M8B server did not start");
		}
	}

	public URI uri() {
		return URI.create((secure ? "wss" : "ws") + "://127.0.0.1:" + getPort() + "/ws/agent");
	}

	public Map<String, String> awaitHandshake(final long timeoutMillis) throws InterruptedException {
		return handshakes.poll(timeoutMillis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Waits for the next frame of the given type, discarding frames of other types.
	 */
	public JsonNode awaitFrame(final String type, final long timeoutMillis) throws InterruptedException, IOException {
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (true) {
			final long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
			if (remaining <= 0) {
				return null;
			}
			final String frame = frames.poll(remaining, TimeUnit.MILLISECONDS);
			if (frame == null) {
				return null;
			}
			final JsonNode node = M8bJson.MAPPER.readTree(frame);
			if (type.equals(node.path("type").asText())) {
				return node;
			}
		}
	}

	public Integer awaitClose(final long timeoutMillis) throws InterruptedException {
		return closeCodes.poll(timeoutMillis, TimeUnit.MILLISECONDS);
	}

	public int connectionCount() {
		return connections.size();
	}

	public void sendToAll(final M8bMessage message) {
		sendToAll(M8bJson.write(message));
	}

	public void sendToAll(final String text) {
		connections.forEach(connection -> connection.send(text));
	}

	/** Sends a WebSocket control Ping -- not a protocol frame -- to every connection. */
	public void pingAll() {
		connections.forEach(WebSocket::sendPing);
	}

	public void sendBinaryToAll(final byte[] payload) {
		connections.forEach(connection -> connection.send(payload));
	}

	public void closeAll(final int code, final String reason) {
		connections.forEach(connection -> connection.close(code, reason));
	}

	@Override
	public void onStart() {
		started.countDown();
	}

	@Override
	public void onOpen(final WebSocket connection, final ClientHandshake handshake) {
		connections.add(connection);
		handshakes.add(
			Map.of(
				"Authorization",
				String.valueOf(handshake.getFieldValue("Authorization")),
				M8bTunnelSettings.AGENT_UID_HEADER,
				String.valueOf(handshake.getFieldValue(M8bTunnelSettings.AGENT_UID_HEADER)),
				"path",
				String.valueOf(handshake.getResourceDescriptor())
			)
		);
	}

	@Override
	public void onMessage(final WebSocket connection, final String message) {
		frames.add(message);
		try {
			final String type = M8bJson.MAPPER.readTree(message).path("type").asText();
			if (autoRegister && M8bMessage.AgentRegister.TYPE.equals(type)) {
				connection.send(M8bJson.write(limits));
			} else if (autoPong && M8bMessage.HeartbeatPing.TYPE.equals(type)) {
				connection.send(M8bJson.write(new HeartbeatPong()));
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void onClose(final WebSocket connection, final int code, final String reason, final boolean remote) {
		connections.remove(connection);
		closeCodes.add(code);
	}

	@Override
	public void onError(final WebSocket connection, final Exception exception) {
		// Connection errors surface through onClose; nothing to record here
	}
}
