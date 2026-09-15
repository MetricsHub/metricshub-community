package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MultiHostToolExecutorTest {

	@Test
	void shouldExecuteEachHostnameWhenNothingIsCancelled() {
		final var response = MultiHostToolExecutor.executeForHosts(
			List.of("host1", "host2"),
			() -> HostToolResponse.<String>builder().build(),
			hostname -> HostToolResponse.<String>builder().hostname(hostname).response("ok").build(),
			2
		);

		assertEquals(List.of("host1", "host2"), response.getHosts().stream().map(HostToolResponse::getHostname).toList());
	}

	@Test
	void shouldStopPerHostTasksWhenCallingTaskIsCancelled() throws Exception {
		final var started = new CountDownLatch(2);
		final var interrupted = new CountDownLatch(2);
		final var completed = new AtomicInteger();

		final ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			final Future<?> callingTask = caller.submit(() ->
				MultiHostToolExecutor.executeForHosts(
					List.of("host1", "host2"),
					() -> HostToolResponse.<String>builder().build(),
					hostname -> {
						started.countDown();
						try {
							// Stands for a long-running host operation
							Thread.sleep(60_000L);
							completed.incrementAndGet();
						} catch (InterruptedException interruptedException) {
							Thread.currentThread().interrupt();
							interrupted.countDown();
						}
						return HostToolResponse.<String>builder().hostname(hostname).build();
					},
					2
				)
			);

			assertTrue(started.await(10, TimeUnit.SECONDS), "The per-host tasks should have started");

			callingTask.cancel(true);

			assertTrue(interrupted.await(10, TimeUnit.SECONDS), "The per-host tasks should have been interrupted");
			assertEquals(0, completed.get(), "No per-host task should have run to completion");
		} finally {
			caller.shutdownNow();
		}
	}

	/**
	 * A host operation blocked in a socket read never observes its interruption, and no pool can be
	 * terminated while it runs. The caller must be held until then, so that such threads stay under
	 * the caller's own ceiling instead of a live pool being left behind per cancelled invocation.
	 */
	@Test
	void shouldHoldTheCallerWhileAHostOperationIgnoresItsInterruption() throws Exception {
		final var started = new CountDownLatch(1);
		final var release = new CountDownLatch(1);
		final var callerReturned = new CountDownLatch(1);
		final var hostTaskFinished = new AtomicBoolean();

		final ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			final Future<?> callingTask = caller.submit(() -> {
				try {
					MultiHostToolExecutor.executeForHosts(
						List.of("host1"),
						() -> HostToolResponse.<String>builder().build(),
						hostname -> {
							started.countDown();
							// Stands for a host operation that cannot be interrupted at all
							while (release.getCount() > 0) {
								try {
									release.await();
								} catch (InterruptedException interruptedException) {
									// Swallowed on purpose, exactly as a blocking socket read does
								}
							}
							hostTaskFinished.set(true);
							return HostToolResponse.<String>builder().hostname(hostname).build();
						},
						2
					);
				} catch (CancellationException cancellationException) {
					// Expected once the pool is gone
				} finally {
					callerReturned.countDown();
				}
			});

			assertTrue(started.await(10, TimeUnit.SECONDS), "The per-host task should have started");

			callingTask.cancel(true);

			assertFalse(
				callerReturned.await(500, TimeUnit.MILLISECONDS),
				"The caller must stay held while the pool it created is still running"
			);
			assertFalse(hostTaskFinished.get(), "The per-host task should still be running");

			release.countDown();

			assertTrue(callerReturned.await(10, TimeUnit.SECONDS), "The caller returns once the pool has terminated");
			assertTrue(hostTaskFinished.get(), "The per-host task held its caller instead of outliving it");
		} finally {
			caller.shutdownNow();
		}
	}

	@Test
	void shouldStopTheSequentialFanOutWhenTheCallingThreadIsInterrupted() {
		final List<String> visited = new ArrayList<>();
		try {
			assertThrows(CancellationException.class, () ->
				MultiHostToolExecutor.executeForHosts(
					List.of("host1", "host2"),
					() -> HostToolResponse.<String>builder().build(),
					hostname -> {
						visited.add(hostname);
						// Stands for the deadline firing while the first host is being processed
						Thread.currentThread().interrupt();
						return HostToolResponse.<String>builder().hostname(hostname).build();
					},
					1
				)
			);
		} finally {
			// Never leave the flag set on a thread the following tests reuse
			Thread.interrupted();
		}

		assertEquals(List.of("host1"), visited, "The remaining hostnames should not have been visited");
	}
}
