package org.metricshub.opamp.client.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RetryScheduleTest {

	private static final Duration BASE = Duration.ofSeconds(30);
	private static final Duration MAX = Duration.ofMinutes(10);

	/**
	 * A random source always returning 1.0 so the jittered delay equals the upper bound.
	 */
	private static Random maxRandom() {
		return new Random() {
			@Override
			public double nextDouble() {
				return 1.0;
			}
		};
	}

	@Test
	void delayShouldGrowExponentiallyUpToTheCap() {
		final RetrySchedule schedule = new RetrySchedule(BASE, MAX, maxRandom());

		assertEquals(Duration.ofSeconds(30), schedule.nextDelayAfterFailure(null));
		assertEquals(Duration.ofSeconds(60), schedule.nextDelayAfterFailure(null));
		assertEquals(Duration.ofSeconds(120), schedule.nextDelayAfterFailure(null));
		assertEquals(Duration.ofSeconds(240), schedule.nextDelayAfterFailure(null));
		assertEquals(Duration.ofSeconds(480), schedule.nextDelayAfterFailure(null));
		// Capped at the maximum delay
		assertEquals(MAX, schedule.nextDelayAfterFailure(null));
		assertEquals(MAX, schedule.nextDelayAfterFailure(null));
	}

	@Test
	void jitterShouldStayWithinBounds() {
		final RetrySchedule schedule = new RetrySchedule(BASE, MAX);
		final Duration delay = schedule.nextDelayAfterFailure(null);
		assertTrue(delay.compareTo(Duration.ofSeconds(15)) >= 0, "Delay must be at least half the base delay");
		assertTrue(delay.compareTo(BASE) <= 0, "Delay must not exceed the base delay on the first failure");
	}

	@Test
	void floorShouldOverrideShorterDelays() {
		final RetrySchedule schedule = new RetrySchedule(BASE, MAX, maxRandom());
		final Duration floor = Duration.ofMinutes(5);
		assertEquals(floor, schedule.nextDelayAfterFailure(floor));
		// A floor smaller than the computed delay has no effect
		assertEquals(Duration.ofSeconds(60), schedule.nextDelayAfterFailure(Duration.ofSeconds(1)));
	}

	@Test
	void resetShouldRestartTheBackoff() {
		final RetrySchedule schedule = new RetrySchedule(BASE, MAX, maxRandom());
		schedule.nextDelayAfterFailure(null);
		schedule.nextDelayAfterFailure(null);
		assertEquals(2, schedule.getFailureCount());

		schedule.reset();

		assertEquals(0, schedule.getFailureCount());
		assertEquals(Duration.ofSeconds(30), schedule.nextDelayAfterFailure(null));
	}

	@Test
	void delayShouldNotOverflowOnManyFailures() {
		final RetrySchedule schedule = new RetrySchedule(BASE, MAX, maxRandom());
		for (int i = 0; i < 100; i++) {
			assertTrue(schedule.nextDelayAfterFailure(null).compareTo(MAX) <= 0);
		}
	}
}
