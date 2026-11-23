package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Request execution helper for timeout management.
 * <p>
 * Wraps blocking request calls in a per-request single-thread executor so that
 * {@link Future#get(long, TimeUnit)} can enforce a timeout and cancel the task
 * if it exceeds the allowed duration.
 * </p>
 * <p>
 * Concurrency is bounded by upper layers (job pool size, strategy executors,
 * connector thread pools), so no shared pool or admission control is needed here.
 * </p>
 * <p>
 * This helper does not count requests: the caller records the outcome of the request in the
 * {@link org.metricshub.engine.telemetry.RequestStats} of its resource, after this method returned or threw,
 * so that a task that completes after its timeout cannot record a second outcome.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ThreadHelper {

	/**
	 * Custom thread factory that names threads for easier debugging and diagnostics.
	 */
	private static class MetricsHubThreadFactory implements ThreadFactory {

		private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

		@Override
		public Thread newThread(final Runnable runnable) {
			final Thread thread = new Thread(runnable, "metricshub-worker-" + THREAD_COUNTER.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		}
	}

	/**
	 * Executes a {@link Callable} task with a specified timeout using a dedicated single-thread executor. On timeout
	 * or interruption, the task is cancelled (its thread is interrupted).
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted before the task started (the task is never
	 *                              submitted) or while waiting; its interrupt status is kept
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out
	 */
	public static <T> T execute(final Callable<T> callable, final long timeout) throws InterruptedException,
		ExecutionException, TimeoutException {
		// A caller already interrupted (an expired deadline of its job) must not start a request it will not wait for
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("Interrupted before the task started");
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<T> future = executor.submit(callable);
			try {
				return future.get(timeout, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
				log.debug("Task timed out after {} seconds and was cancelled.", timeout);
				throw e;
			} catch (InterruptedException e) {
				future.cancel(true);
				Thread.currentThread().interrupt();
				throw e;
			}
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Formerly cleared the request counters of a hostname. The counters now belong to the
	 * {@link org.metricshub.engine.telemetry.TelemetryManager} of each resource and start from zero with it, so
	 * there is nothing to clear.
	 *
	 * @param hostname ignored
	 * @deprecated The request counters are held by {@code TelemetryManager.getRequestStats()}.
	 */
	@Deprecated(forRemoval = true)
	public static void clearStats(final String hostname) {
		// Nothing to clear: the counters belong to each TelemetryManager
	}
}
