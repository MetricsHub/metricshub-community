package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoggingHelperTest {

	@Test
	void testSanatizeMessage() {
		assertEquals("", LoggingHelper.sanatizeMessage(""));
		assertEquals(
			"{\"username\": \"${USERNAME}\",\"password**********\n\"username\": \"${USERNAME}\"",
			LoggingHelper.sanatizeMessage(
				"{\"username\": \"${USERNAME}\",\"password\": \"${PASSWORD}\"}\n\"username\": \"${USERNAME}\""
			)
		);
		assertEquals(
			"{\"username\": \"${USERNAME}\",\"password**********\n\"username\": \"${USERNAME}\"",
			LoggingHelper.sanatizeMessage("{\"username\": \"${USERNAME}\",\"password\n\"username\": \"${USERNAME}\"")
		);
	}
}
