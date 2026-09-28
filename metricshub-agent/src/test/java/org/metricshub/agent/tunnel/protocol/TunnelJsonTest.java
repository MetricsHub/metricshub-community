package org.metricshub.agent.tunnel.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.AgentRegister;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.AgentRegistered;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.HeartbeatPing;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.HeartbeatPong;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.HostsUpdated;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.ProtocolError;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.ToolError;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.ToolInvoke;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.ToolResult;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.Unknown;

class TunnelJsonTest {

	private static final String GOLDEN_REGISTER = "/tunnel/agent-register.json";

	private static AgentRegister sampleRegister() throws IOException {
		final List<ToolDescriptor> tools = List.of(
			new ToolDescriptor(
				"ListHosts",
				"Lists hosts",
				TunnelJson.MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}")
			),
			new ToolDescriptor(
				"PingHost",
				"Pings hosts",
				TunnelJson.MAPPER.readTree(
					"{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"hostname\"]}"
				)
			)
		);
		return new AgentRegister(
			TunnelMessage.PROTOCOL_VERSION,
			new AgentDescriptor(
				"MetricsHub Agent",
				"3.9.07",
				"Community",
				"server-01",
				"linux",
				"amd64",
				"abcdef12",
				Map.of("service.name", "MetricsHub Agent", "version", "3.9.07", "host.name", "server-01")
			),
			TunnelJson.fingerprint(tools),
			tools,
			List.of(
				new HostDescriptor(
					"paris-host1",
					"paris",
					Map.of("ssh", "paris-host1.example.com"),
					Map.of("host.name", "paris-host1.example.com", "host.type", "linux")
				)
			)
		);
	}

	@Test
	void agentRegisterShouldMatchGoldenFixture() throws IOException {
		final JsonNode expected;
		try (var stream = TunnelJsonTest.class.getResourceAsStream(GOLDEN_REGISTER)) {
			expected = TunnelJson.MAPPER.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}

		final JsonNode actual = TunnelJson.MAPPER.readTree(TunnelJson.write(sampleRegister()));

		assertEquals(expected, actual, "The agent.register wire shape is a contract shared with the fleet");
	}

	@Test
	void everyMessageShouldRoundTrip() throws IOException {
		final JsonNode args = TunnelJson.MAPPER.readTree("{\"hostname\":[\"server-01\"]}");
		final List<TunnelMessage> messages = List.of(
			sampleRegister(),
			new AgentRegistered(30, 8_388_608L, 4),
			new HeartbeatPing(),
			new HeartbeatPong(),
			new HostsUpdated(List.of()),
			new ToolInvoke("req-1", "PingHost", args, 60_000L),
			new ToolResult("req-1", args, 12L),
			new ToolError("req-1", ToolErrorCode.TIMEOUT, "Timed out after 60000 ms"),
			new ProtocolError("PROTOCOL_ERROR", "Unexpected frame")
		);

		for (final TunnelMessage message : messages) {
			final String json = TunnelJson.write(message);
			assertTrue(json.contains("\"type\":\"" + message.type() + "\""), json);
			assertEquals(1, json.split("\"type\":\"" + message.type() + "\"", -1).length - 1, "type written once: " + json);
			assertEquals(message, TunnelJson.read(json), json);
		}
	}

	@Test
	void unknownTypeShouldNotFail() throws IOException {
		final TunnelMessage message = TunnelJson.read("{\"type\":\"tools.updated\",\"tools\":[]}");

		final Unknown unknown = assertInstanceOf(Unknown.class, message);
		assertEquals("tools.updated", unknown.type());
	}

	@Test
	void unknownPropertiesShouldBeIgnored() throws IOException {
		final TunnelMessage message = TunnelJson.read(
			"{\"type\":\"agent.registered\",\"heartbeatIntervalSeconds\":15,\"maxPayloadBytes\":1024,\"maxInFlight\":2,\"future\":true}"
		);

		assertEquals(new AgentRegistered(15, 1024, 2), message);
	}

	@Test
	void fingerprintShouldIgnoreKeyOrderAndDetectChanges() throws IOException {
		final JsonNode a = TunnelJson.MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}");
		final JsonNode b = TunnelJson.MAPPER.readTree("{\"properties\":{\"x\":{\"type\":\"string\"}},\"type\":\"object\"}");
		final JsonNode c = TunnelJson.MAPPER.readTree("{\"properties\":{\"x\":{\"type\":\"number\"}},\"type\":\"object\"}");

		assertEquals(
			TunnelJson.fingerprint(List.of(new ToolDescriptor("T", "d", a))),
			TunnelJson.fingerprint(List.of(new ToolDescriptor("T", "d", b)))
		);
		assertNotEquals(
			TunnelJson.fingerprint(List.of(new ToolDescriptor("T", "d", a))),
			TunnelJson.fingerprint(List.of(new ToolDescriptor("T", "d", c)))
		);
		assertTrue(TunnelJson.fingerprint(List.of()).matches("sha256:[0-9a-f]{64}"));
	}
}
