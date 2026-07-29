package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.AgentContextReaderTracker;
import org.metricshub.web.dto.RestartStatus;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestAck;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestResult;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class AgentLifecycleServiceTest {

	private AgentContextHolder agentContextHolder;
	private AgentContext bootContext;
	private AgentLifecycleService agentLifecycleService;

	@BeforeEach
	void setUp() {
		bootContext = mockContext();
		agentContextHolder = new AgentContextHolder(bootContext);
		agentLifecycleService = new AgentLifecycleService(agentContextHolder);
	}

	@AfterEach
	void tearDown() {
		agentLifecycleService.shutdown();
	}

	@Test
	void testRestartSwapsHolderAndClosesPreviousContext() {
		final AgentContext reloadedContext = mockContext();

		agentLifecycleService.restart(bootContext, reloadedContext);

		verify(bootContext.getTaskSchedulingService()).stop();
		verify(bootContext.getOtelCollectorProcessService()).stop();
		verify(reloadedContext.getOtelCollectorProcessService()).launch();
		verify(reloadedContext.getTaskSchedulingService()).start();

		assertEquals(reloadedContext, agentContextHolder.getAgentContext());
		assertEquals(2L, agentContextHolder.getGeneration());
		verify(bootContext).close();
	}

	@Test
	void testInitialStatusIsIdle() {
		final RestartStatus status = agentLifecycleService.getRestartStatus();
		assertEquals(RestartStatus.State.IDLE, status.getState());
		assertEquals("No restart requested yet.", status.getMessage());
		assertNull(status.getRequestId());
	}

	@Test
	void testRestartAsyncSchedulesRestartAndUpdatesStatus() {
		final AgentContext reloadedContext = mockContext();

		final RestartRequestAck ack = agentLifecycleService.restartAsync(() -> reloadedContext);
		assertEquals(RestartRequestResult.SCHEDULED, ack.result());

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> {
				final RestartStatus status = agentLifecycleService.getRestartStatus();
				assertEquals(RestartStatus.State.SUCCEEDED, status.getState());
				assertEquals("MetricsHub Agent restarted successfully.", status.getMessage());
				assertNotNull(status.getStartedAt());
				assertNotNull(status.getEndedAt());
				assertEquals(2L, status.getContextGeneration());
				assertEquals(ack.requestId(), status.getRequestId());
			});

		verify(bootContext.getTaskSchedulingService()).stop();
		verify(bootContext.getOtelCollectorProcessService()).stop();
		verify(reloadedContext.getOtelCollectorProcessService()).launch();
		verify(reloadedContext.getTaskSchedulingService()).start();
		verify(bootContext).close();
		assertEquals(reloadedContext, agentContextHolder.getAgentContext());
	}

	@Test
	void testRestartAsyncQueuesConcurrentRequestsAndRunsThemAfter() {
		final AgentContext firstReloaded = mockContext();
		final AgentContext secondReloaded = mockContext();

		final CountDownLatch releaseFirstRestart = new CountDownLatch(1);

		// Block the first restart on the running scheduler stop so we can fire a
		// concurrent request while it is still in progress. Extract the mock first
		// so Mockito's stubbing DSL is not confused by nested mock calls.
		final TaskSchedulingService bootScheduler = bootContext.getTaskSchedulingService();
		doAnswer(_ -> {
			releaseFirstRestart.await(5, TimeUnit.SECONDS);
			return null;
		})
			.when(bootScheduler)
			.stop();

		final RestartRequestAck first = agentLifecycleService.restartAsync(() -> firstReloaded);
		assertEquals(RestartRequestResult.SCHEDULED, first.result());

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.until(() -> agentLifecycleService.getRestartStatus().getState() == RestartStatus.State.IN_PROGRESS);

		final RestartRequestAck second = agentLifecycleService.restartAsync(() -> secondReloaded);
		assertEquals(RestartRequestResult.QUEUED, second.result());
		assertTrue(second.requestId() > first.requestId(), "Request ids must be monotonically increasing");

		// While the first restart is still running, the exposed status must reference the
		// FIRST request — a client polling for the queued second request must not mistake
		// it for its own outcome.
		assertEquals(first.requestId(), agentLifecycleService.getRestartStatus().getRequestId());

		// Release the first restart; the queued second one must then run automatically.
		releaseFirstRestart.countDown();

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> {
				final RestartStatus status = agentLifecycleService.getRestartStatus();
				assertEquals(RestartStatus.State.SUCCEEDED, status.getState());
				// Two swaps happened: bootstrap -> firstReloaded -> secondReloaded
				assertEquals(3L, status.getContextGeneration());
				// The terminal status must be correlated with the queued (second) request
				assertEquals(second.requestId(), status.getRequestId());
			});

		// Both reloaded contexts had their scheduler / OTEL launched and started
		verify(firstReloaded.getOtelCollectorProcessService(), times(1)).launch();
		verify(firstReloaded.getTaskSchedulingService(), times(1)).start();
		verify(secondReloaded.getOtelCollectorProcessService(), times(1)).launch();
		verify(secondReloaded.getTaskSchedulingService(), times(1)).start();

		// After the queued run, the holder points to the second reloaded context
		assertEquals(secondReloaded, agentContextHolder.getAgentContext());
		assertEquals(3L, agentContextHolder.getGeneration());

		// Both previous contexts were closed
		verify(bootContext).close();
		verify(firstReloaded).close();
	}

	@Test
	void testRestartAsyncCoalescesMultiplePendingRequests() {
		final AgentContext firstReloaded = mockContext();
		final AgentContext olderPending = mockContext();
		final AgentContext newerPending = mockContext();

		final CountDownLatch releaseFirstRestart = new CountDownLatch(1);

		final TaskSchedulingService bootScheduler = bootContext.getTaskSchedulingService();
		doAnswer(_ -> {
			releaseFirstRestart.await(5, TimeUnit.SECONDS);
			return null;
		})
			.when(bootScheduler)
			.stop();

		// First restart runs, second is queued, third coalesces with the second
		assertEquals(RestartRequestResult.SCHEDULED, agentLifecycleService.restartAsync(() -> firstReloaded).result());
		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.until(() -> agentLifecycleService.getRestartStatus().getState() == RestartStatus.State.IN_PROGRESS);

		final RestartRequestAck queuedAck = agentLifecycleService.restartAsync(() -> olderPending);
		assertEquals(RestartRequestResult.QUEUED, queuedAck.result());
		final RestartRequestAck coalescedAck = agentLifecycleService.restartAsync(() -> newerPending);
		assertEquals(RestartRequestResult.COALESCED, coalescedAck.result());
		assertTrue(coalescedAck.requestId() > queuedAck.requestId(), "Request ids must be monotonically increasing");

		// The discarded (older pending) supplier is dropped WITHOUT being invoked: suppliers are
		// lazy (they build a full context with freshly reloaded extensions), so instantiating one
		// on the coalescing caller's thread just to close it would be wasted, blocking work.
		verify(olderPending, never()).close();

		releaseFirstRestart.countDown();

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> {
				final RestartStatus status = agentLifecycleService.getRestartStatus();
				assertEquals(RestartStatus.State.SUCCEEDED, status.getState());
				assertEquals(3L, status.getContextGeneration());
				// The run serves the coalesced (newest) request; its id also covers the
				// discarded queued request (coalescedAck.requestId() > queuedAck.requestId()),
				// so a client polling for either request resolves on this status.
				assertEquals(coalescedAck.requestId(), status.getRequestId());
			});

		// Only firstReloaded and newerPending were activated; olderPending was never launched
		verify(firstReloaded.getOtelCollectorProcessService(), times(1)).launch();
		verify(newerPending.getOtelCollectorProcessService(), times(1)).launch();
		verify(olderPending, never()).getOtelCollectorProcessService();
		verify(olderPending, never()).getTaskSchedulingService();

		// Final active context is newerPending
		assertEquals(newerPending, agentContextHolder.getAgentContext());
	}

	@Test
	void testRestartAsyncCapturesFailureAndClosesReloadedContext() {
		final AgentContext reloadedContext = mockContext();
		// Force the actual restart to fail by making the running scheduler throw on stop().
		final TaskSchedulingService bootScheduler = bootContext.getTaskSchedulingService();
		doAnswer(_ -> {
			throw new IllegalStateException("boom");
		})
			.when(bootScheduler)
			.stop();

		final RestartRequestAck ack = agentLifecycleService.restartAsync(() -> reloadedContext);
		assertEquals(RestartRequestResult.SCHEDULED, ack.result());

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> {
				final RestartStatus status = agentLifecycleService.getRestartStatus();
				assertEquals(RestartStatus.State.FAILED, status.getState());
				assertTrue(status.getMessage().contains("boom"), "Status message should carry the error");
				assertNotNull(status.getEndedAt());
				assertEquals(ack.requestId(), status.getRequestId());
			});

		// Reloaded context that we built but never activated must be closed to avoid leaks
		verify(reloadedContext).close();

		// Holder was not swapped
		assertEquals(bootContext, agentContextHolder.getAgentContext());
		assertEquals(1L, agentContextHolder.getGeneration());
	}

	@Test
	void testPreAndPostRestartHooksFireInOrderAroundTheRestart() {
		final AgentContext reloadedContext = mockContext();
		final java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		final java.util.concurrent.atomic.AtomicReference<AgentContext> postArg =
			new java.util.concurrent.atomic.AtomicReference<>();

		agentLifecycleService.addPreRestartHook(() -> events.add("pre"));
		agentLifecycleService.addPostRestartHook(ctx -> {
			events.add("post");
			postArg.set(ctx);
		});

		agentLifecycleService.restart(bootContext, reloadedContext);

		assertEquals(java.util.List.of("pre", "post"), events);
		assertEquals(reloadedContext, postArg.get());
		verify(reloadedContext.getTaskSchedulingService()).start();
		verify(bootContext).close();
	}

	@Test
	void testHookExceptionsDoNotAbortRestart() {
		final AgentContext reloadedContext = mockContext();
		agentLifecycleService.addPreRestartHook(() -> {
			throw new IllegalStateException("pre-boom");
		});
		agentLifecycleService.addPostRestartHook(_ -> {
			throw new IllegalStateException("post-boom");
		});

		// Must not propagate; restart completes normally
		agentLifecycleService.restart(bootContext, reloadedContext);

		assertEquals(reloadedContext, agentContextHolder.getAgentContext());
		verify(bootContext).close();
	}

	@Test
	void testRestartLaunchesCollectorByDefault() {
		final AgentContext reloadedContext = mockContext();

		agentLifecycleService.restart(bootContext, reloadedContext);

		// Community default: predicate returns true, so the collector is launched
		verify(reloadedContext.getOtelCollectorProcessService(), times(1)).launch();
	}

	@Test
	void testRestartSkipsCollectorLaunchWhenPredicateReturnsFalse() {
		final AgentContext reloadedContext = mockContext();
		agentLifecycleService.setCollectorLaunchPredicate(_ -> false);

		agentLifecycleService.restart(bootContext, reloadedContext);

		// Predicate refused: collector must NOT be launched...
		verify(reloadedContext.getOtelCollectorProcessService(), never()).launch();
		// ...but the reloaded context still becomes active and its scheduler still starts
		assertEquals(reloadedContext, agentContextHolder.getAgentContext());
		verify(reloadedContext.getTaskSchedulingService(), times(1)).start();
		verify(bootContext).close();
	}

	@Test
	void testSetCollectorLaunchPredicateRejectsNull() {
		try {
			agentLifecycleService.setCollectorLaunchPredicate(null);
			throw new AssertionError("Expected NullPointerException");
		} catch (NullPointerException expected) {
			// ok
		}
	}

	private static AgentContext mockContext() {
		final AgentContext context = mock(AgentContext.class);
		final TaskSchedulingService scheduling = mock(TaskSchedulingService.class);
		final OtelCollectorProcessService otel = mock(OtelCollectorProcessService.class);
		when(context.getTaskSchedulingService()).thenReturn(scheduling);
		when(context.getOtelCollectorProcessService()).thenReturn(otel);
		return context;
	}

	/**
	 * Builds a mock context whose {@link AgentContext#getExtensionManager()} returns {@code manager}.
	 */
	private static AgentContext mockContext(final ExtensionManager manager) {
		final AgentContext context = mockContext();
		when(context.getExtensionManager()).thenReturn(manager);
		return context;
	}

	@Test
	void testExtensionReloadDefersOldLoaderClose() {
		final ExtensionManager oldManager = mock(ExtensionManager.class);
		final ExtensionManager newManager = mock(ExtensionManager.class);

		// An extension reload swaps in a different manager (e.g. the /restart endpoint).
		agentLifecycleService.restart(mockContext(oldManager), mockContext(newManager));

		// The orphaned manager is not closed immediately; the close is deferred by the grace delay.
		verify(oldManager, never()).close();
		verify(newManager, never()).close();
	}

	@Test
	void testOrphanedManagerClosedAfterGracePeriod() {
		final ExtensionManager m0 = mock(ExtensionManager.class);
		final ExtensionManager m1 = mock(ExtensionManager.class);

		agentLifecycleService.setOrphanedLoaderCloseGraceMs(50L);

		// Two back-to-back restarts: each orphaned manager gets its own full grace window.
		final AgentContext c1 = mockContext(m1);
		agentLifecycleService.restart(mockContext(m0), c1); // orphans m0

		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m0, times(1)).close());
		verify(m1, never()).close();
	}

	@Test
	void testReusedManagerIsNeverScheduledForClose() {
		final ExtensionManager m1 = mock(ExtensionManager.class);

		agentLifecycleService.setOrphanedLoaderCloseGraceMs(50L);

		// A configuration-file reload reuses the same manager (m1 -> m1): nothing is orphaned.
		final AgentContext c1 = mockContext(m1);
		agentLifecycleService.restart(c1, mockContext(m1));

		Awaitility.await()
			.pollDelay(Duration.ofMillis(300))
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m1, never()).close());
	}

	@Test
	void testRetirementWaitsForOldSchedulerTermination() {
		final ExtensionManager m0 = mock(ExtensionManager.class);
		final ExtensionManager m1 = mock(ExtensionManager.class);

		agentLifecycleService.setOrphanedLoaderCloseGraceMs(50L);
		agentLifecycleService.setRetirementRecheckMs(50L);

		// The replaced context's scheduler is still alive (a collection may run with an unbounded
		// job timeout). Extract the mocks first so Mockito's DSL is not confused by nested calls.
		final AgentContext outgoing = mockContext(m0);
		final ScheduledThreadPoolExecutor oldExecutor = new ScheduledThreadPoolExecutor(1);
		final ThreadPoolTaskScheduler oldScheduler = mock(ThreadPoolTaskScheduler.class);
		when(oldScheduler.getScheduledThreadPoolExecutor()).thenReturn(oldExecutor);
		final TaskSchedulingService outgoingScheduling = outgoing.getTaskSchedulingService();
		when(outgoingScheduling.getTaskScheduler()).thenReturn(oldScheduler);

		agentLifecycleService.restart(outgoing, mockContext(m1));

		// Well past the grace delay, the manager must NOT be closed while the old scheduler lives.
		Awaitility.await()
			.pollDelay(Duration.ofMillis(300))
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m0, never()).close());

		// Once the old scheduler terminates, the next recheck retires the orphaned manager.
		oldExecutor.shutdown();
		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m0, times(1)).close());
	}

	@Test
	void testRetirementWaitsForRequestSideReaders() {
		final ExtensionManager m0 = mock(ExtensionManager.class);
		final ExtensionManager m1 = mock(ExtensionManager.class);

		agentLifecycleService.setOrphanedLoaderCloseGraceMs(50L);
		agentLifecycleService.setRetirementRecheckMs(50L);

		// A request-side reader (HTTP/MCP operation) entered under the outgoing generation.
		final AgentContextReaderTracker tracker = new AgentContextReaderTracker(agentContextHolder);
		agentLifecycleService.setReaderTracker(tracker);
		final long outgoingGeneration = agentContextHolder.getGeneration();
		tracker.acquire(outgoingGeneration);

		agentLifecycleService.restart(mockContext(m0), mockContext(m1)); // orphans m0

		// Not closed while the reader lease is held, well past the grace delay.
		Awaitility.await()
			.pollDelay(Duration.ofMillis(300))
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m0, never()).close());

		// Releasing the lease lets the next recheck retire the orphaned manager.
		tracker.release(outgoingGeneration);
		Awaitility.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> verify(m0, times(1)).close());
	}

	@Test
	void testShutdownClosesPendingOrphanedManagersImmediately() {
		final ExtensionManager m0 = mock(ExtensionManager.class);
		final ExtensionManager m1 = mock(ExtensionManager.class);

		// Long grace: the scheduled close cannot have fired yet when shutdown() runs.
		agentLifecycleService.setOrphanedLoaderCloseGraceMs(60_000L);
		agentLifecycleService.restart(mockContext(m0), mockContext(m1)); // orphans m0
		verify(m0, never()).close();

		// Process exit: pending retirements are flushed immediately.
		agentLifecycleService.shutdown();
		verify(m0, times(1)).close();
		verify(m1, never()).close();
	}

	@Test
	void testFailedRestartReleasesNeverActivatedReloadedManager() {
		final ExtensionManager bootManager = mock(ExtensionManager.class);
		final ExtensionManager reloadedManager = mock(ExtensionManager.class);

		// A boot context carrying its own manager, with a scheduler that throws on stop() so the
		// restart fails before the context is swapped in. Extract the scheduler mock first so
		// Mockito's stubbing DSL is not confused by the nested mock call.
		final AgentContext boot = mockContext(bootManager);
		final TaskSchedulingService bootScheduler = boot.getTaskSchedulingService();
		doAnswer(_ -> {
			throw new IllegalStateException("boom");
		})
			.when(bootScheduler)
			.stop();

		final AgentContextHolder holder = new AgentContextHolder(boot);
		final AgentLifecycleService service = new AgentLifecycleService(holder);
		try {
			service.restartAsync(() -> mockContext(reloadedManager));

			Awaitility.await()
				.atMost(Durations.FIVE_SECONDS)
				.untilAsserted(() -> assertEquals(RestartStatus.State.FAILED, service.getRestartStatus().getState()));

			// The reloaded context never became active, so its freshly reloaded extension loaders
			// are released immediately; the active (boot) manager is left untouched.
			verify(reloadedManager, times(1)).close();
			verify(bootManager, never()).close();
		} finally {
			service.shutdown();
		}
	}
}
