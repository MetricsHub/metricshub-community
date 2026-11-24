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

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
		if (isCancelled()) {
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
	 * Where {@link #executeInterruptibly(Callable)} and {@link #closeInterruptibly(Runnable)} put, on the current
	 * thread, the request they gave up on because their caller was interrupted. Null when nobody tracks them.
	 */
	private static final ThreadLocal<Collection<ExecutorService>> ABANDONED = new ThreadLocal<>();

	/**
	 * Runs a blocking request on a dedicated daemon thread and waits for it without any timeout: the request ends when
	 * the call returns or throws, with its own protocol timeouts, or when the caller is interrupted. In that last case
	 * the request may still be running on its thread, but its result is dropped and nothing more is sent by the caller.
	 * Used for the library calls that ignore {@link Thread#interrupt()}.
	 *
	 * @param <T>     the type of the result of the request
	 * @param request the request, which must not write any engine state
	 * @return the result of the request
	 * @throws InterruptedException if the caller was interrupted before the request started (the request is never
	 *                              submitted) or while waiting; its interrupt status is kept
	 * @throws ExecutionException   if the request threw an exception, its cause
	 */
	public static <T> T executeInterruptibly(final Callable<T> request) throws InterruptedException, ExecutionException {
		// A caller already interrupted (an expired deadline of its job) must not start a request it will not wait for
		if (isCancelled()) {
			throw new InterruptedException("Interrupted before the request started");
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<T> future = executor.submit(request);
			try {
				return future.get();
			} catch (InterruptedException e) {
				future.cancel(true);
				abandon(executor);
				Thread.currentThread().interrupt();
				throw e;
			}
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Runs the given body with the given sink receiving, as an {@link ExecutorService} that terminates when the request
	 * ends, every request that {@link #executeInterruptibly(Callable)} or {@link #closeInterruptibly(Runnable)} gives
	 * up on in the current thread. The previous sink is restored afterwards, so calls can be nested.
	 *
	 * @param <T>  the type of the result of the body
	 * @param sink the collection receiving the abandoned requests
	 * @param body the body to run
	 * @return the result of the body
	 */
	public static <T> T trackingAbandonedRequests(final Collection<ExecutorService> sink, final Supplier<T> body) {
		final Collection<ExecutorService> previous = ABANDONED.get();
		ABANDONED.set(sink);
		try {
			return body.get();
		} finally {
			if (previous == null) {
				ABANDONED.remove();
			} else {
				ABANDONED.set(previous);
			}
		}
	}

	/**
	 * Adds the given request to the sink of the current thread, if any.
	 *
	 * @param request the request given up on
	 */
	private static void abandon(final ExecutorService request) {
		final Collection<ExecutorService> sink = ABANDONED.get();
		if (sink != null) {
			sink.add(request);
		}
	}

	/**
	 * What {@link #shelter(Runnable, long)} runs on the current thread once it sees the interrupt, which it keeps
	 * cleared until its task ends. Null when nothing must be told.
	 */
	private static final ThreadLocal<Runnable> ON_INTERRUPT = new ThreadLocal<>();

	/**
	 * Set on the thread of a sheltered task once {@link #shelter(Runnable, long)} gave up on it, see
	 * {@link #isCancelled()}.
	 */
	private static final ThreadLocal<AtomicBoolean> CANCELLED = new ThreadLocal<>();

	/**
	 * Runs the given body with the given hook run by {@link #shelter(Runnable, long)} as soon as it sees the interrupt of
	 * the current thread, since it clears that interrupt while its task runs: whoever watches the interrupt status of
	 * this thread would miss it. The previous hook is restored afterwards.
	 *
	 * @param hook what to do when a shelter sees the interrupt, on the current thread
	 * @param body the body to run
	 */
	public static void onInterrupt(final Runnable hook, final Runnable body) {
		final Runnable previous = ON_INTERRUPT.get();
		ON_INTERRUPT.set(hook);
		try {
			body.run();
		} finally {
			if (previous == null) {
				ON_INTERRUPT.remove();
			} else {
				ON_INTERRUPT.set(previous);
			}
		}
	}

	/**
	 * Whether the current thread is interrupted, or runs a sheltered task that {@link #shelter(Runnable, long)} gave up
	 * on: unlike the interrupt status, that never clears, even when a library swallows the interrupt.
	 *
	 * @return {@code true} if the work of the current thread is cancelled
	 */
	public static boolean isCancelled() {
		final AtomicBoolean cancelled = CANCELLED.get();
		return Thread.currentThread().isInterrupted() || cancelled != null && cancelled.get();
	}

	/**
	 * The {@link #isCancelled()} of the current thread, for other threads to check: the threads of a pool it starts do
	 * not inherit its interrupt.
	 *
	 * @return whether the work of the current thread is cancelled, when asked
	 */
	public static BooleanSupplier cancellation() {
		final Thread thread = Thread.currentThread();
		final AtomicBoolean cancelled = CANCELLED.get();
		return () -> thread.isInterrupted() || cancelled != null && cancelled.get();
	}

	/**
	 * Runs the {@link #onInterrupt(Runnable, Runnable)} hook of the current thread, if any.
	 */
	private static void notifyInterrupt() {
		final Runnable hook = ON_INTERRUPT.get();
		if (hook != null) {
			hook.run();
		}
	}

	/**
	 * Runs the given task on a child thread that starts with a clear interrupt status, and waits for it. Once the caller
	 * is interrupted (before the call or while waiting), the task gets {@code graceSeconds} more to complete, then it is
	 * interrupted, cancelled for good (see {@link #isCancelled()}) and waited for at most one more second. The caller's
	 * interrupt status is restored before returning, and its {@link #onInterrupt(Runnable, Runnable)} hook is run as
	 * soon as the interrupt is seen.
	 *
	 * @param task         the task to run
	 * @param graceSeconds how long the task may still run after the caller was interrupted, in seconds
	 */
	public static void shelter(final Runnable task, final long graceSeconds) {
		// Clears and remembers the interrupt: a caller interrupted at entry already uses its grace
		boolean interrupted = Thread.interrupted();
		if (interrupted) {
			notifyInterrupt();
		}
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(graceSeconds);
		final AtomicBoolean cancelled = new AtomicBoolean();
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<?> future = executor.submit(() -> {
				CANCELLED.set(cancelled);
				task.run();
			});
			while (true) {
				try {
					if (interrupted) {
						future.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
					} else {
						future.get();
					}
					return;
				} catch (InterruptedException e) {
					// Future.get cleared the interrupt status, so the next wait does not throw at once
					if (!interrupted) {
						interrupted = true;
						deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(graceSeconds);
						notifyInterrupt();
					}
				} catch (TimeoutException e) {
					log.debug("Sheltered task still running {} seconds after the interrupt: cancelling it.", graceSeconds);
					cancelled.set(true);
					future.cancel(true);
					executor.shutdownNow();
					awaitQuietly(executor);
					return;
				} catch (ExecutionException e) {
					throw unchecked(e);
				}
			}
		} finally {
			executor.shutdownNow();
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Waits at most one second for the given cancelled executor to terminate.
	 *
	 * @param executor the executor
	 */
	private static void awaitQuietly(final ExecutorService executor) {
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// The caller is already interrupted: its status is restored by shelter
			log.debug("Interrupted while waiting for a cancelled sheltered task.");
		}
	}

	/**
	 * Runs a close that may send a request and ignore {@link Thread#interrupt()} on a virtual thread, and waits for it.
	 * An interrupted caller returns at once, with its interrupt status kept, and the close completes in the background.
	 *
	 * @param close the close
	 */
	public static void closeInterruptibly(final Runnable close) {
		final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		final Future<?> future = executor.submit(close);
		executor.shutdown();
		try {
			future.get();
		} catch (InterruptedException e) {
			abandon(executor);
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			throw unchecked(e);
		}
	}

	/**
	 * Gets the unchecked cause of the given exception thrown by a {@link Runnable}, or throws it if it is an error.
	 *
	 * @param e the exception
	 * @return the runtime exception to throw
	 */
	private static RuntimeException unchecked(final ExecutionException e) {
		final Throwable cause = e.getCause();
		if (cause instanceof Error error) {
			throw error;
		}
		return cause instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(cause);
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
