package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class EnvToolTest {

	@Test
	void testGet() {
		final EnvTool envTool = new EnvTool();
		final String path = envTool.get("JAVA_HOME");
		assertNotNull(path, "JAVA_HOME environment variable should not be null");
	}
}
