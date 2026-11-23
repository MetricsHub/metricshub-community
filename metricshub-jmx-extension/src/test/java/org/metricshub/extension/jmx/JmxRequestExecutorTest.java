package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;

class JmxRequestExecutorTest {

	private JmxRequestExecutor jmxRequestExecutor;
	private JMXConnector jmxConnectorMock;
	private MBeanServerConnection mBeanServerConnectionMock;
	private RequestStats requestStats;

	@BeforeEach
	void setUp() throws Exception {
		jmxRequestExecutor = spy(new JmxRequestExecutor());
		jmxConnectorMock = mock(JMXConnector.class);
		mBeanServerConnectionMock = mock(MBeanServerConnection.class);
		requestStats = new RequestStats();

		// By default, stub connect to return our mock connector
		doReturn(jmxConnectorMock).when(jmxRequestExecutor).connect(any(), any(), any(), any());
		when(jmxConnectorMock.getMBeanServerConnection()).thenReturn(mBeanServerConnectionMock);
	}

	/**
	 * Asserts the JMX counters of the given stats, and that no other protocol recorded anything.
	 */
	private static void assertCounts(
		final RequestStats stats,
		final long completed,
		final long timeout,
		final long failed
	) {
		assertEquals(
			Map.of(
				JmxExtension.IDENTIFIER,
				Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
			),
			stats.snapshot()
		);
	}

	/**
	 * Asserts the JMX counters of {@link #requestStats}.
	 */
	private void assertCounts(final long completed, final long timeout, final long failed) {
		assertCounts(requestStats, completed, timeout, failed);
	}

