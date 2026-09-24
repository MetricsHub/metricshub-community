package org.metricshub.opamp.client.it;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.ServerToAgent;

/**
 * Minimal in-process OpAMP server for integration tests, built on the JDK HTTP server. Records
 * every {@code AgentToServer} message and replies with scripted {@code ServerToAgent} responses
 * (or scripted HTTP errors). When the script is exhausted, replies with an empty
 * {@code ServerToAgent}.
 */
class FakeOpampServer implements AutoCloseable {

	/**
	 * One scripted reply: either a {@code ServerToAgent} message or an HTTP error.
	 */
	record ScriptedReply(ServerToAgent message, int httpStatus, String retryAfter) {
		static ScriptedReply ok(final ServerToAgent message) {
			return new ScriptedReply(message, 200, null);
		}

		static ScriptedReply httpError(final int status, final String retryAfter) {
			return new ScriptedReply(null, status, retryAfter);
		}
	}

	/**
	 * One recorded exchange: the received message and the request headers of interest.
	 */
	record RecordedRequest(AgentToServer message, String contentType, String authorization) {}

	private final HttpServer server;
	private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
	private final BlockingQueue<ScriptedReply> script = new LinkedBlockingQueue<>();

	FakeOpampServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/opamp", this::handle);
		server.start();
	}

	URI endpoint() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/opamp");
	}

	void enqueue(final ScriptedReply reply) {
		script.add(reply);
	}

	RecordedRequest awaitRequest(final long timeoutMillis) throws InterruptedException {
		final RecordedRequest request = requests.poll(timeoutMillis, TimeUnit.MILLISECONDS);
		if (request == null) {
			throw new AssertionError("No OpAMP request received within " + timeoutMillis + " ms");
		}
		return request;
	}

	private void handle(final HttpExchange exchange) throws IOException {
		final byte[] body = exchange.getRequestBody().readAllBytes();
		requests.add(
			new RecordedRequest(
				AgentToServer.parseFrom(body),
				exchange.getRequestHeaders().getFirst("Content-Type"),
				exchange.getRequestHeaders().getFirst("Authorization")
			)
		);

		final ScriptedReply reply = script.poll();
		if (reply != null && reply.message() == null) {
			if (reply.retryAfter() != null) {
				exchange.getResponseHeaders().set("Retry-After", reply.retryAfter());
			}
			exchange.sendResponseHeaders(reply.httpStatus(), -1);
			exchange.close();
			return;
		}

		final ServerToAgent response = reply != null ? reply.message() : ServerToAgent.getDefaultInstance();
		final byte[] responseBytes = response.toByteArray();
		exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
		exchange.sendResponseHeaders(200, responseBytes.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(responseBytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
