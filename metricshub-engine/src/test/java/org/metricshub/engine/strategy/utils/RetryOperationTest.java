package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
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
	void retryClearsInterruptFlagBeforeRetryAttempt() {
		final AtomicInteger attempts = new AtomicInteger();
		final AtomicBoolean interruptedOnFirstAttempt = new AtomicBoolean();
		final AtomicBoolean interruptedOnRetry = new AtomicBoolean();

		// Simulate a stale interrupt left by a previous operation
		Thread.currentThread().interrupt();

		final RetryOperation<String> retryOperation = RetryOperation
			.<String>builder()
			.withHostname("hostname")
			.withDescription("description")
			.withDefaultValue("default")
			.withMaxRetries(1)
			.build();

		final String result = retryOperation.run(() -> {
			if (attempts.getAndIncrement() == 0) {
				interruptedOnFirstAttempt.set(Thread.currentThread().isInterrupted());
				throw new RetryableException();
			}
			interruptedOnRetry.set(Thread.currentThread().isInterrupted());
			return "success";
		});

		assertEquals("success", result);
		assertTrue(interruptedOnFirstAttempt.get());
		assertFalse(interruptedOnRetry.get());
		assertFalse(Thread.currentThread().isInterrupted());
	}
}