	/**
	 * Makes connect block, ignoring interrupts, until the returned latch is released, and stores the worker thread.
	 */
	private CountDownLatch blockConnect(final AtomicReference<Thread> worker) throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			worker.set(Thread.currentThread());
			while (true) {
				try {
					release.await();
					return jmxConnectorMock;
				} catch (InterruptedException e) {
					// Ignore the cancellation, as a client that does not honor interrupts would
				}
			}
		}).when(jmxRequestExecutor).connect(any(), any(), any(), any());
		return release;
	}

	@Test
	void testFetchMBeanReturnsMatchedAttributes() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("HeapMemoryUsage"))).thenReturn("1024");

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("1024", result.get(0).get(0));
		assertCounts(1, 0, 0);
	}

	@Test
	void testFetchMBeanReturnsEmptyWhenNoMatches() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Collections.emptySet());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=NonExistent",
			List.of("attr"),
			List.of(),
			requestStats,
			null
		);

		assertNotNull(result);
		assertTrue(result.isEmpty());
		// A valid empty answer
		assertCounts(1, 0, 0);
	}

	@Test
	void testFetchMBeanIncludesKeyProperties() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final Hashtable<String, String> keyTable = new Hashtable<>();
		keyTable.put("type", "GarbageCollector");
		keyTable.put("name", "G1 Young Gen");
		final ObjectName objectName = new ObjectName("java.lang", keyTable);

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("CollectionCount"))).thenReturn(42L);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=GarbageCollector,name=*",
			List.of("CollectionCount"),
			List.of("name", "type"),
			null,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		final List<String> row = result.get(0);
		assertEquals(3, row.size());
		assertEquals("G1 Young Gen", row.get(0));
		assertEquals("GarbageCollector", row.get(1));
		assertEquals("42", row.get(2));
	}

	@Test
	void testFetchMBeanHandlesMissingKeyProperty() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of(),
			List.of("nonExistentKey"),
			null,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("", result.get(0).get(0));
	}

	@Test
	void testFetchMBeanHandlesAttributeException() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(any(), eq("BadAttr"))).thenThrow(
			new AttributeNotFoundException("BadAttr")
		);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("BadAttr"),
			List.of(),
			requestStats,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("", result.get(0).get(0));
		// The server answered: a missing attribute is tolerated
		assertCounts(1, 0, 0);
	}

	@Test
	void testFetchMBeanReturnsEmptyOnConnectionFailure() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("badhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Connection refused")).when(jmxRequestExecutor).connect(any(), any(), any(), any());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertNotNull(result);
		assertTrue(result.isEmpty());
		assertCounts(0, 0, 1);
	}

	@Test
	void testFetchMBeanWithNullStats() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(eq(objectName), eq("Uptime"))).thenReturn(12345L);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of("Uptime"),
			List.of(),
			null,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("12345", result.get(0).get(0));

		// A failure without stats records nothing either, and does not throw
		doThrow(new IOException("Connection refused")).when(jmxRequestExecutor).connect(any(), any(), any(), any());
		assertTrue(
			jmxRequestExecutor.fetchMBean(config, "java.lang:type=Runtime", List.of("Uptime"), List.of(), null, null)
				.isEmpty()
		);
		assertFalse(jmxRequestExecutor.checkConnection(config, null));
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testFetchMBeanAuthenticationFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new SecurityException("Authentication failed! Invalid username or password")).when(jmxRequestExecutor)
			.connect(any(), any(), any(), any());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertTrue(result.isEmpty());
		assertCounts(0, 0, 1);
	}

	@Test
	void testFetchMBeanWrappedSocketTimeout() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		// RMIConnector wraps the JNDI/RMI failure in an IOException
		doThrow(new IOException("Failed to retrieve RMIServer stub", new SocketTimeoutException("Read timed out"))).when(
			jmxRequestExecutor
		).connect(any(), any(), any(), any());

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertTrue(result.isEmpty());
		assertCounts(0, 1, 0);
	}

	@Test
	void testFetchMBeanQueryNamesFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenThrow(
			new IOException("Connection reset")
		);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertTrue(result.isEmpty());
		assertCounts(0, 0, 1);
	}

	@Test
	void testFetchMBeanAttributeConnectionLost() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(objectName, "Lost")).thenThrow(new IOException("Connection lost"));
		when(mBeanServerConnectionMock.getAttribute(objectName, "Kept")).thenReturn(5);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("Lost", "Kept"),
			List.of(),
			requestStats,
			null
		);

		// Same row as before: the lost attribute is empty, the next one is still read
		assertEquals(List.of(List.of("", "5")), result);
		assertCounts(0, 0, 1);
	}

	@Test
	void testFetchMBeanCloseFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(objectName, "HeapMemoryUsage")).thenReturn("1024");
		doThrow(new IOException("Close failed")).when(jmxConnectorMock).close();

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Memory",
			List.of("HeapMemoryUsage"),
			List.of(),
			requestStats,
			null
		);

		assertEquals(List.of(List.of("1024")), result);
		assertCounts(0, 0, 1);
	}

	@Test
	void testFetchMBeanMalformedPatternSendsNothing() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		assertTrue(
			jmxRequestExecutor.fetchMBean(config, "no domain separator", List.of("A"), List.of(), requestStats, null)
				.isEmpty()
		);
		assertTrue(jmxRequestExecutor.fetchMBean(config, null, List.of("A"), List.of(), requestStats, null).isEmpty());

		verify(jmxRequestExecutor, never()).connect(any(), any(), any(), any());
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testInvalidServiceUrlSendsNothing() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new MalformedURLException("Service URL contains non-ASCII character")).when(jmxRequestExecutor)
			.connect(any(), any(), any(), any());

		assertTrue(
			jmxRequestExecutor.fetchMBean(config, "java.lang:type=Memory", List.of("A"), List.of(), requestStats, null)
				.isEmpty()
		);
		assertFalse(jmxRequestExecutor.checkConnection(config, requestStats));
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testFetchMBeanDeadlineRecordsOneTimeoutDespiteLateCompletion() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(1L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(objectName, "HeapMemoryUsage")).thenReturn("1024");
		final AtomicReference<Thread> worker = new AtomicReference<>();
		final CountDownLatch release = blockConnect(worker);

		assertThrows(
			TimeoutException.class,
			() -> jmxRequestExecutor.fetchMBean(
				config,
				"java.lang:type=Memory",
				List.of("HeapMemoryUsage"),
				List.of(),
				requestStats,
				null
			)
		);

		// The worker completes the request after the deadline
		release.countDown();
		worker.get().join(TimeUnit.SECONDS.toMillis(10));
		assertFalse(worker.get().isAlive());
		verify(mBeanServerConnectionMock).getAttribute(objectName, "HeapMemoryUsage");

		assertCounts(0, 1, 0);
	}

	@Test
	void testFetchMBeanInterruptedBeforeTheRequestCountsNothing() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final AtomicReference<Thread> worker = new AtomicReference<>();
		final CountDownLatch release = blockConnect(worker);

		// An earlier deadline of the job interrupted the thread
		Thread.currentThread().interrupt();
		try {
			assertThrows(
				InterruptedException.class,
				() -> jmxRequestExecutor.fetchMBean(
					config,
					"java.lang:type=Memory",
					List.of("A"),
					List.of(),
					requestStats,
					null
				)
			);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
			release.countDown();
		}

		// Nothing was sent: the request never reached connect
		verify(jmxRequestExecutor, never()).connect(any(), any(), any(), any());
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testFetchMBeanInterruptedDuringTheRequestIsTimeout() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final AtomicReference<Thread> worker = new AtomicReference<>();
		final CountDownLatch release = blockConnect(worker);

		// The deadline of the job expires while the worker connects
		final Thread caller = Thread.currentThread();
		final Thread deadline = new Thread(() -> {
			while (worker.get() == null) {
				Thread.onSpinWait();
			}
			caller.interrupt();
		});
		deadline.start();
		try {
			assertThrows(
				InterruptedException.class,
				() -> jmxRequestExecutor.fetchMBean(
					config,
					"java.lang:type=Memory",
					List.of("A"),
					List.of(),
					requestStats,
					null
				)
			);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			// Clear the interrupt flag first: join would throw on it
			Thread.interrupted();
			deadline.join(TimeUnit.SECONDS.toMillis(10));
			release.countDown();
		}

		assertCounts(0, 1, 0);
	}

	@Test
	void testFetchMBeanWorkerCrashIsFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new LinkageError("Crash")).when(jmxRequestExecutor).connect(any(), any(), any(), any());

		assertThrows(
			ExecutionException.class,
			() -> jmxRequestExecutor.fetchMBean(config, "java.lang:type=Memory", List.of("A"), List.of(), requestStats, null)
		);
		assertCounts(0, 0, 1);
	}

	@Test
	void testProcessorsRecordInTheResourceStats() throws Exception {
		// The JMX hostname overrides the resource hostname
		final JmxConfiguration config = JmxConfiguration.builder().hostname("jmx-host").port(1099).timeout(30L).build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname("resource-host")
					.configurations(Map.of(JmxConfiguration.class, config))
					.build()
			)
			.build();

		final ObjectName objectName = new ObjectName("java.lang:type=Memory");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));
		when(mBeanServerConnectionMock.getAttribute(objectName, "HeapMemoryUsage")).thenReturn("1024");

		final JmxSource source = new JmxSource();
		source.setObjectName("java.lang:type=Memory");
		source.setAttributes(List.of("HeapMemoryUsage"));
		new JmxSourceProcessor(jmxRequestExecutor).process(source, telemetryManager);

		final JmxCriterion criterion = new JmxCriterion();
		criterion.setObjectName("java.lang:type=Memory");
		criterion.setAttributes(List.of("HeapMemoryUsage"));
		new JmxCriterionProcessor(jmxRequestExecutor, true).process(criterion, "connector", telemetryManager);

		verify(jmxRequestExecutor, times(2)).connect(eq("jmx-host"), any(), any(), any());
		assertCounts(telemetryManager.getRequestStats(), 2, 0, 0);
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testFetchMBeanMultipleMatches() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName1 = new ObjectName("java.lang:type=GarbageCollector,name=G1YoungGen");
		final ObjectName objectName2 = new ObjectName("java.lang:type=GarbageCollector,name=G1OldGen");
		final Set<ObjectName> matches = new HashSet<>();
		matches.add(objectName1);
		matches.add(objectName2);

		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(matches);
		when(mBeanServerConnectionMock.getAttribute(eq(objectName1), eq("CollectionCount"))).thenReturn(10);
		when(mBeanServerConnectionMock.getAttribute(eq(objectName2), eq("CollectionCount"))).thenReturn(20);

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=GarbageCollector,name=*",
			List.of("CollectionCount"),
			List.of("name"),
			null,
			null
		);

		assertNotNull(result);
		assertEquals(2, result.size());
	}

	@Test
	void testCheckConnectionReturnsTrue() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final boolean result = jmxRequestExecutor.checkConnection(config, requestStats);
		assertTrue(result);
		assertCounts(1, 0, 0);
	}

	@Test
	void testCheckConnectionReturnsFalseOnFailure() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("badhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Connection refused")).when(jmxRequestExecutor).connect(any(), any(), any(), any());

		final boolean result = jmxRequestExecutor.checkConnection(config, requestStats);
		assertFalse(result);
		assertCounts(0, 0, 1);
	}

	@Test
	void testCheckConnectionAuthenticationFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new SecurityException("Authentication failed")).when(jmxRequestExecutor)
			.connect(any(), any(), any(), any());

		assertFalse(jmxRequestExecutor.checkConnection(config, requestStats));
		assertCounts(0, 0, 1);
	}

	@Test
	void testCheckConnectionWrappedSocketTimeout() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Failed to retrieve RMIServer stub", new SocketTimeoutException("connect timed out"))).when(
			jmxRequestExecutor
		).connect(any(), any(), any(), any());

		assertFalse(jmxRequestExecutor.checkConnection(config, requestStats));
		assertCounts(0, 1, 0);
	}

	@Test
	void testCheckConnectionCloseFailed() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		doThrow(new IOException("Close failed")).when(jmxConnectorMock).close();

		assertFalse(jmxRequestExecutor.checkConnection(config, requestStats));
		assertCounts(0, 0, 1);
	}

	@Test
	void testCheckConnectionDeadlineRecordsOneTimeout() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(1L).build();

		final AtomicReference<Thread> worker = new AtomicReference<>();
		final CountDownLatch release = blockConnect(worker);

		assertThrows(TimeoutException.class, () -> jmxRequestExecutor.checkConnection(config, requestStats));

		release.countDown();
		worker.get().join(TimeUnit.SECONDS.toMillis(10));
		assertFalse(worker.get().isAlive());
		verify(jmxConnectorMock).close();

		assertCounts(0, 1, 0);
	}

	@Test
	void testCheckConnectionWithNullStats() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final boolean result = jmxRequestExecutor.checkConnection(config, null);
		assertTrue(result);
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testCheckConnectionWithCredentials() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder()
			.hostname("testhost")
			.port(9999)
			.username("admin")
			.password("secret".toCharArray())
			.timeout(30L)
			.build();

		final boolean result = jmxRequestExecutor.checkConnection(config, null);
		assertTrue(result);
	}

	@Test
	void testFetchMBeanWithNoAttributes() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("testhost").port(1099).timeout(30L).build();

		final ObjectName objectName = new ObjectName("java.lang:type=Runtime");
		when(mBeanServerConnectionMock.queryNames(any(ObjectName.class), eq(null))).thenReturn(Set.of(objectName));

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			config,
			"java.lang:type=Runtime",
			List.of(),
			List.of("type"),
			null,
			null
		);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(1, result.get(0).size());
		assertEquals("Runtime", result.get(0).get(0));
	}

	@Test
	void testBuildJmxRmiUrl() {
		final String url = jmxRequestExecutor.buildJmxRmiUrl("myhost", 1099);
		assertEquals("service:jmx:rmi:///jndi/rmi://myhost:1099/jmxrmi", url);
	}

	@Test
	void testBuildJmxRmiUrlCustomPort() {
		final String url = jmxRequestExecutor.buildJmxRmiUrl("192.168.1.1", 9999);
		assertEquals("service:jmx:rmi:///jndi/rmi://192.168.1.1:9999/jmxrmi", url);
	}
}
