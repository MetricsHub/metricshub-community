package org.metricshub.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;

class UpgradeConfigTest {

	private static AgentConfig deserialize(final String yaml) throws Exception {
		return JsonHelper.deserialize(
			ConfigHelper.newObjectMapper(),
			new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
			AgentConfig.class
		);
	}

	@Test
	void upgradeSectionShouldBeDeserialized() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  upgrade:
			    enabled: true
			    hostAllowlist: [ nexus.example.com ]
			    downloadHeaders:
			      nexus.example.com:
			        Authorization: Basic cmVhZGVyOnNlY3JldA==
			        X-Repo-Token: my-token
			"""
		);

		final UpgradeConfig upgrade = agentConfig.getCentral().getUpgrade();
		assertTrue(upgrade.isEnabled());
		assertEquals(
			Map.of("nexus.example.com", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==", "X-Repo-Token", "my-token")),
			upgrade.getDownloadHeaders()
		);
	}

	@Test
	void downloadHeadersShouldAcceptAuthorityKeysWithAPort() throws Exception {
		// The shipped examples show 'nexus.example.com:8443:' as a plain YAML key; this pins
		// that the parser reads it as one key, not a nested mapping.
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  upgrade:
			    downloadHeaders:
			      nexus.example.com:8443:
			        Authorization: Basic cmVhZGVyOnNlY3JldA==
			"""
		);

		assertEquals(
			Map.of("nexus.example.com:8443", Map.of("Authorization", "Basic cmVhZGVyOnNlY3JldA==")),
			agentConfig.getCentral().getUpgrade().getDownloadHeaders()
		);
	}

	@Test
	void downloadHeadersShouldDefaultToEmpty() throws Exception {
		final AgentConfig agentConfig = deserialize("loggerLevel: error\n");

		assertEquals(Map.of(), agentConfig.getCentral().getUpgrade().getDownloadHeaders());
	}

	@Test
	void nullDownloadHeadersShouldKeepTheDefault() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  upgrade:
			    downloadHeaders:
			"""
		);

		assertEquals(Map.of(), agentConfig.getCentral().getUpgrade().getDownloadHeaders());
	}
}
