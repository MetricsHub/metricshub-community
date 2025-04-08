package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JUtilsTest {

	@Test
	void encodeSha256OkTest() {
		final String input = "username_password";
		final String result = "c557c390026e598a852fe5d2fcdad83d7c759451b0aa355854bbc57db4b5e817"; //SHA-256 of "username_password"
		assertEquals(result, JUtils.encodeSha256(input));
	}

	@Test
	void encodeSha256EmptyTest() {
		assertNotNull(JUtils.encodeSha256(""));
	}

	@Test
	void encodeSha256NullTest() {
		assertNull(JUtils.encodeSha256(null));
	}
}
