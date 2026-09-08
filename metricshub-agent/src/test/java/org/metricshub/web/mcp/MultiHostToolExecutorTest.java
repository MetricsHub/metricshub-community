package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
}
