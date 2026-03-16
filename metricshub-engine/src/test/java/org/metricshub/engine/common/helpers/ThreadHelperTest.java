package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertThrows(
			TimeoutException.class,
			() ->
				ThreadHelper.execute(
					() -> {
						Thread.sleep(10_000);
						return "never";
					},
					1
				)
		);
	}

	@Test
	void testWorkerThreadNaming() throws Exception {
		final String threadName = ThreadHelper.execute(() -> Thread.currentThread().getName(), 5);
		assertTrue(
			threadName.startsWith("metricshub-worker-"),
			"Worker threads should be named with metricshub-worker- prefix, but was: " + threadName
		);
	}

	@Test
	void testStatsCompletedIncrement() throws Exception {
		final long before = ThreadHelper.getStats().getCompleted();
		ThreadHelper.execute(() -> "done", 5);
		final long after = ThreadHelper.getStats().getCompleted();
		assertTrue(after > before, "Completed count should increment after successful execution");
	}

	@Test
	void testStatsTimeoutIncrement() {
		final long before = ThreadHelper.getStats().getTimeout();
		assertThrows(
			TimeoutException.class,
			() ->
				ThreadHelper.execute(
					() -> {
						Thread.sleep(10_000);
						return "never";
					},
					1
				)
		);
		final long after = ThreadHelper.getStats().getTimeout();
		assertTrue(after > before, "Timeout count should increment after a timeout");
	}
}
