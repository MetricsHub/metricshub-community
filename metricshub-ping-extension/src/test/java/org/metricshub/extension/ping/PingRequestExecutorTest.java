package org.metricshub.extension.ping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.telemetry.RequestOutcome.COMPLETED;
import static org.metricshub.engine.telemetry.RequestOutcome.FAILED;
import static org.metricshub.engine.telemetry.RequestOutcome.TIMEOUT;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;

class PingRequestExecutorTest {

	private static final String HOSTNAME = "host";
	private static final int TIMEOUT_MS = 5000;

	private final FakePingRequestExecutor executor = new FakePingRequestExecutor();
	private final RequestStats stats = new RequestStats();

	/**
	 * Builds the expected snapshot of the ping counters.
	 */
	static Map<String, Map<RequestOutcome, Long>> counts(final long completed, final long timeout, final long failed) {
		return Map.of(PingRequestExecutor.OPERATION_TYPE, Map.of(COMPLETED, completed, TIMEOUT, timeout, FAILED, failed));
	}

	@Test
	void testReachableIsCompleted() throws Exception {
		executor.reachable = true;
		executor.elapsedMillis = 3;
		assertTrue(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		assertEquals(counts(1, 0, 0), stats.snapshot());
	}

	@Test
	void testUnresolvableIsFailed() {
		final UnknownHostException error = new UnknownHostException(HOSTNAME);
		executor.resolveError = error;
		assertSame(error, assertThrows(UnknownHostException.class, () -> executor.ping(HOSTNAME, TIMEOUT_MS, stats)));
		assertEquals(counts(0, 0, 1), stats.snapshot());
	}

	@Test
	void testIoExceptionIsFailed() throws Exception {
		executor.pingError = new IOException("network error");
		assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		assertEquals(counts(0, 0, 1), stats.snapshot());
	}

	@Test
	void testIoTimeoutExceptionIsTimeout() throws Exception {
		executor.pingError = new SocketTimeoutException("timed out");
		assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		executor.pingError = new IOException("network error", new SocketTimeoutException("timed out"));
		assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		assertEquals(counts(0, 2, 0), stats.snapshot());
	}

	@Test
	void testImmediateFalseIsFailed() throws Exception {
		executor.elapsedMillis = 10;
		assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		assertEquals(counts(0, 0, 1), stats.snapshot());
	}

	@Test
	void testFalseAfterTimeoutIsTimeout() throws Exception {
		executor.elapsedMillis = TIMEOUT_MS;
		assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
		assertEquals(counts(0, 1, 0), stats.snapshot());
	}

	@Test
	void testEachAttemptIsCounted() throws Exception {
		executor.reachable = true;
		executor.ping(HOSTNAME, TIMEOUT_MS, stats);
		executor.ping(HOSTNAME, TIMEOUT_MS, stats);
		executor.reachable = false;
		executor.elapsedMillis = TIMEOUT_MS + 20;
		executor.ping(HOSTNAME, TIMEOUT_MS, stats);
		assertEquals(counts(2, 1, 0), stats.snapshot());
	}

	@Test
	void testClassifyNoAnswerTolerance() {
		final long limit = TIMEOUT_MS - PingRequestExecutor.TIMEOUT_TOLERANCE_MS;
		assertEquals(TIMEOUT, PingRequestExecutor.classifyNoAnswer(TimeUnit.MILLISECONDS.toNanos(limit), TIMEOUT_MS));
		assertEquals(FAILED, PingRequestExecutor.classifyNoAnswer(TimeUnit.MILLISECONDS.toNanos(limit - 1), TIMEOUT_MS));
		assertEquals(FAILED, PingRequestExecutor.classifyNoAnswer(0, TIMEOUT_MS));
	}

	@Test
	void testNullStatsRecordsNothing() {
		executor.reachable = true;
		assertDoesNotThrow(() -> executor.ping(HOSTNAME, TIMEOUT_MS, null));
		executor.reachable = false;
		executor.elapsedMillis = TIMEOUT_MS;
		assertDoesNotThrow(() -> executor.ping(HOSTNAME, TIMEOUT_MS, null));
		executor.pingError = new IOException("network error");
		assertDoesNotThrow(() -> executor.ping(HOSTNAME, TIMEOUT_MS, null));
		executor.resolveError = new UnknownHostException(HOSTNAME);
		assertThrows(UnknownHostException.class, () -> executor.ping(HOSTNAME, TIMEOUT_MS, null));
	}
}
