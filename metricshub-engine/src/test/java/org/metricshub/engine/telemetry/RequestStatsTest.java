package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RequestStatsTest {

	@Test
	void testEmptySnapshot() {
		assertTrue(new RequestStats().snapshot().isEmpty());
	}

	@Test
	void testSnapshotHasEveryOutcomeOfARecordedProtocol() {
		final RequestStats stats = new RequestStats();
		stats.record("snmp", RequestOutcome.COMPLETED);
		stats.record("snmp", RequestOutcome.COMPLETED);
		stats.record("http", RequestOutcome.FAILED);

		final Map<String, Map<RequestOutcome, Long>> snapshot = stats.snapshot();
		assertEquals(List.of("http", "snmp"), List.copyOf(snapshot.keySet()));
		assertEquals(
			Map.of(RequestOutcome.COMPLETED, 2L, RequestOutcome.TIMEOUT, 0L, RequestOutcome.FAILED, 0L),
			snapshot.get("snmp")
		);
		assertEquals(
			Map.of(RequestOutcome.COMPLETED, 0L, RequestOutcome.TIMEOUT, 0L, RequestOutcome.FAILED, 1L),
			snapshot.get("http")
		);
	}

	@Test
	void testSnapshotIsACopy() {
		final RequestStats stats = new RequestStats();
		stats.record("wbem", RequestOutcome.TIMEOUT);
		final Map<String, Map<RequestOutcome, Long>> snapshot = stats.snapshot();
		stats.record("wbem", RequestOutcome.TIMEOUT);
		assertEquals(1L, snapshot.get("wbem").get(RequestOutcome.TIMEOUT));
		assertEquals(2L, stats.snapshot().get("wbem").get(RequestOutcome.TIMEOUT));
	}

	@Test
	void testNullArgumentsAreRejected() {
		final RequestStats stats = new RequestStats();
		assertThrows(IllegalArgumentException.class, () -> stats.record(null, RequestOutcome.COMPLETED));
		assertThrows(IllegalArgumentException.class, () -> stats.record("snmp", null));
	}

	@Test
	void testConcurrentRecordsAreAllCounted() throws Exception {
		final RequestStats stats = new RequestStats();
		final int threads = 8;
		final int perThread = 10_000;
		final ExecutorService executor = Executors.newFixedThreadPool(threads);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			for (int t = 0; t < threads; t++) {
				final RequestOutcome outcome = RequestOutcome.values()[t % RequestOutcome.values().length];
				final String protocol = t % 2 == 0 ? "ssh" : "winrm";
				executor.submit(() -> {
					start.await();
					for (int i = 0; i < perThread; i++) {
						stats.record(protocol, outcome);
					}
					return null;
				});
			}
			start.countDown();
		} finally {
			executor.shutdown();
			assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
		}

		final Map<String, Map<RequestOutcome, Long>> snapshot = stats.snapshot();
		final long total = snapshot.values().stream().flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum();
		assertEquals((long) threads * perThread, total);
		// Threads 0, 2, 4, 6 record ssh with COMPLETED, TIMEOUT, FAILED, COMPLETED
		assertEquals(2L * perThread, snapshot.get("ssh").get(RequestOutcome.COMPLETED));
		assertEquals((long) perThread, snapshot.get("ssh").get(RequestOutcome.TIMEOUT));
		assertEquals((long) perThread, snapshot.get("ssh").get(RequestOutcome.FAILED));
	}
}
