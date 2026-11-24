package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.RetryableException;
import org.metricshub.engine.common.helpers.ThreadHelper;

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

	@Test
	void shelteredTaskGivenUpOnIsNotRetriedEvenWhenItsInterruptIsSwallowed() throws InterruptedException {
		final AtomicInteger attempts = new AtomicInteger();
		final CountDownLatch givenUp = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(1);
		final AtomicReference<String> result = new AtomicReference<>();

		// A beforeAll or afterAll of a cancelled run, given up on by its shelter after its grace
		Thread.currentThread().interrupt();
		ThreadHelper.shelter(() -> {
			result.set(
				RetryOperation.<String>builder()
					.withHostname("hostname")
					.withDescription("description")
					.withDefaultValue("default")
					.withMaxRetries(1)
					.build()
					.run(() -> {
						if (attempts.getAndIncrement() == 0) {
							// A library that swallows every interrupt, the cancel of the shelter included, then fails
							while (givenUp.getCount() > 0) {
								try {
									givenUp.await();
								} catch (InterruptedException e) {
									// Swallowed
								}
							}
							Thread.interrupted();
							throw new RetryableException();
						}
						return "sent again";
					})
			);
			done.countDown();
		}, 1);
		Thread.interrupted();
		givenUp.countDown();

		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals("default", result.get());
		assertEquals(1, attempts.get());
	}
}
