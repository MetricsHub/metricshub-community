package org.metricshub.opamp.client.retry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.time.Duration;
import java.util.Random;

/**
 * Computes retry delays for the OpAMP polling loop: exponential backoff with jitter on
 * consecutive failures, capped at a maximum delay, with support for server-provided minimum
 * delays ({@code Retry-After} header or {@code RetryInfo.retry_after_nanoseconds}) acting as
 * floors.
 * <p>
 * Not thread-safe: designed to be used from the single polling thread.
 * </p>
 */
public class RetrySchedule {

	private final Duration baseDelay;
	private final Duration maxDelay;
	private final Random random;
	private int failureCount;

	/**
	 * Creates a retry schedule.
	 *
	 * @param baseDelay the base delay, used as the first-failure backoff and growth base
	 * @param maxDelay  the maximum delay the backoff can reach
	 */
	public RetrySchedule(final Duration baseDelay, final Duration maxDelay) {
		this(baseDelay, maxDelay, new Random());
	}

	/**
	 * Creates a retry schedule with a caller-provided random source (used by tests).
	 *
	 * @param baseDelay the base delay, used as the first-failure backoff and growth base
	 * @param maxDelay  the maximum delay the backoff can reach
	 * @param random    the random source used for jitter
	 */
	public RetrySchedule(final Duration baseDelay, final Duration maxDelay, final Random random) {
		this.baseDelay = baseDelay;
		this.maxDelay = maxDelay;
		this.random = random;
	}

	/**
	 * Resets the backoff after a successful exchange.
	 */
	public void reset() {
		failureCount = 0;
	}

	/**
	 * Records a failure and returns the delay to wait before the next attempt: an exponentially
	 * growing delay with jitter in the [delay/2, delay] range, capped at the maximum delay, and
	 * never lower than the given floor.
	 *
	 * @param floor the server-suggested minimum delay, or {@code null} when none was provided
	 * @return the delay to wait before the next attempt
	 */
	public Duration nextDelayAfterFailure(final Duration floor) {
		failureCount++;
		final int exponent = Math.min(failureCount - 1, 16);
		long delayMs = baseDelay.toMillis() << exponent;
		if (delayMs <= 0 || delayMs > maxDelay.toMillis()) {
			delayMs = maxDelay.toMillis();
		}
		// Jitter in the [delayMs / 2, delayMs] range
		final long jitteredMs = delayMs / 2 + (long) (random.nextDouble() * (delayMs - delayMs / 2));
		Duration delay = Duration.ofMillis(jitteredMs);
		if (floor != null && floor.compareTo(delay) > 0) {
			delay = floor;
		}
		return delay;
	}

	/**
	 * Returns the number of consecutive failures recorded since the last reset.
	 *
	 * @return the consecutive failure count
	 */
	public int getFailureCount() {
		return failureCount;
	}
}
