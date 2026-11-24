package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The beforeAll/afterAll session of a connector whose run is interrupted, and the monitor jobs that outlive the thread
 * timeout.
 */
class ConnectorSessionTest {

	private static final Path CONNECTORS = Paths.get("src", "test", "resources", "test-files", "strategy", "session");
	private static final String A = "SessionA";
	private static final String B = "SessionB";
	private static final String C = "SessionC";
	private static final String LOGIN = "${source::beforeAll.login}";
	private static final String LOGOUT = "${source::afterAll.logout}";
	private static final String FAN_DISCOVERY = "${source::monitors.fan.discovery.sources.source(1)}";
	private static final String FAN_COLLECT = "${source::monitors.fan.collect.sources.source(1)}";
	private static final long LONG_TIMEOUT = 300;

	/**
	 * The device: answers a source of a connector.
	 */
	@FunctionalInterface
	private interface Device {
		SourceTable answer(String connectorId, String sourceKey) throws Exception;
	}

	private final IProtocolExtension extension = mock(IProtocolExtension.class);

	private TelemetryManager newTelemetryManager(
		final boolean sequential,
		final long strategyTimeout,
		final Device device,
		final String... connectorIds
	) {
		final Map<String, Monitor> connectorMonitors = new HashMap<>();
		for (final String connectorId : connectorIds) {
			final Monitor connectorMonitor = Monitor.builder().type(KnownMonitorType.CONNECTOR.getKey()).build();
			connectorMonitor.addAttribute(MONITOR_ATTRIBUTE_ID, connectorId);
			connectorMonitors.put(
				String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), connectorId),
				connectorMonitor
			);
		}
		final Monitor hostMonitor = Monitor.builder().type(KnownMonitorType.HOST.getKey()).isEndpoint(true).build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.monitors(
				new HashMap<>(
					Map.of(
						KnownMonitorType.HOST.getKey(),
						new HashMap<>(Map.of("host-1", hostMonitor)),
						KnownMonitorType.CONNECTOR.getKey(),
						connectorMonitors
					)
				)
			)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId("host-1")
					.hostname("host-1")
					.hostType(DeviceKind.LINUX)
					.sequential(sequential)
					.strategyTimeout(strategyTimeout)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.connectorStore(new ConnectorStore(CONNECTORS))
			.build();

		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(SnmpTableSource.class)).when(extension).getSupportedSources();
		doAnswer(invocation -> device.answer(invocation.getArgument(1), invocation.<Source>getArgument(0).getKey())).when(
			extension
		).processSource(any(), anyString(), any());
		return telemetryManager;
	}

	private DiscoveryStrategy discovery(final TelemetryManager telemetryManager) {
		return DiscoveryStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(System.currentTimeMillis())
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build())
			.build();
	}

	private CollectStrategy collect(final TelemetryManager telemetryManager) {
		return CollectStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(System.currentTimeMillis())
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build())
			.build();
	}

	private static SourceTable table(final String csv) {
		return SourceTable.ofTable(SourceTable.csvToTable(csv, ";"));
	}

	/**
	 * A request the way a protocol extension sends it: an interrupted caller stops waiting and gets nothing.
	 */
	private static SourceTable request(final Callable<SourceTable> io) throws ExecutionException {
		try {
			return ThreadHelper.executeInterruptibly(io);
		} catch (InterruptedException e) {
			return SourceTable.empty();
		}
	}

	/**
	 * Waits for the latch like a request that ignores interrupts, and tells whether it was interrupted.
	 */
	private static boolean awaitIgnoringInterrupts(final CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				return interrupted;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
	}

	private static Set<String> fanIds(final TelemetryManager telemetryManager) {
		final Map<String, Monitor> fans = telemetryManager.findMonitorsByType("fan");
		return fans == null
			? Set.of()
			: fans.values().stream().map(fan -> fan.getAttribute(MONITOR_ATTRIBUTE_ID)).collect(Collectors.toSet());
	}

	private static Monitor createFan(final TelemetryManager telemetryManager, final String connectorId, final String id) {
		return MonitorFactory.builder()
			.monitorType("fan")
			.telemetryManager(telemetryManager)
			.connectorId(connectorId)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_ID, id)))
			.discoveryTime(System.currentTimeMillis())
			.keys(DEFAULT_KEYS)
			.build()
			.createOrUpdateMonitor();
	}

	@Test
	void testLogoutSentOnAClearThreadAfterAnInterruptDuringAMonitorJob() {
		final AtomicReference<Thread> logoutThread = new AtomicReference<>();
		final AtomicBoolean logoutInterrupted = new AtomicBoolean(true);
		final TelemetryManager telemetryManager = newTelemetryManager(
			true,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case FAN_DISCOVERY -> {
					// The job deadline expires during the monitor job
					Thread.currentThread().interrupt();
					yield table("fan-1");
				}
				case LOGOUT -> {
					logoutThread.set(Thread.currentThread());
					logoutInterrupted.set(Thread.currentThread().isInterrupted());
					yield table("bye");
				}
				default -> table("token-1");
			},
			A
		);

		try {
			discovery(telemetryManager).run();
			assertTrue(Thread.currentThread().isInterrupted(), "The interrupt of the strategy thread is restored");
		} finally {
			Thread.interrupted();
		}

		assertNotNull(logoutThread.get(), "The logout is sent");
		assertNotSame(Thread.currentThread(), logoutThread.get());
		assertFalse(logoutInterrupted.get());
		// The job cut short is not mapped
		assertEquals(Set.of(), fanIds(telemetryManager));
	}

	@Test
	void testLogoutSentAfterAnInterruptDuringACollectJob() {
		final AtomicBoolean logoutInterrupted = new AtomicBoolean(true);
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(
			true,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case FAN_COLLECT -> {
					Thread.currentThread().interrupt();
					yield table("fan-1;7");
				}
				case LOGOUT -> {
					logouts.incrementAndGet();
					logoutInterrupted.set(Thread.currentThread().isInterrupted());
					yield table("bye");
				}
				default -> table("token-1");
			},
			A
		);
		final Monitor fan = createFan(telemetryManager, A, "fan-1");

		try {
			collect(telemetryManager).run();
			assertTrue(Thread.currentThread().isInterrupted(), "The interrupt of the strategy thread is restored");
		} finally {
			Thread.interrupted();
		}

		assertEquals(1, logouts.get());
		assertFalse(logoutInterrupted.get());
		// The job cut short is not collected
		assertNull(fan.getMetric("hw.fan.speed"));
	}

	@Test
	void testLoginAnsweringAfterTheInterruptKeepsItsTokenForTheLogout() throws InterruptedException {
		final CountDownLatch loginSent = new CountDownLatch(1);
		final AtomicReference<TelemetryManager> telemetryManagerRef = new AtomicReference<>();
		final AtomicReference<SourceTable> tokenAtLogout = new AtomicReference<>();
		final TelemetryManager telemetryManager = newTelemetryManager(
			true,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case LOGIN -> request(() -> {
					loginSent.countDown();
					Thread.sleep(500);
					return table("token-1");
				});
				case LOGOUT -> {
					tokenAtLogout.set(
						telemetryManagerRef.get().getHostProperties().getConnectorNamespace(A).getSourceTable(LOGIN)
					);
					yield table("bye");
				}
				default -> table("fan-1");
			},
			A
		);
		telemetryManagerRef.set(telemetryManager);

		// The job deadline expires while the login is on the wire
		final Thread strategyThread = Thread.currentThread();
		final Thread deadline = new Thread(() -> {
			try {
				if (loginSent.await(5, TimeUnit.SECONDS)) {
					strategyThread.interrupt();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		deadline.start();
		try {
			discovery(telemetryManager).run();
		} finally {
			deadline.join();
			Thread.interrupted();
		}

		assertNotNull(tokenAtLogout.get(), "The logout is sent");
		assertEquals(List.of(List.of("token-1")), tokenAtLogout.get().getTable());
	}

	@Test
	void testRunInterruptedBeforeTheBeforeAllRunsNeitherBeforeAllNorAfterAll() {
		final AtomicInteger surroundingRequests = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(
			true,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case LOGIN, LOGOUT -> {
					surroundingRequests.incrementAndGet();
					yield table("x");
				}
				default -> table("fan-1");
			},
			A
		);

		Thread.currentThread().interrupt();
		try {
			discovery(telemetryManager).run();
		} finally {
			Thread.interrupted();
		}

		assertEquals(0, surroundingRequests.get());
	}

	@Test
	void testAfterAllOfAnInterruptedRunOnlyWithinTheCleanupWindowOfAnOpenedSession() {
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(true, LONG_TIMEOUT, (connectorId, key) -> {
			if (LOGOUT.equals(key)) {
				logouts.incrementAndGet();
			}
			return table("x");
		}, A);
		final DiscoveryStrategy strategy = discovery(telemetryManager);
		final long now = System.nanoTime();
		final long pastTheWindow = now -
			TimeUnit.SECONDS.toNanos(LONG_TIMEOUT + AbstractStrategy.CLEANUP_WINDOW_SECONDS + 1);

		Thread.currentThread().interrupt();
		try {
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), true, pastTheWindow);
			assertEquals(0, logouts.get(), "Outside the cleanup window");
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), false, now);
			assertEquals(0, logouts.get(), "No session opened by this run");
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), true, now);
			assertEquals(1, logouts.get(), "Within the cleanup window");
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}

		// Not interrupted: the afterAll always runs
		strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), false, pastTheWindow);
		assertEquals(2, logouts.get());
	}

	@Test
	void testOnlyOneAfterAllOfAnInterruptedRunUsesTheCleanupGrace() {
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(true, LONG_TIMEOUT, (connectorId, key) -> {
			if (LOGOUT.equals(key)) {
				logouts.incrementAndGet();
			}
			return table("x");
		}, A, B);
		final DiscoveryStrategy strategy = discovery(telemetryManager);
		final long now = System.nanoTime();

		// A reload cancels the run long before its timeout, and two connectors whose session it opened end afterwards
		Thread.currentThread().interrupt();
		try {
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), true, now);
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(B), true, now);
		} finally {
			Thread.interrupted();
		}

		// Each graced afterAll may last 3 s: one only, so that the run ends before ContextExecutor abandons it
		assertEquals(1, logouts.get());
	}

	@Test
	void testConnectorWithoutAfterAllLeavesTheCleanupGraceToTheNextOne() {
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(true, LONG_TIMEOUT, (connectorId, key) -> {
			if (LOGOUT.equals(key)) {
				logouts.incrementAndGet();
			}
			return table("x");
		}, A);
		final DiscoveryStrategy strategy = discovery(telemetryManager);
		final long now = System.nanoTime();
		final Connector withoutAfterAll = Connector.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename("WithoutAfterAll").build())
			.build();

		// A cancelled run ends a connector that closes nothing, then the one whose session it opened
		Thread.currentThread().interrupt();
		try {
			strategy.runAfterAll(withoutAfterAll, true, now);
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(A), true, now);
		} finally {
			Thread.interrupted();
		}

		assertEquals(1, logouts.get());
	}

	@Test
	void testAfterAllGivenUpOnSendsNothingMoreWhenItsInterruptIsSwallowed() throws InterruptedException {
		final CountDownLatch givenUp = new CountDownLatch(1);
		final AtomicInteger afterAllRequests = new AtomicInteger();
		final AtomicReference<Thread> afterAllThread = new AtomicReference<>();
		final TelemetryManager telemetryManager = newTelemetryManager(true, LONG_TIMEOUT, (connectorId, key) -> {
			if (key.startsWith("${source::afterAll.") && afterAllRequests.getAndIncrement() == 0) {
				afterAllThread.set(Thread.currentThread());
				// A request that swallows every interrupt, the cancel of the shelter included
				awaitIgnoringInterrupts(givenUp);
				Thread.interrupted();
			}
			return table("x");
		}, C);
		final DiscoveryStrategy strategy = discovery(telemetryManager);

		// The afterAll of a cancelled run outlives its grace: the shelter gives up on it
		Thread.currentThread().interrupt();
		try {
			strategy.runAfterAll(telemetryManager.getConnectorStore().getStore().get(C), true, System.nanoTime());
		} finally {
			Thread.interrupted();
		}
		givenUp.countDown();
		afterAllThread.get().join(5000);

		// It resumes with a clear interrupt status, yet sends no other afterAll source and stores nothing
		assertFalse(afterAllThread.get().isAlive());
		assertEquals(1, afterAllRequests.get());
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(C);
		assertNull(namespace.getSourceTable(LOGOUT));
		assertNull(namespace.getSourceTable("${source::afterAll.audit}"));
	}

	@Test
	void testDeferredPoolIsCutWhenAShelteredBeforeAllSeesTheCancel() {
		assertDeferredPoolCutDuringTheBeforeAllOfTheNextConnector(false);
	}

	@Test
	void testDeferredCollectPoolIsCutWhenAShelteredBeforeAllSeesTheCancel() {
		assertDeferredPoolCutDuringTheBeforeAllOfTheNextConnector(true);
	}

	/**
	 * The monitor job of connector A outlives the thread timeout, then the run is cancelled while the strategy thread
	 * waits for the sheltered beforeAll of connector B, which clears its interrupt. The job of A must be interrupted
	 * without waiting for that beforeAll to end.
	 *
	 * @param collect Whether to run the collect strategy rather than the discovery
	 */
	private void assertDeferredPoolCutDuringTheBeforeAllOfTheNextConnector(final boolean collect) {
		final CountDownLatch never = new CountDownLatch(1);
		final CountDownLatch jobCut = new CountDownLatch(1);
		final AtomicBoolean cutDuringTheLogin = new AtomicBoolean();
		final Thread strategyThread = Thread.currentThread();
		final String job = collect ? FAN_COLLECT : FAN_DISCOVERY;
		final TelemetryManager telemetryManager = newTelemetryManager(false, LONG_TIMEOUT, (connectorId, key) -> {
			if (job.equals(key) && A.equals(connectorId)) {
				final SourceTable answer = request(() -> {
					never.await();
					return table("fan-1;7");
				});
				jobCut.countDown();
				return answer;
			}
			if (LOGIN.equals(key) && B.equals(connectorId)) {
				// The cancel of a reload, while the login of B runs on its sheltered thread
				strategyThread.interrupt();
				boolean cut = false;
				final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
				while (!cut && System.nanoTime() - end < 0) {
					try {
						cut = jobCut.await(end - System.nanoTime(), TimeUnit.NANOSECONDS);
					} catch (InterruptedException e) {
						// A login that ignores interrupts
					}
				}
				cutDuringTheLogin.set(cut);
			}
			return table("token");
		}, A, B);
		createFan(telemetryManager, A, "fan-1");
		final AbstractStrategy strategy = spy(collect ? collect(telemetryManager) : discovery(telemetryManager));
		doReturn(1L).when(strategy).getThreadTimeout();

		try {
			strategy.run();
		} finally {
			Thread.interrupted();
			never.countDown();
		}

		assertTrue(cutDuringTheLogin.get());
	}

	@Test
	void testDeferredPoolIsCutOnceTheStrategyThreadIsInterrupted() {
		assertDeferredPoolCutWhileTheNextConnectorRuns(false);
	}

	@Test
	void testDeferredCollectPoolIsCutOnceTheStrategyThreadIsInterrupted() {
		assertDeferredPoolCutWhileTheNextConnectorRuns(true);
	}

	/**
	 * The monitor job of connector A outlives the thread timeout, then the run is cancelled while the strategy thread is
	 * busy with connector B, in code that ignores interrupts. The job of A must be interrupted without waiting for the
	 * strategy thread.
	 *
	 * @param collect Whether to run the collect strategy rather than the discovery
	 */
	private void assertDeferredPoolCutWhileTheNextConnectorRuns(final boolean collect) {
		final CountDownLatch never = new CountDownLatch(1);
		final CountDownLatch jobCut = new CountDownLatch(1);
		final String job = collect ? FAN_COLLECT : FAN_DISCOVERY;
		final TelemetryManager telemetryManager = newTelemetryManager(false, LONG_TIMEOUT, (connectorId, key) -> {
			if (job.equals(key) && A.equals(connectorId)) {
				final SourceTable answer = request(() -> {
					never.await();
					return table("fan-1;7");
				});
				jobCut.countDown();
				return answer;
			}
			return table("token");
		}, A, B);
		createFan(telemetryManager, A, "fan-1");
		final AbstractStrategy strategy = spy(collect ? collect(telemetryManager) : discovery(telemetryManager));
		doReturn(1L).when(strategy).getThreadTimeout();
		final AtomicBoolean cutWhileBusy = new AtomicBoolean();
		doAnswer(invocation -> {
			if (B.equals(invocation.<Connector>getArgument(0).getCompiledFilename())) {
				Thread.currentThread().interrupt();
				final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (jobCut.getCount() > 0 && System.nanoTime() - end < 0) {
					Thread.onSpinWait();
				}
				cutWhileBusy.set(jobCut.getCount() == 0);
			}
			return invocation.callRealMethod();
		}).when(strategy).validateConnectorDetectionCriteria(any(), anyString(), anyString());

		try {
			strategy.run();
		} finally {
			Thread.interrupted();
			never.countDown();
		}

		assertTrue(cutWhileBusy.get());
	}

	@Test
	void testBlockingLogoutOfAnInterruptedRunIsBounded() {
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicBoolean logoutCancelled = new AtomicBoolean();
		final TelemetryManager telemetryManager = newTelemetryManager(
			true,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case FAN_DISCOVERY -> {
					Thread.currentThread().interrupt();
					yield table("fan-1");
				}
				case LOGOUT -> {
					logoutCancelled.set(awaitIgnoringInterrupts(release));
					yield table("bye");
				}
				default -> table("token-1");
			},
			A
		);

		final long start = System.nanoTime();
		try {
			discovery(telemetryManager).run();
		} finally {
			Thread.interrupted();
		}
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		release.countDown();

		// The 2 s grace of the afterAll, then 1 s for the cancelled logout
		assertTrue(elapsedMillis >= 2_900, "Returned after " + elapsedMillis + " ms");
		assertTrue(elapsedMillis < 5_000, "Returned after " + elapsedMillis + " ms");
		assertTrue(logoutCancelled.get());
	}

	@Test
	void testPoolPastTheThreadTimeoutIsJoinedAfterTheNextConnectors() {
		final CountDownLatch nextConnectorStarted = new CountDownLatch(1);
		final List<String> events = Collections.synchronizedList(new ArrayList<>());
		final TelemetryManager telemetryManager = newTelemetryManager(
			false,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case LOGIN -> {
					if (B.equals(connectorId)) {
						nextConnectorStarted.countDown();
					}
					yield table("token");
				}
				case FAN_DISCOVERY -> A.equals(connectorId) ? request(() -> {
					// Answers once the next connector started, after the thread timeout of this one
					events.add(nextConnectorStarted.await(5, TimeUnit.SECONDS) ? "job A" : "job A alone");
					return table("fanA");
				}) : table("fanB");
				case LOGOUT -> {
					events.add("logout " + connectorId);
					yield table("bye");
				}
				default -> SourceTable.empty();
			},
			A,
			B
		);
		final DiscoveryStrategy strategy = spy(discovery(telemetryManager));
		doReturn(1L).when(strategy).getThreadTimeout();

		strategy.run();

		// The next connector started while the job of the first one was still running, and the job was still mapped
		assertTrue(events.contains("job A"), events::toString);
		assertEquals(Set.of("fanA", "fanB"), fanIds(telemetryManager));
		// The first connector logs out only once its job is done, after the next connector
		assertEquals("logout " + A, events.get(events.size() - 1), events::toString);
		assertTrue(events.indexOf("logout " + B) < events.indexOf("logout " + A), events::toString);
	}

	@Test
	void testPoolPastTheStrategyDeadlineIsCutAndAbandoned() throws InterruptedException {
		final CountDownLatch never = new CountDownLatch(1);
		final CountDownLatch jobCut = new CountDownLatch(1);
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(false, 2, (connectorId, key) -> switch (key) {
			case FAN_DISCOVERY -> {
				final SourceTable answer = request(() -> {
					never.await();
					return table("fan-1");
				});
				if (Thread.currentThread().isInterrupted()) {
					jobCut.countDown();
				}
				yield answer;
			}
			case LOGOUT -> {
				logouts.incrementAndGet();
				yield table("bye");
			}
			default -> table("token");
		}, A);
		createFan(telemetryManager, A, "fan-old");
		final DiscoveryStrategy strategy = spy(discovery(telemetryManager));
		doReturn(1L).when(strategy).getThreadTimeout();

		final long start = System.nanoTime();
		strategy.run();
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		// Waited past the 1 s thread timeout, until the 2 s strategy deadline, then cut the job
		assertTrue(elapsedMillis >= 1_900, "Returned after " + elapsedMillis + " ms");
		assertTrue(elapsedMillis < 3_500, "Returned after " + elapsedMillis + " ms");
		assertTrue(jobCut.await(1, TimeUnit.SECONDS));
		never.countDown();
		// Abandoned: nothing mapped, nothing removed, and the session closed
		assertEquals(Set.of("fan-old"), fanIds(telemetryManager));
		assertEquals(1, logouts.get());
	}

	@Test
	void testCollectPoolPastTheThreadTimeoutIsCollectedBeforeTheDeadline() {
		final List<String> events = Collections.synchronizedList(new ArrayList<>());
		final TelemetryManager telemetryManager = newTelemetryManager(
			false,
			LONG_TIMEOUT,
			(connectorId, key) -> switch (key) {
				case FAN_COLLECT -> request(() -> {
					Thread.sleep(1_500);
					events.add("job");
					return table("fan-1;7");
				});
				case LOGOUT -> {
					events.add("logout");
					yield table("bye");
				}
				default -> table("token");
			},
			A
		);
		final Monitor fan = createFan(telemetryManager, A, "fan-1");
		final CollectStrategy strategy = spy(collect(telemetryManager));
		doReturn(1L).when(strategy).getThreadTimeout();

		strategy.run();

		final NumberMetric speed = fan.getMetric("hw.fan.speed", NumberMetric.class);
		assertNotNull(speed, "The job past the thread timeout is collected");
		assertEquals(7.0, speed.getValue());
		assertEquals(List.of("job", "logout"), events);
	}

	@Test
	void testStrategyStuckInARequestEndsRightAfterItsTimeout() {
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicInteger logouts = new AtomicInteger();
		final TelemetryManager telemetryManager = newTelemetryManager(true, 1, (connectorId, key) -> switch (key) {
			case FAN_DISCOVERY -> request(() -> {
				awaitIgnoringInterrupts(release);
				return table("fan-1");
			});
			case LOGOUT -> {
				logouts.incrementAndGet();
				yield table("bye");
			}
			default -> table("token");
		}, A);

		final long start = System.nanoTime();
		try {
			assertThrows(TimeoutException.class, () -> new ContextExecutor(discovery(telemetryManager)).execute());
		} finally {
			release.countDown();
		}
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		// The strategy thread ended right after the interrupt: ContextExecutor did not wait its 10 s grace
		assertTrue(elapsedMillis < 4_000, "Returned after " + elapsedMillis + " ms");
		assertEquals(1, logouts.get());
		assertEquals(Set.of(), fanIds(telemetryManager));
	}
}
