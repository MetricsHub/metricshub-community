package org.metricshub.agent.process.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.process.config.Slf4jLevel;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Slf4jSafeStreamProcessorTest {

	@Test
	void test() {
		final StringBuilder builder = new StringBuilder();
		final Slf4jTestStreamProcessor destination = new Slf4jTestStreamProcessor(
			LoggerFactory.getLogger(OtelCollectorProcessService.class),
			Slf4jLevel.INFO,
			builder
		);
		assertDoesNotThrow(() -> new Slf4jSafeStreamProcessor(destination).process(null));
		assertEquals("null", builder.toString());
		builder.setLength(0);
		assertDoesNotThrow(() -> new Slf4jSafeStreamProcessor(destination).process(""));
		assertEquals("", builder.toString());
		builder.setLength(0);
		assertDoesNotThrow(() -> new Slf4jSafeStreamProcessor(destination).process("{}"));
		assertEquals("{ }", builder.toString());
		builder.setLength(0);
	}

	class Slf4jTestStreamProcessor extends Slf4jStreamProcessor {

		private StringBuilder builder;

		public Slf4jTestStreamProcessor(@NonNull Logger logger, @NonNull Slf4jLevel level, StringBuilder builder) {
			super(logger, level);
			this.builder = builder;
		}

		@Override
		public void process(String block) {
			builder.append(block);
		}
	}
}
