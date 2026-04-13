package org.metricshub.agent.context;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentInfoTest {

	@Test
	void testConstructor() {
		final AgentInfo agentInfo = new AgentInfo();
		final Map<String, String> metricAttributes = agentInfo.getAttributes();
		assertNotNull(metricAttributes);
		assertNotNull(metricAttributes.get("name"));
		assertNotNull(metricAttributes.get("version"));
		assertNotNull(metricAttributes.get("build_number"));
		assertNotNull(metricAttributes.get("build_date"));
		assertNotNull(metricAttributes.get("cc_version"));
		assertNotNull(metricAttributes.get("service.name"));
		assertNotNull(metricAttributes.get("host.name"));
		assertNotNull(metricAttributes.get("agent.host.name"));
		assertNotNull(metricAttributes.get("host.type"));
		assertNotNull(metricAttributes.get("os.type"));
	}
}
