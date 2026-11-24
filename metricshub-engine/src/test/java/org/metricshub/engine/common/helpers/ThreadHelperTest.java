package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.metricshub.engine.common.helpers.ThreadHelper.StartGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThreadHelper} with per-request executor and timeout management.
 */
class ThreadHelperTest {

	@Test
	void testExecuteReturnsResult() throws Exception {
		final String result = ThreadHelper.execute(() -> "hello", 5);
		assertEquals("hello", result);
	}

	@Test
	void testExecuteThrowsTimeoutException() {
		assertThrows(TimeoutException.class, () -> ThreadHelper.execute(() -> {
			Thread.sleep(10_000);
			return "never";
		}, 1));
	}

	@Test
	void testInterruptedCallerStartsNothing() throws Exception {
		final int workerBefore = workerNumber();
		final CountDownLatch started = new CountDownLatch(1);
		Thread.currentThread().interrupt();
		try {
			assertThrows(InterruptedException.class, () -> ThreadHelper.execute(() -> {
				started.countDown();
				return "never";
			}, 5));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertFalse(started.await(500, TimeUnit.MILLISECONDS));
		// No worker thread was created for the interrupted call
		assertEquals(workerBefore + 1, workerNumber());
	}

	/**
	 * Runs a task and returns the number of its worker thread, which each new worker increments.
	 *
	 * @return the number in the name of the worker thread
	 * @throws Exception never
	 */
	private static int workerNumber() throws Exception {
		final String name = ThreadHelper.execute(() -> Thread.currentThread().getName(), 5);
		return Integer.parseInt(name.substring("metricshub-worker-".length()));
	}

	@Test
	void testWorkerThreadNaming() throws Exception {
		final String threadName = ThreadHelper.execute(() -> Thread.currentThread().getName(), 5);
		assertTrue(
			threadName.startsWith("metricshub-worker-"),
			"Worker threads should be named with metricshub-worker- prefix, but was: " + threadName
		);
	}

	@AfterEach
	void clearInterrupt() {
		Thread.interrupted();
	}

	/**
	 * Waits for the latch and swallows every interrupt, like a library that ignores {@link Thread#interrupt()}.
	 *
	 * @param latch the latch
	 */
	static void awaitSwallowingInterrupts(final CountDownLatch latch) {
		while (true) {
			try {
				latch.await();
				return;
			} catch (InterruptedException e) {
				// Swallowed
			}
		}
	}

	/**
	 * Interrupts the current thread after the given delay.
	 *
	 * @param millis the delay in milliseconds
	 */
	private static void interruptMeAfter(final long millis) {
		final Thread caller = Thread.currentThread();
		Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				return;
			}
			caller.interrupt();
		});
	}

	@Test
	void testExecuteInterruptiblyReturnsResultAndCause() throws Exception {
		assertEquals("hello", ThreadHelper.executeInterruptibly(() -> "hello"));
		final IOException failure = new IOException("refused");
		final ExecutionException e = assertThrows(ExecutionException.class, () -> ThreadHelper.executeInterruptibly(() -> {
			throw failure;
		}));
		assertEquals(failure, e.getCause());
	}

	@Test
	void testExecuteInterruptiblyInterruptedCallerStartsNothing() throws Exception {
		final AtomicBoolean ran = new AtomicBoolean();
		Thread.currentThread().interrupt();
		assertThrows(InterruptedException.class, () -> ThreadHelper.executeInterruptibly(() -> {
			ran.set(true);
			return "never";
		}));
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
		Thread.sleep(200);
		assertFalse(ran.get());
	}

	@Test
	void testExecuteInterruptiblyGivesUpOnTheInterruptAndTracksTheRequest() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		final AtomicLong elapsed = new AtomicLong();
		final AtomicBoolean interruptedException = new AtomicBoolean();
		final AtomicBoolean flagKept = new AtomicBoolean();
		try {
			interruptMeAfter(100);
			final long start = System.nanoTime();
			ThreadHelper.trackingAbandonedRequests(sink, () -> {
				try {
					return ThreadHelper.executeInterruptibly(() -> {
						awaitSwallowingInterrupts(release);
						return "late";
					});
				} catch (InterruptedException e) {
					interruptedException.set(true);
					flagKept.set(Thread.currentThread().isInterrupted());
					return null;
				} catch (ExecutionException e) {
					return null;
				} finally {
					elapsed.set(System.nanoTime() - start);
				}
			});
			assertTrue(interruptedException.get());
			assertTrue(flagKept.get());
			assertTrue(TimeUnit.NANOSECONDS.toMillis(elapsed.get()) < 1000, "Took " + elapsed.get() + " ns");
			assertEquals(1, sink.size());
			assertFalse(sink.get(0).isTerminated(), "The request is still running");
		} finally {
			Thread.interrupted();
			release.countDown();
		}
		assertTrue(sink.get(0).awaitTermination(5, TimeUnit.SECONDS), "Terminates when the request ends");
	}

	@Test
	void testExecuteInterruptiblyTracksNothingWithoutInterrupt() throws Exception {
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		final String result = ThreadHelper.trackingAbandonedRequests(sink, () -> {
			try {
				return ThreadHelper.executeInterruptibly(() -> "done");
			} catch (InterruptedException | ExecutionException e) {
				return null;
			}
		});
		assertEquals("done", result);
		assertTrue(sink.isEmpty());
	}

	@Test
	void testExecuteInterruptiblyHasNoTimeout() throws Exception {
		assertEquals("slow", ThreadHelper.executeInterruptibly(() -> {
			Thread.sleep(2000);
			return "slow";
		}));
	}

	@Test
	void testExecuteTracksTheRequestOfAnInterruptedCaller() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		final AtomicBoolean started = new AtomicBoolean(true);
		try {
			interruptMeAfter(100);
			ThreadHelper.trackingAbandonedRequests(sink, () -> {
				try {
					return ThreadHelper.execute(() -> {
						awaitSwallowingInterrupts(release);
						return "late";
					}, 30);
				} catch (InterruptedException e) {
					started.set(!ThreadHelper.neverStarted(e));
					return null;
				} catch (ExecutionException | TimeoutException e) {
					return null;
				}
			});
			assertTrue(started.get());
			assertEquals(1, sink.size());
			assertFalse(sink.get(0).isTerminated(), "The request is still running");
		} finally {
			Thread.interrupted();
			release.countDown();
		}
		assertTrue(sink.get(0).awaitTermination(5, TimeUnit.SECONDS), "Terminates when the request ends");
	}

	@Test
	void testExecuteTracksTheRequestThatTimedOut() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		final AtomicBoolean timedOut = new AtomicBoolean();
		try {
			ThreadHelper.trackingAbandonedRequests(sink, () -> {
				try {
					return ThreadHelper.execute(() -> {
						awaitSwallowingInterrupts(release);
						return "late";
					}, 1);
				} catch (TimeoutException e) {
					timedOut.set(true);
					return null;
				} catch (InterruptedException | ExecutionException e) {
					return null;
				}
			});
			assertTrue(timedOut.get());
			assertEquals(1, sink.size());
			assertFalse(sink.get(0).isTerminated(), "The request is still running");
		} finally {
			release.countDown();
		}
		assertTrue(sink.get(0).awaitTermination(5, TimeUnit.SECONDS), "Terminates when the request ends");
	}

	@Test
	void testExecuteLocallyNeverTracksTheComputation() {
		final CountDownLatch release = new CountDownLatch(1);
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		try {
			ThreadHelper.trackingAbandonedRequests(sink, () -> {
				try {
					return ThreadHelper.executeLocally(() -> {
						awaitSwallowingInterrupts(release);
						return "late";
					}, 1);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					return null;
				}
			});
			assertTrue(sink.isEmpty());
		} finally {
			release.countDown();
		}
	}

	@Test
	void testStartGateSettlesTheRaceOnce() throws Exception {
		// The request starts first: the caller cuts short a request that was sent
		final StartGate sent = new StartGate();
		assertTrue(sent.start());
		assertFalse(sent.abandon());
		assertFalse(sent.start());

		// The caller gives up first: the request never runs, whenever its thread gets to it
		final StartGate abandoned = new StartGate();
		assertTrue(abandoned.abandon());
		assertTrue(abandoned.abandon());
		assertFalse(abandoned.start());
		final AtomicBoolean ran = new AtomicBoolean();
		assertNull(abandoned.guard(() -> {
			ran.set(true);
			return "sent";
		}).call());
		assertFalse(ran.get());
	}

	@Test
	void testRequestOfACancelledCallerNeverStarts() throws Exception {
		Thread.currentThread().interrupt();
		final InterruptedException notStarted = assertThrows(
			InterruptedException.class,
			() -> ThreadHelper.executeInterruptibly(() -> "sent")
		);
		assertTrue(ThreadHelper.neverStarted(notStarted));
		assertTrue(ThreadHelper.neverStarted(new IOException("wrapped", notStarted)));
		Thread.interrupted();

		// A request interrupted while it runs did start
		final CountDownLatch release = new CountDownLatch(1);
		interruptMeAfter(100);
		final InterruptedException started = assertThrows(
			InterruptedException.class,
			() -> ThreadHelper.executeInterruptibly(() -> {
				awaitSwallowingInterrupts(release);
				return "sent";
			})
		);
		release.countDown();
		assertFalse(ThreadHelper.neverStarted(started));
	}

	@Test
	void testShelterInterruptedCallerRunsTheTaskWithAClearFlag() {
		final AtomicBoolean taskInterrupted = new AtomicBoolean(true);
		final AtomicBoolean done = new AtomicBoolean();
		Thread.currentThread().interrupt();
		ThreadHelper.shelter(() -> {
			taskInterrupted.set(Thread.currentThread().isInterrupted());
			done.set(true);
		}, 2, false);
		assertTrue(Thread.currentThread().isInterrupted());
		assertFalse(taskInterrupted.get());
		assertTrue(done.get());
	}

	@Test
	void testShelterInterruptedAtEntryGivesTheGraceThenCancels() {
		final CountDownLatch cancelled = new CountDownLatch(1);
		Thread.currentThread().interrupt();
		final long start = System.nanoTime();
		ThreadHelper.shelter(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException e) {
				cancelled.countDown();
			}
		}, 1, false);
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		assertTrue(Thread.currentThread().isInterrupted());
		assertTrue(elapsedMillis >= 900 && elapsedMillis < 2500, "Took " + elapsedMillis + " ms");
		assertEquals(0, cancelled.getCount());
	}

	@Test
	void testShelterInterruptedDuringTheTaskGivesTheGraceThenCancels() {
		final CountDownLatch cancelled = new CountDownLatch(1);
		interruptMeAfter(200);
		final long start = System.nanoTime();
		ThreadHelper.shelter(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException e) {
				cancelled.countDown();
			}
		}, 1, false);
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		assertTrue(Thread.currentThread().isInterrupted());
		// 200 ms before the interrupt, then 1 s of grace, then at most 1 s for the cancelled task
		assertTrue(elapsedMillis >= 1100 && elapsedMillis < 2700, "Took " + elapsedMillis + " ms");
		assertEquals(0, cancelled.getCount());
	}

	@Test
	void testShelterRethrowsUnwrapped() {
		final IllegalArgumentException runtime = new IllegalArgumentException("runtime");
		assertEquals(runtime, assertThrows(IllegalArgumentException.class, () -> ThreadHelper.shelter(() -> {
			throw runtime;
		}, 1, false)));
		final AssertionError error = new AssertionError("error");
		assertEquals(error, assertThrows(AssertionError.class, () -> ThreadHelper.shelter(() -> {
			throw error;
		}, 1, false)));
	}

	@Test
	void testShelterInterruptedAtEntryRunsTheHookBeforeTheTask() {
		final AtomicBoolean hookRan = new AtomicBoolean();
		final AtomicBoolean hookRanBeforeTheTask = new AtomicBoolean();
		final AtomicBoolean taskRan = new AtomicBoolean();
		Thread.currentThread().interrupt();
		ThreadHelper.onInterrupt(() -> {
			hookRan.set(true);
			hookRanBeforeTheTask.set(!taskRan.get());
		}, () -> ThreadHelper.shelter(() -> taskRan.set(true), 1, false));
		assertTrue(Thread.currentThread().isInterrupted());
		assertTrue(hookRan.get());
		assertTrue(hookRanBeforeTheTask.get());
	}

	@Test
	void testShelterRunsTheInterruptHookAsSoonAsItSeesTheInterrupt() {
		final CountDownLatch hookRan = new CountDownLatch(1);
		final AtomicBoolean hookRanWhileTheTaskRuns = new AtomicBoolean();
		interruptMeAfter(200);
		ThreadHelper.onInterrupt(hookRan::countDown, () -> ThreadHelper.shelter(() -> {
			// The task outlives the interrupt: the hook must not wait for it
			try {
				hookRanWhileTheTaskRuns.set(hookRan.await(5, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, 10, false));
		assertTrue(Thread.currentThread().isInterrupted());
		assertTrue(hookRanWhileTheTaskRuns.get());
	}

	@Test
	void testShelterWithoutInterruptRunsNoHookAndLeavesNoHook() {
		final AtomicBoolean hookRan = new AtomicBoolean();
		ThreadHelper.onInterrupt(() -> hookRan.set(true), () -> ThreadHelper.shelter(() -> {}, 1, false));
		assertFalse(hookRan.get());
		// Outside its scope the hook is gone
		Thread.currentThread().interrupt();
		ThreadHelper.shelter(() -> {}, 1, false);
		assertFalse(hookRan.get());
	}

	@Test
	void testShelteredTaskGivenUpOnStaysCancelledWhenItsInterruptIsSwallowed() throws InterruptedException {
		final CountDownLatch givenUp = new CountDownLatch(1);
		final CountDownLatch checked = new CountDownLatch(1);
		final AtomicBoolean cancelledDuringTheGrace = new AtomicBoolean(true);
		final AtomicBoolean cancelledAfterwards = new AtomicBoolean();
		Thread.currentThread().interrupt();
		ThreadHelper.shelter(() -> {
			cancelledDuringTheGrace.set(ThreadHelper.isCancelled());
			// A library that swallows every interrupt, the cancel of the shelter included
			awaitSwallowingInterrupts(givenUp);
			Thread.interrupted();
			cancelledAfterwards.set(ThreadHelper.isCancelled());
			checked.countDown();
		}, 1, false);
		// shelter gave up on the task and restored the interrupt of the caller, which the token never reaches
		assertTrue(Thread.interrupted());
		assertFalse(ThreadHelper.isCancelled());
		// The task now resumes with a clear interrupt status
		givenUp.countDown();
		assertTrue(checked.await(5, TimeUnit.SECONDS));
		assertFalse(cancelledDuringTheGrace.get());
		assertTrue(cancelledAfterwards.get());
	}

	@Test
	void testCloseInterruptiblyInterruptedCallerReturnsAtOnceAndTracksTheClose() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final List<ExecutorService> sink = new CopyOnWriteArrayList<>();
		Thread.currentThread().interrupt();
		final long start = System.nanoTime();
		ThreadHelper.trackingAbandonedRequests(sink, () -> {
			ThreadHelper.closeInterruptibly(() -> {
				awaitSwallowingInterrupts(release);
				closed.countDown();
			});
			return null;
		});
		assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
		assertEquals(1, sink.size());
		assertFalse(sink.get(0).isTerminated());
		release.countDown();
		assertTrue(closed.await(5, TimeUnit.SECONDS), "The close still completes");
		assertTrue(sink.get(0).awaitTermination(5, TimeUnit.SECONDS));
	}

	@Test
	void testCloseInterruptiblyWaitsAndRethrows() {
		final AtomicBoolean closed = new AtomicBoolean();
		ThreadHelper.closeInterruptibly(() -> {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				return;
			}
			closed.set(true);
		});
		assertTrue(closed.get());
		final IllegalStateException failure = new IllegalStateException("close failed");
		assertEquals(failure, assertThrows(IllegalStateException.class, () -> ThreadHelper.closeInterruptibly(() -> {
			throw failure;
		})));
	}
}
