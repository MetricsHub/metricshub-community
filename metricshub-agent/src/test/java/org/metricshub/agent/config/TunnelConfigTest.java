package org.metricshub.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;

class TunnelConfigTest {

	private static AgentConfig deserialize(final String yaml) throws Exception {
		return JsonHelper.deserialize(
			ConfigHelper.newObjectMapper(),
			new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
			AgentConfig.class
		);
	}

	@Test
	void tunnelSectionShouldBeDeserialized() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  enabled: true
			  tunnel:
			    endpoint: wss://central.example.com/ws/agent
			    headers:
			      Authorization: Bearer my-token
			    certificateFile: /opt/metricshub/security/central-ca.pem
			    heartbeatInterval: 1m
			    excludedTools: [ ExecuteSshCommandline, ExecuteWinRemoteCommand ]
			"""
		);

		final TunnelConfig tunnel = agentConfig.getCentral().getTunnel();
		assertTrue(tunnel.isEnabled());
		assertEquals("wss://central.example.com/ws/agent", tunnel.getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer my-token"), tunnel.getHeaders());
		assertEquals("/opt/metricshub/security/central-ca.pem", tunnel.getCertificateFile());
		assertEquals(60, tunnel.getHeartbeatInterval());
		assertEquals(Set.of("ExecuteSshCommandline", "ExecuteWinRemoteCommand"), tunnel.getExcludedTools());
	}

	@Test
	void tunnelShouldBeDisabledByDefault() throws Exception {
		final AgentConfig agentConfig = deserialize("loggerLevel: error\n");

		// The channel says yes and the roof it hangs from says nothing, so nothing runs
		final TunnelConfig tunnel = agentConfig.getCentral().tunnel();
		assertFalse(tunnel.isEnabled());
		assertNull(tunnel.getEndpoint());
		assertTrue(tunnel.getHeaders().isEmpty());
		assertEquals(TunnelConfig.DEFAULT_HEARTBEAT_INTERVAL, tunnel.getHeartbeatInterval());
		assertTrue(tunnel.getExcludedTools().isEmpty());
	}

	@Test
	void nullValuesShouldKeepDefaults() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  enabled: true
			  tunnel:
			    endpoint:
			    heartbeatInterval:
			    excludedTools:
			"""
		);

		final TunnelConfig tunnel = agentConfig.getCentral().getTunnel();
		assertTrue(tunnel.isEnabled());
		assertNull(tunnel.getEndpoint());
		assertEquals(TunnelConfig.DEFAULT_PATH, tunnel.getPath());
		assertEquals(TunnelConfig.DEFAULT_HEARTBEAT_INTERVAL, tunnel.getHeartbeatInterval());
		assertTrue(tunnel.getExcludedTools().isEmpty());
	}

	@Test
	void tunnelShouldBeRefusableOnItsOwn() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  enabled: true
			  url: https://central.example.com
			  tunnel:
			    enabled: false
			"""
		);

		// Fleet management without exposing this agent's tools to the Governor: said on the channel,
		// and the roof being on does not overrule it
		assertFalse(agentConfig.getCentral().tunnel().isEnabled());
		assertTrue(agentConfig.getCentral().opamp().isEnabled());
	}
}
