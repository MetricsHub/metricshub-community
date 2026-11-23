package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.RetryableException;

class RetryOperationTest {

	@AfterEach
	void clearInterruptFlag() {
		Thread.interrupted();
	}

	@Test
	void interruptedThreadIsNotRetried() {
		final AtomicInteger attempts = new AtomicInteger();

		// The job deadline expired during the first attempt
		final String result = RetryOperation.<String>builder()
			.withHostname("hostname")
			.withDescription("description")
			.withDefaultValue("default")
			.withMaxRetries(1)
			.build()
			.run(() -> {
				attempts.incrementAndGet();
				Thread.currentThread().interrupt();
				throw new RetryableException();
			});

		assertEquals("default", result);
		assertEquals(1, attempts.get());
		assertTrue(Thread.currentThread().isInterrupted());
	}

	@Test
	void retrySucceeds() {
		final AtomicInteger attempts = new AtomicInteger();

		final RetryOperation<String> retryOperation = RetryOperation.<String>builder()
			.withHostname("hostname")
			.withDescription("description")
			.withDefaultValue("default")
			.withMaxRetries(1)
			.build();

		final String result = retryOperation.run(() -> {
			if (attempts.getAndIncrement() == 0) {
				throw new RetryableException();
			}
			return "success";
		});

		assertEquals("success", result);
		assertEquals(2, attempts.get());
	}
}
