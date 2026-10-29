package org.metricshub.engine.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolParameterTest {

	@Test
	void testTypeDefaultsToStringAndIsNormalized() {
		assertEquals("string", new ToolParameter(null, null, false, null).type());
		assertEquals("string", new ToolParameter(" ", null, false, null).type());
		assertEquals("integer", new ToolParameter(" Integer ", null, false, null).type());
	}

	@Test
	void testUnknownTypeIsRejected() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			new ToolParameter("intger", "A typo", false, null)
		);
		assertTrue(exception.getMessage().contains("intger"), exception.getMessage());
	}
}
