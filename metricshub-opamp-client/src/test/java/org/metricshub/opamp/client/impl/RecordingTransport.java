package org.metricshub.opamp.client.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.metricshub.opamp.client.http.OpampTransport;
import org.metricshub.opamp.client.http.TransportResponse;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.ServerToAgent;

/**
 * Fake transport for unit tests: records every {@code AgentToServer} message and replies with
 * scripted responses. When the script is exhausted, replies with an empty {@code ServerToAgent}.
 */
class RecordingTransport implements OpampTransport {

	/**
	 * One scripted exchange: either a response or an {@link IOException} to throw.
	 */
	record ScriptedReply(TransportResponse response, IOException failure) {
		static ScriptedReply ok(final ServerToAgent serverToAgent) {
			return new ScriptedReply(new TransportResponse(200, serverToAgent.toByteArray(), Optional.empty()), null);
		}

		static ScriptedReply httpError(final int statusCode, final Duration retryAfter) {
			return new ScriptedReply(new TransportResponse(statusCode, new byte[0], Optional.ofNullable(retryAfter)), null);
		}

		static ScriptedReply networkFailure() {
			return new ScriptedReply(null, new IOException("Simulated network failure"));
		}
	}

	private final BlockingQueue<AgentToServer> requests = new LinkedBlockingQueue<>();
	private final BlockingQueue<ScriptedReply> script = new LinkedBlockingQueue<>();
	private volatile boolean closed;

	void enqueue(final ScriptedReply reply) {
		script.add(reply);
	}

	/**
	 * Awaits the next recorded request, failing after the given timeout.
	 */
	AgentToServer awaitRequest(final Duration timeout) throws InterruptedException {
		final AgentToServer request = requests.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (request == null) {
			throw new AssertionError("No OpAMP request received within " + timeout);
		}
		return request;
	}

	boolean isClosed() {
		return closed;
	}

	@Override
	public TransportResponse send(final byte[] agentToServerBytes) throws IOException {
		try {
			requests.add(AgentToServer.parseFrom(agentToServerBytes));
		} catch (InvalidProtocolBufferException e) {
			throw new AssertionError("The client sent an unparseable AgentToServer message", e);
		}
		final ScriptedReply reply = script.poll();
		if (reply == null) {
			return new TransportResponse(200, ServerToAgent.getDefaultInstance().toByteArray(), Optional.empty());
		}
		if (reply.failure() != null) {
			throw reply.failure();
		}
		return reply.response();
	}

	@Override
	public void close() {
		closed = true;
	}
}
