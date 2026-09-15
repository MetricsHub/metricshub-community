package org.metricshub.agent.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionHelperTest {

	@Test
	void normalizeShouldStripQualifiers() {
		assertEquals("3.9.05", VersionHelper.normalize("3.9.05-SNAPSHOT"));
		assertEquals("3.9.05", VersionHelper.normalize(" 3.9.05 "));
		assertEquals("", VersionHelper.normalize(null));
	}

	@Test
	void isSameVersionShouldIgnoreQualifiers() {
		assertTrue(VersionHelper.isSameVersion("3.9.05-SNAPSHOT", "3.9.05"));
		assertFalse(VersionHelper.isSameVersion("3.9.05", "3.9.06"));
	}

	@Test
	void compareShouldOrderNumerically() {
		assertTrue(VersionHelper.compare("3.10.00", "3.9.06") > 0);
		assertTrue(VersionHelper.compare("3.9.06", "3.10.00") < 0);
		assertEquals(0, VersionHelper.compare("3.9.05-SNAPSHOT", "3.9.05"));
		assertTrue(VersionHelper.compare("3.9.05.1", "3.9.05") > 0);
		assertTrue(VersionHelper.compare("3.9", "3.9.01") < 0);
	}
}
