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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Global shared request execution controller.
 * <p>
 * Manages all blocking request executions across the JVM using a shared
 * {@link ThreadPoolExecutor} with a bounded {@link ArrayBlockingQueue} and
 * {@link ThreadPoolExecutor.AbortPolicy}. When both the pool and queue are
 * full, new submissions are rejected and the rejection is recorded.
 * </p>
 * <p>
 * The executor is configurable at runtime through {@link #configure(Config)},
 * which replaces the current executor atomically and shuts down the previous one.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ThreadHelper {

	/**
	 * Time in seconds to keep idle threads alive before termination.
	 */
	private static final long KEEP_ALIVE_SECONDS = 60L;

	/**
	 * Counter: number of requests that completed successfully.
	 */
	private static final AtomicLong COMPLETED_COUNT = new AtomicLong(0);

	/**
	 * Counter: number of requests that exceeded their timeout and were cancelled.
	 */
	private static final AtomicLong TIMEOUT_COUNT = new AtomicLong(0);

	/**
	 * Counter: number of requests rejected because the executor was saturated.
	 */
	private static final AtomicLong REJECTED_COUNT = new AtomicLong(0);

	/**
	 * Shared executor reference. Can be replaced at runtime via {@link #configure(Config)}.
	 */
	private static final AtomicReference<ThreadPoolExecutor> SHARED_EXECUTOR = new AtomicReference<>(
		buildExecutor(Config.builder().build())
	);

	/**
	 * Configuration for the shared request executor.
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class Config {

		/**
		 * Default pool size.
		 */
		public static final int DEFAULT_POOL_SIZE = 100;

		/**
		 * Default queue size.
		 */
		public static final int DEFAULT_QUEUE_SIZE = 50;

		@Builder.Default
		private int poolSize = DEFAULT_POOL_SIZE;

		@Builder.Default
		private int queueSize = DEFAULT_QUEUE_SIZE;
	}

	/**
	 * Live statistics of the shared request executor.
	 */
	@Getter
	@Builder
	@lombok.AllArgsConstructor
	public static class Stats {

		private final long completed;
		private final long timeout;
		private final long rejected;
		private final int active;
		private final int queueSize;
		private final int remainingCapacity;
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
	 * Builds a new {@link ThreadPoolExecutor} from the given configuration.
	 *
	 * @param config the executor configuration
	 * @return a new {@link ThreadPoolExecutor}
	 */
	private static ThreadPoolExecutor buildExecutor(final Config config) {
		return new ThreadPoolExecutor(
			config.getPoolSize(),
			config.getPoolSize(),
			KEEP_ALIVE_SECONDS,
			TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(config.getQueueSize()),
			new MetricsHubThreadFactory(),
			new ThreadPoolExecutor.AbortPolicy()
		);
	}

	/**
	 * Configures (or reconfigures) the shared executor. The previous executor is
	 * shut down gracefully. This method is safe to call at any time.
	 *
	 * @param config the new configuration to apply
	 */
	public static void configure(final Config config) {
		final ThreadPoolExecutor newExecutor = buildExecutor(config);
		final ThreadPoolExecutor oldExecutor = SHARED_EXECUTOR.getAndSet(newExecutor);
		if (oldExecutor != null) {
			oldExecutor.shutdown();
		}
		log.info("ThreadHelper reconfigured: poolSize={}, queueSize={}.", config.getPoolSize(), config.getQueueSize());
	}

	/**
	 * Executes a {@link Callable} task with a specified timeout using the shared
	 * thread pool. If the task completes within the given timeout, its result is
	 * returned. Otherwise, the future is cancelled and a {@link TimeoutException}
	 * is thrown.
	 * <p>
	 * If the executor is saturated (pool and queue full), the submission is rejected
	 * and the rejection counter is incremented. A {@link TimeoutException} is thrown
	 * in that case to signal the caller.
	 * </p>
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted while waiting
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out or the task was rejected
	 */
	public static <T> T execute(final Callable<T> callable, final long timeout)
		throws InterruptedException, ExecutionException, TimeoutException {
		final Future<T> handler;
		try {
			handler = SHARED_EXECUTOR.get().submit(callable);
		} catch (RejectedExecutionException e) {
			REJECTED_COUNT.incrementAndGet();
			log.warn("Request rejected: shared executor is saturated.");
			throw new TimeoutException("Request rejected: shared executor is saturated.");
		}
		try {
			final T result = handler.get(timeout, TimeUnit.SECONDS);
			COMPLETED_COUNT.incrementAndGet();
			return result;
		} catch (TimeoutException e) {
			handler.cancel(true);
			TIMEOUT_COUNT.incrementAndGet();
			log.warn("Task timed out after {} seconds and was cancelled.", timeout);
			throw e;
		} catch (InterruptedException e) {
			handler.cancel(true);
			Thread.currentThread().interrupt();
			throw e;
		}
	}

	/**
	 * Returns the shared executor service. Primarily intended for testing and
	 * monitoring purposes.
	 *
	 * @return the shared {@link ExecutorService}
	 */
	public static ExecutorService getSharedExecutor() {
		return SHARED_EXECUTOR.get();
	}

	/**
	 * Returns a snapshot of the current executor statistics.
	 *
	 * @return a {@link Stats} instance with live counters and executor metrics
	 */
	public static Stats getStats() {
		final ThreadPoolExecutor executor = SHARED_EXECUTOR.get();
		return Stats
			.builder()
			.completed(COMPLETED_COUNT.get())
			.timeout(TIMEOUT_COUNT.get())
			.rejected(REJECTED_COUNT.get())
			.active(executor.getActiveCount())
			.queueSize(executor.getQueue().size())
			.remainingCapacity(executor.getQueue().remainingCapacity())
			.build();
	}
}
