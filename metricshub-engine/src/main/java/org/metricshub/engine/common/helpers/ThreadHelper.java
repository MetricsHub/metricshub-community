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
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides utility methods for thread management, including executing tasks
 * with a specified timeout. This class is part of the MetricsHub Engine's
 * common helper utilities.
 * <p>
 * Uses a shared thread pool to avoid creating a new executor per request,
 * which prevents thread accumulation under load. The pool is shared across
 * all hosts and all protocol requests. A {@link SynchronousQueue} ensures
 * that each submission either hands off to an idle thread or creates a new
 * one (up to {@link #MAX_POOL_SIZE}). When all threads are busy, the
 * {@link ThreadPoolExecutor.CallerRunsPolicy} runs the task on the calling
 * thread, providing natural back-pressure instead of rejection.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ThreadHelper {

	/**
	 * Maximum number of threads in the shared pool.
	 */
	private static final int MAX_POOL_SIZE = 50;

	/**
	 * Time in seconds to keep idle threads alive before termination.
	 */
	private static final long KEEP_ALIVE_SECONDS = 60L;

	/**
	 * Shared thread pool executor. Core pool is 0 so threads are only created
	 * on demand. {@link SynchronousQueue} forces a direct handoff: if no idle
	 * thread is available a new one is created, up to {@link #MAX_POOL_SIZE}.
	 * When the maximum is reached, {@link ThreadPoolExecutor.CallerRunsPolicy}
	 * executes the task on the caller's thread, throttling submission rather
	 * than rejecting it. Idle threads are terminated after
	 * {@link #KEEP_ALIVE_SECONDS}.
	 */
	private static final ExecutorService SHARED_EXECUTOR = new ThreadPoolExecutor(
		0,
		MAX_POOL_SIZE,
		KEEP_ALIVE_SECONDS,
		TimeUnit.SECONDS,
		new SynchronousQueue<>(),
		new MetricsHubThreadFactory(),
		new ThreadPoolExecutor.CallerRunsPolicy()
	);

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
	 * Executes a {@link Callable} task with a specified timeout using the shared
	 * thread pool. If the task completes within the given timeout, its result is
	 * returned. Otherwise, the future is cancelled and a {@link TimeoutException}
	 * is thrown.
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
		final Future<T> handler = SHARED_EXECUTOR.submit(callable);
		try {
			return handler.get(timeout, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			// Cancel the task and interrupt the worker thread
			handler.cancel(true);
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
		return SHARED_EXECUTOR;
	}
}
