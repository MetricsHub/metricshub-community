package org.metricshub.agent.process.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ConsoleStreamProcessorTest {

	@Test
	void test() {
		final String block = "test process output line";
		{
			final ConsoleStreamProcessor processor = new ConsoleStreamProcessor(false);
			assertDoesNotThrow(() -> processor.process(block));
		}

		{
			final ConsoleStreamProcessor processor = new ConsoleStreamProcessor(true);
			assertDoesNotThrow(() -> processor.process(block));
		}
	}
}
