package org.metricshub.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;

class CentralConfigTest {

	private static CentralConfig deserialize(final String yaml) throws Exception {
		return JsonHelper.deserialize(
			ConfigHelper.newObjectMapper(),
			new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
			AgentConfig.class
		).getCentral();
	}

	@Test
	void oneServerShouldBeWrittenOnce() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com:4320
			  headers:
			    Authorization: Bearer dev-agent-secret
			  certificateFile: /opt/metricshub/security/central-ca.pem
			"""
		);

		// Both channels reach the same server, so both are addressed, credentialed and trusted from
		// what was written once -- and only the tunnel's scheme differs, because only it is a socket
		assertEquals("https://central.example.com:4320/v1/opamp", central.opamp().getEndpoint());
		assertEquals("wss://central.example.com:4320/ws/agent", central.tunnel().getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer dev-agent-secret"), central.opamp().getHeaders());
		assertEquals(Map.of("Authorization", "Bearer dev-agent-secret"), central.tunnel().getHeaders());
		assertEquals("/opt/metricshub/security/central-ca.pem", central.opamp().getCertificateFile());
		assertEquals("/opt/metricshub/security/central-ca.pem", central.tunnel().getCertificateFile());
	}

	@Test
	void plainHttpShouldGiveAPlainSocket() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: http://127.0.0.1:4320
			"""
		);

		assertEquals("http://127.0.0.1:4320/v1/opamp", central.opamp().getEndpoint());
		assertEquals("ws://127.0.0.1:4320/ws/agent", central.tunnel().getEndpoint());
	}

	@Test
	void aTrailingSlashShouldNotDoubleUp() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com/
			"""
		);

		assertEquals("https://central.example.com/v1/opamp", central.opamp().getEndpoint());
		assertEquals("wss://central.example.com/ws/agent", central.tunnel().getEndpoint());
	}

	@Test
	void aChannelShouldBeAbleToLeaveTheRoof() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com
			  headers:
			    Authorization: Bearer shared
			  certificateFile: /shared/ca.pem
			  tunnel:
			    endpoint: wss://tunnel.example.net:9443/ws/agent
			    headers:
			      Authorization: Bearer its-own
			    certificateFile: /its/own/ca.pem
			"""
		);

		// The deployment where the two channels do not come out of the same ingress
		assertEquals("https://central.example.com/v1/opamp", central.opamp().getEndpoint());
		assertEquals("wss://tunnel.example.net:9443/ws/agent", central.tunnel().getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer its-own"), central.tunnel().getHeaders());
		assertEquals("/its/own/ca.pem", central.tunnel().getCertificateFile());
		// and the one that stayed is untouched by it
		assertEquals(Map.of("Authorization", "Bearer shared"), central.opamp().getHeaders());
		assertEquals("/shared/ca.pem", central.opamp().getCertificateFile());
	}

	@Test
	void aHeaderWithoutANameShouldNotStopBothChannels() {
		final Map<String, String> nameless = new HashMap<>();
		nameless.put(null, "Bearer nowhere");
		nameless.put("Authorization", "Bearer real");
		final CentralConfig central = CentralConfig.builder()
			.enabled(true)
			.url("https://central.example.com")
			.headers(nameless)
			.build();

		// Ordering case-insensitively means a comparator, and a comparator cannot order a name that
		// is not there. Left to throw, one nameless entry would stop OpAMP and the tunnel alike --
		// where the plain map this replaced simply held it for `FleetHeaders.decrypt` to drop
		assertEquals(Map.of("Authorization", "Bearer real"), central.opamp().getHeaders());
		assertEquals(Map.of("Authorization", "Bearer real"), central.tunnel().getHeaders());
	}

	@Test
	void aPathWrittenWithoutItsSlashShouldStillBeAPath() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com
			  opamp:
			    path: fleet/v1/opamp
			  tunnel:
			    path: fleet/ws/agent
			"""
		);

		// Welded on, the path becomes part of the HOST -- a name that resolves to nothing, which
		// reads like a DNS problem rather than the missing slash it is
		assertEquals("https://central.example.com/fleet/v1/opamp", central.opamp().getEndpoint());
		assertEquals("wss://central.example.com/fleet/ws/agent", central.tunnel().getEndpoint());
	}

	@Test
	void aChannelShouldOverrideAHeaderItSpellsDifferently() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com
			  headers:
			    Authorization: Bearer shared
			  tunnel:
			    headers:
			      authorization: Bearer its-own
			"""
		);

		// A header name is case-insensitive, and `HttpRequest.Builder.header` APPENDS: kept apart,
		// these two would send the credential twice in one request and a server reading one value
		// refuses it. The override has to hold whatever the operator capitalised
		assertEquals(1, central.tunnel().getHeaders().size());
		assertEquals("Bearer its-own", central.tunnel().getHeaders().get("Authorization"));
		assertEquals("Bearer its-own", central.tunnel().getHeaders().get("authorization"));
		// and the channel that did not override is untouched
		assertEquals(Map.of("Authorization", "Bearer shared"), central.opamp().getHeaders());
	}

	@Test
	void aPathShouldBeAbleToMoveWithoutTheHost() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com
			  opamp:
			    path: /fleet/v1/opamp
			  tunnel:
			    path: /fleet/ws/agent
			"""
		);

		assertEquals("https://central.example.com/fleet/v1/opamp", central.opamp().getEndpoint());
		assertEquals("wss://central.example.com/fleet/ws/agent", central.tunnel().getEndpoint());
	}

	@Test
	void nothingShouldRunWithoutTheRoof() throws Exception {
		final CentralConfig central = deserialize(
			"""
			central:
			  url: https://central.example.com
			  opamp:
			    enabled: true
			  tunnel:
			    enabled: true
			"""
		);

		// A channel cannot let itself in: `central.enabled` is the door, and it is shut by default
		assertFalse(central.opamp().isEnabled());
		assertFalse(central.tunnel().isEnabled());
	}

	@Test
	void anAbsentSectionShouldStillAnswer() throws Exception {
		final CentralConfig central = deserialize("loggerLevel: error\n");

		// Nothing configured is not nothing to read: every caller asks the same two methods
		assertFalse(central.isEnabled());
		assertFalse(central.opamp().isEnabled());
		assertFalse(central.tunnel().isEnabled());
		assertNull(central.opamp().getEndpoint());
		assertNull(central.tunnel().getEndpoint());
		assertTrue(central.getUpgrade().isEnabled());
	}
}
