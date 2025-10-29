package org.metricshub.cli.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class VersionServiceTest {

	@Test
	void testGetVersion() throws Exception {
		assertNotNull(new VersionService().getVersion());
	}
}
