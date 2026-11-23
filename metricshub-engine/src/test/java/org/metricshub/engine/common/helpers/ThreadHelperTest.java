package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThreadHelper} with per-request executor and timeout management.
 */
class ThreadHelperTest {

	@Test
	void testExecuteReturnsResult() throws Exception {
		final String result = ThreadHelper.execute(() -> "hello", 5);
		assertEquals("hello", result);
	}

	@Test
	void testExecuteThrowsTimeoutException() {
		assertThrows(TimeoutException.class, () -> ThreadHelper.execute(() -> {
			Thread.sleep(10_000);
			return "never";
		}, 1));
	}

	@Test
	void testInterruptedCallerStartsNothing() throws Exception {
		final int workerBefore = workerNumber();
		final CountDownLatch started = new CountDownLatch(1);
		Thread.currentThread().interrupt();
		try {
			assertThrows(InterruptedException.class, () -> ThreadHelper.execute(() -> {
				started.countDown();
				return "never";
			}, 5));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertFalse(started.await(500, TimeUnit.MILLISECONDS));
		// No worker thread was created for the interrupted call
		assertEquals(workerBefore + 1, workerNumber());
	}

	/**
	 * Runs a task and returns the number of its worker thread, which each new worker increments.
	 *
	 * @return the number in the name of the worker thread
	 * @throws Exception never
	 */
	private static int workerNumber() throws Exception {
		final String name = ThreadHelper.execute(() -> Thread.currentThread().getName(), 5);
		return Integer.parseInt(name.substring("metricshub-worker-".length()));
	}

	@Test
	void testWorkerThreadNaming() throws Exception {
		final String threadName = ThreadHelper.execute(() -> Thread.currentThread().getName(), 5);
		assertTrue(
			threadName.startsWith("metricshub-worker-"),
			"Worker threads should be named with metricshub-worker- prefix, but was: " + threadName
		);
	}
}
