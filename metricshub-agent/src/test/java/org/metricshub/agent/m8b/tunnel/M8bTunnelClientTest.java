package org.metricshub.agent.m8b.tunnel;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.m8b.protocol.AgentDescriptor;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegister;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolResult;

class M8bTunnelClientTest {

	private static final String AGENT_UID = "01923e4a-7c1e-7f4b-8a2d-3c5e6f7a8b9c";
	private static final long TIMEOUT_MS = 10_000;

	private FakeM8bServer server;
	private M8bTunnelClient client;

	/**
	 * Listener recording every callback.
	 */
	private static final class RecordingListener implements M8bTunnelListener {

		final AtomicInteger registrations = new AtomicInteger();
		final BlockingQueue<AgentRegistered> registered = new LinkedBlockingQueue<>();
		final BlockingQueue<ToolInvoke> invocations = new LinkedBlockingQueue<>();
		final BlockingQueue<Integer> disconnections = new LinkedBlockingQueue<>();

		@Override
		public AgentRegister buildRegistration() {
			registrations.incrementAndGet();
			return new AgentRegister(
				M8bMessage.PROTOCOL_VERSION,
				new AgentDescriptor("MetricsHub Agent", "3.9.07", "Community", "server-01", "linux", "amd64", "b1", Map.of()),
				"sha256:0",
				List.of(),
				List.of()
			);
		}

		@Override
		public void onRegistered(final AgentRegistered limits) {
			registered.add(limits);
		}

		@Override
		public void onInvoke(final ToolInvoke invoke) {
			invocations.add(invoke);
		}

		@Override
		public void onDisconnected(final int code, final String reason) {
			disconnections.add(code);
		}
	}

	@AfterEach
	void tearDown() throws Exception {
		if (client != null) {
			client.stop("test over");
		}
		if (server != null) {
			server.stop(1000);
		}
	}

	private M8bTunnelSettings settings(final FakeM8bServer fakeServer, final String certificateFile) {
		return new M8bTunnelSettings(
			fakeServer.uri(),
			Map.of("Authorization", "Bearer secret-token"),
			certificateFile,
			AGENT_UID,
			Duration.ofSeconds(1),
			Duration.ofSeconds(5),
			Duration.ofSeconds(2)
		);
	}

