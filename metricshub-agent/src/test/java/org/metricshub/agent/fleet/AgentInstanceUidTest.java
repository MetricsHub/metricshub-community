package org.metricshub.agent.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.metricshub.agent.security.PasswordEncrypt;

class AgentInstanceUidTest {

	@Test
	void fileShouldBeMetricsHubAgentUidBesideTheKeystore() {
		assertEquals(
			PasswordEncrypt.getKeyStoreFile(true).toPath().toAbsolutePath().getParent().resolve("metricshub-agent-uid"),
			AgentInstanceUid.file()
		);
	}
}
