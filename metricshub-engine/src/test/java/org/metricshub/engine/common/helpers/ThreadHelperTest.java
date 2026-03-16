package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
		final String hostname = "host-completed-test";
		final Map<String, ThreadHelper.Stats> before = ThreadHelper.getStats(hostname);
		final long completedBefore = before.containsKey("snmp") ? before.get("snmp").getCompleted() : 0;

		ThreadHelper.execute(() -> "done", 5, hostname, "snmp");

		final Map<String, ThreadHelper.Stats> after = ThreadHelper.getStats(hostname);
		assertNotNull(after.get("snmp"));
		assertTrue(
			after.get("snmp").getCompleted() > completedBefore,
			"Completed count should increment after successful execution"
		);
	}

	@Test
	void testStatsTimeoutIncrement() {
		final String hostname = "host-timeout-test";
		final Map<String, ThreadHelper.Stats> before = ThreadHelper.getStats(hostname);
		final long timeoutBefore = before.containsKey("jmx") ? before.get("jmx").getTimeout() : 0;

		assertThrows(
			TimeoutException.class,
			() ->
				ThreadHelper.execute(
					() -> {
						Thread.sleep(10_000);
						return "never";
					},
					1,
					hostname,
					"jmx"
				)
		);

		final Map<String, ThreadHelper.Stats> after = ThreadHelper.getStats(hostname);
		assertNotNull(after.get("jmx"));
		assertTrue(after.get("jmx").getTimeout() > timeoutBefore, "Timeout count should increment after a timeout");
	}

	@Test
	void testSimpleOverloadDoesNotRecordStats() throws Exception {
		final String hostname = "host-simple-no-stats";
		ThreadHelper.execute(() -> "no-tracking", 5);
		final Map<String, ThreadHelper.Stats> stats = ThreadHelper.getStats(hostname);
		assertTrue(stats.isEmpty(), "Simple execute overload should not record any stats");
	}

	@Test
	void testGetStatsPerOperation() throws Exception {
		final String hostname = "host-per-op-test";
		ThreadHelper.execute(() -> "a", 5, hostname, "snmp");
		ThreadHelper.execute(() -> "b", 5, hostname, "wbem");
		ThreadHelper.execute(() -> "c", 5, hostname, "snmp");

		final Map<String, ThreadHelper.Stats> stats = ThreadHelper.getStats(hostname);
		assertEquals(2, stats.size(), "Should have stats for 2 operation types");
		assertEquals(2, stats.get("snmp").getCompleted());
		assertEquals(1, stats.get("wbem").getCompleted());
		assertEquals(0, stats.get("snmp").getTimeout());
	}
}
