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

class M8bConfigTest {

	private static AgentConfig deserialize(final String yaml) throws Exception {
		return JsonHelper.deserialize(
			ConfigHelper.newObjectMapper(),
			new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
			AgentConfig.class
		);
	}

	@Test
	void m8bSectionShouldBeDeserialized() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			m8b:
			  enabled: true
			  endpoint: wss://m8b.example.com/ws/agent
			  headers:
			    Authorization: Bearer my-token
			  certificateFile: /opt/metricshub/security/m8b-ca.pem
			  heartbeatInterval: 1m
			  excludedTools: [ ExecuteSshCommandline, ExecuteWinRemoteCommand ]
			"""
		);

		final M8bConfig m8b = agentConfig.getM8b();
		assertTrue(m8b.isEnabled());
		assertEquals("wss://m8b.example.com/ws/agent", m8b.getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer my-token"), m8b.getHeaders());
		assertEquals("/opt/metricshub/security/m8b-ca.pem", m8b.getCertificateFile());
		assertEquals(60, m8b.getHeartbeatInterval());
		assertEquals(Set.of("ExecuteSshCommandline", "ExecuteWinRemoteCommand"), m8b.getExcludedTools());
	}

	@Test
	void m8bShouldBeDisabledByDefault() throws Exception {
		final AgentConfig agentConfig = deserialize("loggerLevel: error\n");

		final M8bConfig m8b = agentConfig.getM8b();
		assertFalse(m8b.isEnabled());
		assertNull(m8b.getEndpoint());
		assertTrue(m8b.getHeaders().isEmpty());
		assertEquals(M8bConfig.DEFAULT_HEARTBEAT_INTERVAL, m8b.getHeartbeatInterval());
		assertTrue(m8b.getExcludedTools().isEmpty());
	}

	@Test
	void nullValuesShouldKeepDefaults() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			m8b:
			  enabled: true
			  endpoint:
			  heartbeatInterval:
			  excludedTools:
			"""
		);

		final M8bConfig m8b = agentConfig.getM8b();
		assertTrue(m8b.isEnabled());
		assertNull(m8b.getEndpoint());
		assertEquals(M8bConfig.DEFAULT_HEARTBEAT_INTERVAL, m8b.getHeartbeatInterval());
		assertTrue(m8b.getExcludedTools().isEmpty());
	}
}
