package org.metricshub.extension.ping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.telemetry.RequestOutcome.COMPLETED;
import static org.metricshub.engine.telemetry.RequestOutcome.FAILED;
import static org.metricshub.engine.telemetry.RequestOutcome.TIMEOUT;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.ThreadHelper;
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
	void testPingOnAnInterruptedThreadCountsNothing() throws Exception {
		executor.reachable = true;
		// An earlier deadline of the job interrupted the thread: nothing is resolved nor pinged
		Thread.currentThread().interrupt();
		try {
			assertFalse(executor.ping(HOSTNAME, TIMEOUT_MS, stats));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertNull(executor.resolvedHostname);
		assertEquals(Map.of(), stats.snapshot());
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

	/**
	 * Blocks until released, ignoring interrupts like a blocking library call.
	 *
	 * @param release The latch that ends the wait.
	 */
	private static void blockIgnoringInterrupts(final CountDownLatch release) {
		while (release.getCount() > 0) {
			try {
				release.await();
			} catch (InterruptedException e) {
				// Ignored, like isReachable and the name resolution do
			}
		}
	}

	/**
	 * Pings with the given executor, whose probe blocks until released, and interrupts the caller after 200 ms. The
	 * probe is released after 3 s at the latest, so that a caller that waits for it still ends.
	 *
	 * @param blocked The executor whose probe blocks.
	 * @param release Releases the probe.
	 */
	private void assertInterruptedPingReturnsAtOnce(final PingRequestExecutor blocked, final CountDownLatch release)
		throws Exception {
		final Thread caller = Thread.currentThread();
		final Thread interrupter = Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(200);
				caller.interrupt();
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				release.countDown();
			}
		});
		final List<ExecutorService> abandoned = new ArrayList<>();
		final long start = System.nanoTime();
		final boolean result;
		final long elapsedMillis;
		final boolean interrupted;
		try {
			result = ThreadHelper.trackingAbandonedRequests(abandoned, () -> {
				try {
					return blocked.ping(HOSTNAME, TIMEOUT_MS, stats);
				} catch (UnknownHostException e) {
					throw new IllegalStateException(e);
				}
			});
		} finally {
			elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			interrupted = Thread.interrupted();
			release.countDown();
			interrupter.interrupt();
			interrupter.join();
		}

		assertFalse(result);
		assertTrue(interrupted, "The interrupt status is kept");
		assertTrue(elapsedMillis < 1200, "Returned after " + elapsedMillis + " ms");
		assertEquals(counts(0, 1, 0), stats.snapshot());

		// The probe left behind ends and records nothing
		assertEquals(1, abandoned.size());
		assertTrue(abandoned.get(0).awaitTermination(5, TimeUnit.SECONDS));
		assertEquals(counts(0, 1, 0), stats.snapshot());
	}

	@Test
	void testInterruptedReachabilityTestIsOneTimeout() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		assertInterruptedPingReturnsAtOnce(new FakePingRequestExecutor() {
			@Override
			boolean isReachable(final InetAddress address, final int timeout) {
				blockIgnoringInterrupts(release);
				return true;
			}
		}, release);
	}

	@Test
	void testInterruptedResolutionIsOneTimeout() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		assertInterruptedPingReturnsAtOnce(new FakePingRequestExecutor() {
			@Override
			InetAddress resolve(final String hostname) {
				blockIgnoringInterrupts(release);
				return InetAddress.getLoopbackAddress();
			}
		}, release);
	}

	@Test
	void testProbeThatNeverStartedAfterTheResolutionIsOneTimeout() throws Exception {
		// The interrupt lands after the name resolution, before the probe starts
		final FakePingRequestExecutor interruptedAfterTheResolution = new FakePingRequestExecutor() {
			@Override
			long nanoTime() {
				Thread.currentThread().interrupt();
				return super.nanoTime();
			}
		};
		interruptedAfterTheResolution.reachable = true;
		final boolean result;
		try {
			result = interruptedAfterTheResolution.ping(HOSTNAME, TIMEOUT_MS, stats);
		} finally {
			Thread.interrupted();
		}
		assertFalse(result);
		assertEquals(counts(0, 1, 0), stats.snapshot());
	}

	@Test
	void testInterruptedCallerSendsNothing() throws Exception {
		executor.reachable = true;
		Thread.currentThread().interrupt();
		final boolean result;
		try {
			result = executor.ping(HOSTNAME, TIMEOUT_MS, stats);
		} finally {
			assertTrue(Thread.interrupted(), "The interrupt status is kept");
		}
		assertFalse(result);
		assertNull(executor.resolvedHostname, "Nothing is resolved");
		assertEquals(Map.of(), stats.snapshot());
	}
}
