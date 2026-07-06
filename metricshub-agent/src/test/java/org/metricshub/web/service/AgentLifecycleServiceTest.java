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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.RestartStatus;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestAck;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestResult;

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

		// The discarded (older pending) context should be closed by the service so we don't leak it
		verify(olderPending, times(1)).close();

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
}
