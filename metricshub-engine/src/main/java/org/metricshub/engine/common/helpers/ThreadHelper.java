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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
 * Per-(hostname, operationType) counters track completed and timed-out requests
 * for observability. Strategies read these counters and record them as metrics
 * on the host monitor when self-monitoring is enabled.
 * </p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ThreadHelper {

	/**
	 * Key for per-host, per-operation statistics.
	 */
	record StatsKey(String hostname, String operationType) {}

	/**
	 * Mutable counters for a single (hostname, operationType) pair.
	 */
	static class AtomicStats {

		final AtomicLong completed = new AtomicLong(0);
		final AtomicLong timeout = new AtomicLong(0);

		Stats snapshot() {
			return Stats.builder().completed(completed.get()).timeout(timeout.get()).build();
		}
	}

	/**
	 * Per-(hostname, operationType) statistics map.
	 */
	private static final ConcurrentHashMap<StatsKey, AtomicStats> STATS_MAP = new ConcurrentHashMap<>();

	/**
	 * Immutable execution statistics snapshot.
	 */
	@Getter
	@Builder
	@AllArgsConstructor
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
	 * single-thread executor. This overload does <strong>not</strong> record any
	 * statistics.
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
		return doExecute(callable, timeout, null);
	}

	/**
	 * Executes a {@link Callable} task with a specified timeout using a dedicated
	 * single-thread executor and records per-(hostname, operationType) statistics.
	 *
	 * @param <T>           the type of the result returned by the {@code callable}
	 * @param callable      the task to be executed
	 * @param timeout       the maximum time to wait for the task to complete, in seconds
	 * @param hostname      the hostname associated with this request
	 * @param operationType the operation type (e.g. "snmp", "jmx", "wbem", "json2csv")
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted while waiting
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out
	 */
	public static <T> T execute(
		final Callable<T> callable,
		final long timeout,
		final String hostname,
		final String operationType
	) throws InterruptedException, ExecutionException, TimeoutException {
		return doExecute(callable, timeout, new StatsKey(hostname, operationType));
	}

	/**
	 * Internal execution method shared by both overloads.
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @param statsKey if non-null, the key under which to record statistics
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted while waiting
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out
	 */
	private static <T> T doExecute(final Callable<T> callable, final long timeout, final StatsKey statsKey)
		throws InterruptedException, ExecutionException, TimeoutException {
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<T> future = executor.submit(callable);
			try {
				final T result = future.get(timeout, TimeUnit.SECONDS);
				if (statsKey != null) {
					getOrCreateStats(statsKey).completed.incrementAndGet();
				}
				return result;
			} catch (TimeoutException e) {
				future.cancel(true);
				if (statsKey != null) {
					getOrCreateStats(statsKey).timeout.incrementAndGet();
				}
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
	 * Returns or creates the {@link AtomicStats} for the given key.
	 *
	 * @param key the (hostname, operationType) key
	 * @return the mutable counter holder
	 */
	private static AtomicStats getOrCreateStats(final StatsKey key) {
		return STATS_MAP.computeIfAbsent(key, k -> new AtomicStats());
	}

	/**
	 * Returns a snapshot of the execution statistics for a given hostname,
	 * grouped by operation type.
	 *
	 * @param hostname the hostname to look up
	 * @return an unmodifiable map of operationType to {@link Stats} snapshot;
	 *         empty if no statistics exist for the hostname
	 */
	public static Map<String, Stats> getStats(final String hostname) {
		return Collections.unmodifiableMap(
			STATS_MAP
				.entrySet()
				.stream()
				.filter(entry -> entry.getKey().hostname().equals(hostname))
				.collect(Collectors.toMap(entry -> entry.getKey().operationType(), entry -> entry.getValue().snapshot()))
		);
	}
}
