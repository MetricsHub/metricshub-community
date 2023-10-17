package com.sentrysoftware.metricshub.agent.process.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sentrysoftware.metricshub.agent.process.config.Slf4jLevel;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class Slf4jStreamProcessorTest {

	@Test
	void test() {
		assertDoesNotThrow(() ->
			ProcessorHelper.logger(LoggerFactory.getLogger(Slf4jStreamProcessorTest.class), Slf4jLevel.DEBUG).process(null)
		);
	}
}