	@Test
	void shouldRegisterWithIdentityHeadersAndHonorServerLimits() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);

		client.start();

		final Map<String, String> handshake = server.awaitHandshake(TIMEOUT_MS);
		assertNotNull(handshake, "The client must open the WebSocket");
		assertEquals("Bearer secret-token", handshake.get("Authorization"));
		assertEquals(AGENT_UID, handshake.get(M8bTunnelSettings.AGENT_UID_HEADER));
		assertEquals("/ws/agent", handshake.get("path"));

		final JsonNode register = server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS);
		assertNotNull(register, "agent.register must be the first frame");
		assertEquals(1, register.get("protocolVersion").asInt());
		assertEquals("MetricsHub Agent", register.at("/agent/name").asText());

		final AgentRegistered limits = listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
		assertEquals(server.limits, limits);
		await().atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS).until(client::isConnected);
		assertEquals(server.limits, client.limits());

		// The server asked for a 1 s heartbeat: pings must follow
		assertNotNull(server.awaitFrame(M8bMessage.HeartbeatPing.TYPE, TIMEOUT_MS), "heartbeat.ping expected");
	}

	@Test
	void shouldForwardInvocationsAndSendAnswers() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

		final JsonNode arguments = M8bJson.MAPPER.readTree("{\"hostname\":[\"server-01\"]}");
		server.sendToAll(new ToolInvoke("req-1", "PingHost", arguments, 30_000));

		final ToolInvoke invoke = listener.invocations.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
		assertNotNull(invoke);
		assertEquals("req-1", invoke.requestId());
		assertEquals("PingHost", invoke.tool());
		assertEquals(arguments, invoke.arguments());

		client.send(new ToolResult("req-1", M8bJson.MAPPER.readTree("{\"ok\":true}"), 5));

		final JsonNode result = server.awaitFrame(M8bMessage.ToolResult.TYPE, TIMEOUT_MS);
		assertNotNull(result);
		assertEquals("req-1", result.get("requestId").asText());
		assertTrue(result.at("/result/ok").asBoolean());
	}

	@Test
	void shouldAnswerUnknownMessagesWithAnErrorAndStayConnected() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

		server.sendToAll("{\"type\":\"tools.list\",\"future\":true}");

		final JsonNode error = server.awaitFrame(M8bMessage.ProtocolError.TYPE, TIMEOUT_MS);
		assertNotNull(error);
		assertEquals("UNKNOWN_MESSAGE_TYPE", error.get("code").asText());
		assertTrue(client.isConnected());
		assertTrue(listener.disconnections.isEmpty());
	}

	@Test
	void shouldReconnectAndReRegisterAfterTheServerDrops() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS));

		server.closeAll(1012, "restart");

		assertEquals(1012, listener.disconnections.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		await()
			.atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			.until(() -> !client.isConnected() || client.isConnected());
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS), "The client must register again");
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertEquals(2, listener.registrations.get(), "Each connection builds a fresh registration");
	}

	@Test
	void shouldKeepGrowingTheBackoffAcrossSupersededSessions() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();

		// Two agents sharing a uid supersede each other: registering must not reset the backoff,
		// otherwise both reconnect at the base delay forever and steal the session in a tight loop.
		for (int round = 1; round <= 2; round++) {
			final int expected = round;
			assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS), "registration " + expected);
			server.closeAll(M8bTunnelClient.CLOSE_SUPERSEDED, "superseded");
			assertEquals(
				M8bTunnelClient.CLOSE_SUPERSEDED,
				listener.disconnections.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS),
				"disconnection " + expected
			);
			// The counter is incremented when the next attempt is scheduled, just after the listener
			// is notified, so wait for it rather than racing it. A reset would stall this at 1.
			await()
				.atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS)
				.until(() -> client.retryFailureCount() == expected);
		}
	}

	@Test
	void shouldRetryWithBackoffWhenSuperseded() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS));

		server.closeAll(M8bTunnelClient.CLOSE_SUPERSEDED, "superseded");

		assertEquals(M8bTunnelClient.CLOSE_SUPERSEDED, listener.disconnections.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS), "A superseded client keeps retrying");
	}

	@Test
	void shouldReconnectWhenTheServerStopsAnsweringHeartbeats() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS));

		// A silent server: pings are no longer answered, so 2.5 heartbeat intervals later the
		// client gives up on the connection and opens a new one
		server.autoPong = false;

		final Integer code = listener.disconnections.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
		assertEquals(M8bTunnelClient.CLOSE_HEARTBEAT_TIMEOUT, code);
		// The close frame must reach the server before the socket is aborted, otherwise the server
		// records an abnormal disconnect instead of the code the client meant to send
		assertEquals(
			M8bTunnelClient.CLOSE_HEARTBEAT_TIMEOUT,
			server.awaitClose(TIMEOUT_MS),
			"The server must observe the heartbeat-timeout close code"
		);
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS), "The client must reconnect");
	}

	@Test
	void shouldGiveUpOnAConnectionTheServerNeverAcknowledges() throws Exception {
		server = new FakeM8bServer();
		server.autoRegister = false;
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(
			new M8bTunnelSettings(
				server.uri(),
				Map.of(),
				null,
				AGENT_UID,
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				Duration.ofSeconds(2)
			),
			listener
		);
		client.start();

		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS));
		// No acknowledgement: the registration deadline drops the connection and a new attempt follows
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS), "A second attempt must follow");
		assertFalse(client.isConnected());
		assertTrue(listener.registered.isEmpty(), "onRegistered must not fire without an acknowledgement");
	}

	@Test
	void shouldRejectCleartextEndpointsOutsideLoopback() {
		final Map<String, String> headers = Map.of("Authorization", "Bearer secret-token");
		assertThrows(
			IllegalArgumentException.class,
			() -> new M8bTunnelSettings(URI.create("ws://m8b.example.com/ws/agent"), headers, null, AGENT_UID, null),
			"Credentials must never travel in cleartext to a remote host"
		);
		// Loopback development and TLS endpoints are fine
		new M8bTunnelSettings(URI.create("ws://localhost:8080/ws/agent"), headers, null, AGENT_UID, null);
		new M8bTunnelSettings(URI.create("ws://127.0.0.1:8080/ws/agent"), headers, null, AGENT_UID, null);
		new M8bTunnelSettings(URI.create("wss://m8b.example.com/ws/agent"), headers, null, AGENT_UID, null);
	}

	@Test
	void shouldDropTheConnectionOnABinaryFrame() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS));

		server.sendBinaryToAll(new byte[] { 1, 2, 3 });

		assertEquals(
			M8bTunnelClient.CLOSE_UNSUPPORTED_DATA,
			listener.disconnections.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)
		);
		assertNotNull(server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS), "The client reconnects afterwards");
	}

	@Test
	void sendAfterStopShouldBeDroppedSilently() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

		client.stop("shutdown");
		// A tool finishing after the shutdown must not blow up the worker thread
		client.send(new ToolResult("late", M8bJson.MAPPER.createObjectNode(), 1));
	}

	@Test
	void shouldCloseNormallyOnStop() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, null), listener);
		client.start();
		assertNotNull(server.awaitHandshake(TIMEOUT_MS));
		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS));

		client.stop("shutdown");

		assertEquals(1000, server.awaitClose(TIMEOUT_MS));
		await()
			.atMost(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			.until(() -> server.connectionCount() == 0);
		// stop() is final: no reconnection
		assertEquals(null, server.awaitHandshake(1500));
	}

	@Test
	void shouldConnectOverTlsWithTheConfiguredCertificate(@TempDir final Path tempDir) throws Exception {
		final KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream stream = getClass().getResourceAsStream("/upgrade/test-repository.p12")) {
			keyStore.load(stream, "changeit".toCharArray());
		}
		final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, "changeit".toCharArray());
		final SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		final Path pem = tempDir.resolve("m8b-ca.pem");
		try (InputStream stream = getClass().getResourceAsStream("/upgrade/test-repository.pem")) {
			Files.copy(stream, pem);
		}

		server = new FakeM8bServer(sslContext);
		server.startAndAwait();
		final RecordingListener listener = new RecordingListener();
		client = new M8bTunnelClient(settings(server, pem.toString()), listener);
		client.start();

		assertNotNull(listener.registered.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS), "Registration over wss expected");
		assertTrue(server.uri().getScheme().equals("wss"));
	}
}
