package org.metricshub.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;

class OpAmpConfigTest {

	private static AgentConfig deserialize(final String yaml) throws Exception {
		return JsonHelper.deserialize(
			ConfigHelper.newObjectMapper(),
			new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
			AgentConfig.class
		);
	}

	@Test
	void opampChannelShouldBeDeserialized() throws Exception {
		final AgentConfig agentConfig = deserialize(
			"""
			central:
			  enabled: true
			  opamp:
			    endpoint: https://opamp.example.com/v1/opamp
			    headers:
			      Authorization: Bearer my-token
			    certificateFile: /opt/metricshub/security/opamp-ca.pem
			    pollInterval: 1m
			    requestTimeout: 20
			    reportHealth: false
			"""
		);

		final OpAmpConfig opamp = agentConfig.getCentral().getOpamp();
		assertTrue(opamp.isEnabled());
		assertEquals("https://opamp.example.com/v1/opamp", opamp.getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer my-token"), opamp.getHeaders());
		assertEquals("/opt/metricshub/security/opamp-ca.pem", opamp.getCertificateFile());
		assertEquals(60, opamp.getPollInterval());
		assertEquals(20, opamp.getRequestTimeout());
		assertFalse(opamp.isReportHealth());
	}

	@Test
	void opampShouldBeDisabledByDefault() throws Exception {
		final AgentConfig agentConfig = deserialize("loggerLevel: error\n");

		// The channel says yes and the roof it hangs from says nothing, so nothing runs
		final OpAmpConfig opamp = agentConfig.getCentral().opamp();
		assertFalse(opamp.isEnabled());
		assertEquals(OpAmpConfig.DEFAULT_POLL_INTERVAL, opamp.getPollInterval());
		assertEquals(OpAmpConfig.DEFAULT_REQUEST_TIMEOUT, opamp.getRequestTimeout());
		assertTrue(opamp.isReportHealth());
	}
}
