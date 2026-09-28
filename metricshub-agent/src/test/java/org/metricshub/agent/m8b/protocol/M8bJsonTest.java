package org.metricshub.agent.m8b.protocol;

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
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegister;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.HeartbeatPing;
import org.metricshub.agent.m8b.protocol.M8bMessage.HeartbeatPong;
import org.metricshub.agent.m8b.protocol.M8bMessage.HostsUpdated;
import org.metricshub.agent.m8b.protocol.M8bMessage.ProtocolError;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolError;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolResult;
import org.metricshub.agent.m8b.protocol.M8bMessage.Unknown;

class M8bJsonTest {

	private static final String GOLDEN_REGISTER = "/m8b/agent-register.json";

	private static AgentRegister sampleRegister() throws IOException {
		final List<ToolDescriptor> tools = List.of(
			new ToolDescriptor(
				"ListHosts",
				"Lists hosts",
				M8bJson.MAPPER.readTree("{\"type\":\"object\",\"properties\":{}}")
			),
			new ToolDescriptor(
				"PingHost",
				"Pings hosts",
				M8bJson.MAPPER.readTree(
					"{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"hostname\"]}"
				)
			)
		);
		return new AgentRegister(
			M8bMessage.PROTOCOL_VERSION,
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
			M8bJson.fingerprint(tools),
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
		try (var stream = M8bJsonTest.class.getResourceAsStream(GOLDEN_REGISTER)) {
			expected = M8bJson.MAPPER.readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}

		final JsonNode actual = M8bJson.MAPPER.readTree(M8bJson.write(sampleRegister()));

		assertEquals(expected, actual, "The agent.register wire shape is a contract shared with the fleet");
	}

	@Test
	void everyMessageShouldRoundTrip() throws IOException {
		final JsonNode args = M8bJson.MAPPER.readTree("{\"hostname\":[\"server-01\"]}");
		final List<M8bMessage> messages = List.of(
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

		for (final M8bMessage message : messages) {
			final String json = M8bJson.write(message);
			assertTrue(json.contains("\"type\":\"" + message.type() + "\""), json);
			assertEquals(1, json.split("\"type\":\"" + message.type() + "\"", -1).length - 1, "type written once: " + json);
			assertEquals(message, M8bJson.read(json), json);
		}
	}

	@Test
	void unknownTypeShouldNotFail() throws IOException {
		final M8bMessage message = M8bJson.read("{\"type\":\"tools.updated\",\"tools\":[]}");

		final Unknown unknown = assertInstanceOf(Unknown.class, message);
		assertEquals("tools.updated", unknown.type());
	}

	@Test
	void unknownPropertiesShouldBeIgnored() throws IOException {
		final M8bMessage message = M8bJson.read(
			"{\"type\":\"agent.registered\",\"heartbeatIntervalSeconds\":15,\"maxPayloadBytes\":1024,\"maxInFlight\":2,\"future\":true}"
		);

		assertEquals(new AgentRegistered(15, 1024, 2), message);
	}

	@Test
	void fingerprintShouldIgnoreKeyOrderAndDetectChanges() throws IOException {
		final JsonNode a = M8bJson.MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}");
		final JsonNode b = M8bJson.MAPPER.readTree("{\"properties\":{\"x\":{\"type\":\"string\"}},\"type\":\"object\"}");
		final JsonNode c = M8bJson.MAPPER.readTree("{\"properties\":{\"x\":{\"type\":\"number\"}},\"type\":\"object\"}");

		assertEquals(
			M8bJson.fingerprint(List.of(new ToolDescriptor("T", "d", a))),
			M8bJson.fingerprint(List.of(new ToolDescriptor("T", "d", b)))
		);
		assertNotEquals(
			M8bJson.fingerprint(List.of(new ToolDescriptor("T", "d", a))),
			M8bJson.fingerprint(List.of(new ToolDescriptor("T", "d", c)))
		);
		assertTrue(M8bJson.fingerprint(List.of()).matches("sha256:[0-9a-f]{64}"));
	}
}
