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
	 * or interruption, the task is cancelled (its thread is interrupted) and, if it started, given up on (see
	 * {@link #trackingAbandonedRequests(Collection, Supplier)}).
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @return the result of the executed task
	 * @throws InterruptedException if the current thread was interrupted before the task started (a
	 *                              {@link NotStartedException}) or while waiting; its interrupt status is kept
	 * @throws ExecutionException   if the computation threw an exception
	 * @throws TimeoutException     if the wait timed out, caused by a {@link NotStartedException} when the task never
	 *                              started
	 */
	public static <T> T execute(final Callable<T> callable, final long timeout) throws InterruptedException,
		ExecutionException, TimeoutException {
		return execute(callable, timeout, true);
	}

	/**
	 * {@link #execute(Callable, long)} for a local computation, which never talks to a device: given up on, it is not
	 * tracked by {@link #trackingAbandonedRequests(Collection, Supplier)}, so it never holds back the next request.
	 *
	 * @param <T>         the type of the result of the computation
	 * @param computation the computation
	 * @param timeout     the maximum time to wait for the computation to complete, in seconds
	 * @return the result of the computation
	 * @throws InterruptedException see {@link #execute(Callable, long)}
	 * @throws ExecutionException   see {@link #execute(Callable, long)}
	 * @throws TimeoutException     see {@link #execute(Callable, long)}
	 */
	public static <T> T executeLocally(final Callable<T> computation, final long timeout) throws InterruptedException,
		ExecutionException, TimeoutException {
		return execute(computation, timeout, false);
	}

	/**
	 * See {@link #execute(Callable, long)}.
	 *
	 * @param <T>      the type of the result returned by the {@code callable}
	 * @param callable the task to be executed
	 * @param timeout  the maximum time to wait for the task to complete, in seconds
	 * @param tracked  whether a task given up on is tracked, see {@link #trackingAbandonedRequests(Collection, Supplier)}
	 * @return the result of the executed task
	 * @throws InterruptedException see {@link #execute(Callable, long)}
	 * @throws ExecutionException   see {@link #execute(Callable, long)}
	 * @throws TimeoutException     see {@link #execute(Callable, long)}
	 */
	private static <T> T execute(final Callable<T> callable, final long timeout, final boolean tracked)
		throws InterruptedException, ExecutionException, TimeoutException {
		// A caller already interrupted (an expired deadline of its job) must not start a request it will not wait for
		if (isCancelled()) {
			throw new NotStartedException("Interrupted before the task started");
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final StartGate gate = new StartGate();
			final Future<T> future = executor.submit(gate.guard(callable));
			try {
				return future.get(timeout, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				future.cancel(true);
				log.debug("Task timed out after {} seconds and was cancelled.", timeout);
				if (gate.abandon()) {
					// The worker never got to the task (a timeout of 0, a loaded host): nothing was sent
					e.initCause(new NotStartedException("Timed out before the task started"));
				} else if (tracked) {
					// A task that ignores the interrupt may still be running on the device
					abandon(executor);
				}
				throw e;
			} catch (InterruptedException e) {
				throw cancel(future, gate, tracked ? executor : null, e);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Cancels the task of an interrupted caller: an {@link InterruptedException} as is when the task started, which is
	 * given up on (see {@link #trackingAbandonedRequests(Collection, Supplier)}), a {@link NotStartedException} when it
	 * never will. The interrupt status of the caller is restored.
	 *
	 * @param future    the task
	 * @param gate      the gate of the task
	 * @param executor  the executor running the task, {@code null} when a task given up on is not tracked
	 * @param exception the interrupt of the caller
	 * @return the exception to throw
	 */
	private static InterruptedException cancel(
		final Future<?> future,
		final StartGate gate,
		final ExecutorService executor,
		final InterruptedException exception
	) {
		future.cancel(true);
		Thread.currentThread().interrupt();
		if (gate.abandon()) {
			return new NotStartedException("Interrupted before the task started");
		}
		if (executor != null) {
			abandon(executor);
		}
		return exception;
	}

	/**
	 * Settles, once, the race between a request that starts on another thread and its caller that gives up on it: either
	 * {@link #start()} or {@link #abandon()} wins. A request whose caller won sends nothing, and its caller counts
	 * nothing.
	 */
	public static final class StartGate {

		private static final int PENDING = 0;
		private static final int STARTED = 1;
		private static final int ABANDONED = 2;

		private final AtomicInteger state = new AtomicInteger(PENDING);

		/**
		 * Called by the request right before it sends anything.
		 *
		 * @return {@code true} if the request may go on, {@code false} if its caller already gave up on it
		 */
		public boolean start() {
			return state.compareAndSet(PENDING, STARTED);
		}

		/**
		 * Called by the caller that gives up on the request.
		 *
		 * @return {@code true} if the request never started and never will, {@code false} if it started
		 */
		public boolean abandon() {
			return state.compareAndSet(PENDING, ABANDONED) || state.get() == ABANDONED;
		}

		/**
		 * The given task, run only if its caller did not give up on it before it started.
		 *
		 * @param <T>  the type of the result of the task
		 * @param task the task
		 * @return the guarded task, which returns {@code null} without running the task once given up on
		 */
		<T> Callable<T> guard(final Callable<T> task) {
			return () -> start() ? task.call() : null;
		}
	}

	/**
	 * The {@link InterruptedException} of {@link #execute(Callable, long)} and {@link #executeInterruptibly(Callable)}
	 * when their caller is cancelled before the request started, or the cause of the {@link TimeoutException} of
	 * {@link #execute(Callable, long)} when its task never started: nothing was sent and nothing is counted.
	 */
	public static final class NotStartedException extends InterruptedException {

		private static final long serialVersionUID = 1L;

		/**
		 * Creates the exception of a request that never started.
		 *
		 * @param message the message
		 */
		public NotStartedException(final String message) {
			super(message);
		}
	}

	/**
	 * Whether the given exception, or one of its causes, tells that a request never started: nothing was sent, so its
	 * outcome is not counted.
	 *
	 * @param throwable the exception
	 * @return {@code true} if the request never started
	 */
	public static boolean neverStarted(final Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			if (cause instanceof NotStartedException) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Where {@link #execute(Callable, long)}, {@link #executeInterruptibly(Callable)} and
	 * {@link #closeInterruptibly(Runnable)} put, on the current thread, the request they gave up on because their caller
	 * was interrupted or the request timed out. Null when nobody tracks them.
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
	 * @throws InterruptedException if the caller was interrupted before the request started (a
	 *                              {@link NotStartedException}) or while waiting; its interrupt status is kept
	 * @throws ExecutionException   if the request threw an exception, its cause
	 */
	public static <T> T executeInterruptibly(final Callable<T> request) throws InterruptedException, ExecutionException {
		// A caller already interrupted (an expired deadline of its job) must not start a request it will not wait for
		if (isCancelled()) {
			throw new NotStartedException("Interrupted before the request started");
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final StartGate gate = new StartGate();
			final Future<T> future = executor.submit(gate.guard(request));
			try {
				return future.get();
			} catch (InterruptedException e) {
				throw cancel(future, gate, executor, e);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Runs the given body with the given sink receiving, as an {@link ExecutorService} that terminates when the request
	 * ends, every request that {@link #execute(Callable, long)}, {@link #executeInterruptibly(Callable)} or
	 * {@link #closeInterruptibly(Runnable)} gives up on in the current thread. The previous sink is restored afterwards, so calls can be nested.
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
	 * What {@link #shelter(Runnable, long, boolean)} runs on the current thread once it sees the interrupt, which it keeps
	 * cleared until its task ends. Null when nothing must be told.
	 */
	private static final ThreadLocal<Runnable> ON_INTERRUPT = new ThreadLocal<>();

	/**
	 * Set on the thread of a sheltered task once {@link #shelter(Runnable, long, boolean)} gave up on it, see
	 * {@link #isCancelled()}.
	 */
	private static final ThreadLocal<AtomicBoolean> CANCELLED = new ThreadLocal<>();

	/**
	 * Set on the thread of a sheltered task, when asked, once {@link #shelter(Runnable, long, boolean)} sees the
	 * interrupt of its caller, see {@link #isStopping()}.
	 */
	private static final ThreadLocal<AtomicBoolean> STOPPING = new ThreadLocal<>();

	/**
	 * A close for a try-with-resources statement, which throws no checked exception, see
	 * {@link #closeInterruptibly(Runnable)}.
	 */
	@FunctionalInterface
	public interface InterruptibleClose extends AutoCloseable {
		@Override
		void close();
	}

	/**
	 * Runs the given body with the given hook run by {@link #shelter(Runnable, long, boolean)} as soon as it sees the
	 * interrupt of the current thread, since it clears that interrupt while its task runs: whoever watches the interrupt
	 * status of this thread would miss it. The previous hook is restored afterwards.
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
	 * Whether the current thread is interrupted, or runs a sheltered task that {@link #shelter(Runnable, long, boolean)}
	 * gave up on: unlike the interrupt status, that never clears, even when a library swallows the interrupt.
	 *
	 * @return {@code true} if the work of the current thread is cancelled
	 */
	public static boolean isCancelled() {
		final AtomicBoolean cancelled = CANCELLED.get();
		return Thread.currentThread().isInterrupted() || cancelled != null && cancelled.get();
	}

	/**
	 * Whether the current thread must start no new step of its work: it is cancelled (see {@link #isCancelled()}), or it
	 * runs a sheltered task whose caller was interrupted and which may only end the step it is in, see
	 * {@link #shelter(Runnable, long, boolean)}.
	 *
	 * @return {@code true} if the current thread must start no new step
	 */
	public static boolean isStopping() {
		final AtomicBoolean stopping = STOPPING.get();
		return isCancelled() || stopping != null && stopping.get();
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
	 * Waits at most the given time for the given pool to terminate, even on an interrupted thread: the interrupt status is
	 * cleared while it waits, so the {@link #onInterrupt(Runnable, Runnable)} hook is run first, then restored.
	 *
	 * @param pool         the pool, shut down
	 * @param timeoutNanos the longest wait, in nanoseconds
	 * @return {@code true} if the pool terminated
	 */
	public static boolean awaitTermination(final ExecutorService pool, final long timeoutNanos) {
		final long end = System.nanoTime() + timeoutNanos;
		boolean interrupted = Thread.interrupted();
		if (interrupted) {
			notifyInterrupt();
		}
		try {
			while (true) {
				try {
					return pool.awaitTermination(end - System.nanoTime(), TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					// awaitTermination cleared the interrupt status, so the next wait does not throw at once
					if (!interrupted) {
						interrupted = true;
						notifyInterrupt();
					}
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
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
	 * @param task             the task to run
	 * @param graceSeconds     how long the task may still run after the caller was interrupted, in seconds
	 * @param stopBetweenSteps whether the task, once its caller is interrupted, only ends the step it is in, see
	 *                         {@link #isStopping()}: an opening (a login) ends, while a cleanup (a logout) runs all its
	 *                         steps
	 */
	public static void shelter(final Runnable task, final long graceSeconds, final boolean stopBetweenSteps) {
		// Clears and remembers the interrupt: a caller interrupted at entry already uses its grace
		boolean interrupted = Thread.interrupted();
		final AtomicBoolean stopping = new AtomicBoolean(interrupted && stopBetweenSteps);
		if (interrupted) {
			notifyInterrupt();
		}
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(graceSeconds);
		final AtomicBoolean cancelled = new AtomicBoolean();
		final ExecutorService executor = Executors.newSingleThreadExecutor(new MetricsHubThreadFactory());
		try {
			final Future<?> future = executor.submit(() -> {
				CANCELLED.set(cancelled);
				STOPPING.set(stopping);
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
						stopping.set(stopBetweenSteps);
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
