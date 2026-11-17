package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OsCommandResultTest {

	/**
	 * The result of a command has LF line endings, whatever the host. A carriage return that no line feed follows
	 * stays. CR CR LF is one line ending too.
	 */
	@Test
	void resultIsStoredWithLfLineEndings() {
		assertEquals("a;b\n\nc\rd\n", new OsCommandResult("a;b\r\n\r\nc\rd\r\n", "cmd").getResult());
		assertEquals("a\nb", new OsCommandResult("a\r\r\nb", "cmd").getResult());
		assertEquals(new OsCommandResult("a\nb", "cmd"), new OsCommandResult("a\r\nb", "cmd"));
		assertNull(new OsCommandResult(null, "cmd").getResult());
	}
}
