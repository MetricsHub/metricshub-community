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
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
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
 * Global counters track completed and timed-out requests for observability.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ThreadHelper {

	/**
	 * Counter: number of requests that completed successfully.
	 */
	private static final AtomicLong COMPLETED_COUNT = new AtomicLong(0);

	/**
	 * Counter: number of requests that exceeded their timeout and were cancelled.
	 */
	private static final AtomicLong TIMEOUT_COUNT = new AtomicLong(0);

	/**
	 * Execution statistics.
	 */
	@Getter
	@Builder
	@lombok.AllArgsConstructor
	public static class Stats {

		private final long completed;
		private final long timeout;
	}

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
	 * Executes a {@link Callable} task with a specified timeout using a dedicated
	 * single-thread executor. If the task completes within the given timeout, its
	 * result is returned. Otherwise, the future is cancelled and a
	 * {@link TimeoutException} is thrown.
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted while waiting
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out
	 */
	public static <T> T execute(final Callable<T> callable, final long timeout)
		throws InterruptedException, ExecutionException, TimeoutException {
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<T> future = executor.submit(callable);
			try {
				final T result = future.get(timeout, TimeUnit.SECONDS);
				COMPLETED_COUNT.incrementAndGet();
				return result;
			} catch (TimeoutException e) {
				future.cancel(true);
				TIMEOUT_COUNT.incrementAndGet();
				log.warn("Task timed out after {} seconds and was cancelled.", timeout);
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
	 * Returns a snapshot of the current execution statistics.
	 *
	 * @return a {@link Stats} instance with completed and timeout counters
	 */
	public static Stats getStats() {
		return Stats.builder().completed(COMPLETED_COUNT.get()).timeout(TIMEOUT_COUNT.get()).build();
	}
}
