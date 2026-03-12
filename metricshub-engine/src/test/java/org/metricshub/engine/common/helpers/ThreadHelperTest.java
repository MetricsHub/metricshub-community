package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Tests for the refactored {@link ThreadHelper} with shared thread pool.
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
	void testSharedExecutorIsReused() throws Exception {
		final ExecutorService executor1 = ThreadHelper.getSharedExecutor();
		ThreadHelper.execute(() -> "task1", 5);
		final ExecutorService executor2 = ThreadHelper.getSharedExecutor();
		// Should be the exact same instance (shared pool, not per-request)
		assertTrue(executor1 == executor2, "Shared executor should be reused across calls");
	}

	@Test
	void testSharedExecutorIsNotNull() {
		assertNotNull(ThreadHelper.getSharedExecutor());
	}

	@Test
	void testSharedExecutorIsThreadPoolExecutor() {
		assertTrue(
			ThreadHelper.getSharedExecutor() instanceof ThreadPoolExecutor,
			"Shared executor should be a ThreadPoolExecutor"
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
}
