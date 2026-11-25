package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.source.SnmpGetSourceProcessor;
import org.metricshub.snmp.client.ISnmpClient;
import uk.co.westhawk.snmp.stack.AgentException;
import uk.co.westhawk.snmp.stack.DecodingException;
import uk.co.westhawk.snmp.stack.EncodingException;
import uk.co.westhawk.snmp.stack.PduException;

class AbstractSnmpRequestExecutorTest {

	private static final String OID = "1.3.6.1.2.1.1.1.0";
	private static final String HOSTNAME = "snmp-host";

	private ISnmpClient client;
	private ISnmpConfiguration configuration;
	private RequestStats requestStats;

	/**
	 * Creates the SNMP client of a test executor, possibly failing.
	 */
	@FunctionalInterface
	private interface ClientFactory {
		ISnmpClient create() throws IOException;
	}

	/**
	 * An executor whose SNMP client comes from a {@link ClientFactory}, and which remembers the hostname it was asked
	 * to connect to.
	 */
	private static class TestSnmpRequestExecutor extends AbstractSnmpRequestExecutor {

		private final ClientFactory clientFactory;
		private volatile String requestedHostname;

		TestSnmpRequestExecutor(final ClientFactory clientFactory) {
			this.clientFactory = clientFactory;
		}

		@Override
		protected ISnmpClient createSnmpClient(final ISnmpConfiguration configuration, final String hostname)
			throws IOException {
			requestedHostname = hostname;
			return clientFactory.create();
		}
	}

	@BeforeEach
	void setUp() {
		client = mock(ISnmpClient.class);
		configuration = mock(ISnmpConfiguration.class);
		when(configuration.getTimeout()).thenReturn(1L);
		requestStats = new RequestStats();
	}

	/**
	 * Asserts the SNMP counters of the given stats.
	 *
	 * @param stats     The request counters.
	 * @param completed Expected completed requests.
	 * @param timeout   Expected timed out requests.
	 * @param failed    Expected failed requests.
	 */
	private static void assertCounts(
		final RequestStats stats,
		final long completed,
		final long timeout,
		final long failed
	) {
		assertEquals(
			Map.of(
				AbstractSnmpRequestExecutor.OPERATION_TYPE,
				Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
			),
			stats.snapshot()
		);
	}

	/**
	 * Sleeps without giving up on interruption, like Westhawk's {@code Pdu.waitForSelf}, then restores the interrupt
	 * flag.
	 *
	 * @param millis The duration of the sleep.
	 */
	private static void sleepUninterruptibly(final long millis) {
		final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		boolean interrupted = false;
		long remaining;
		while ((remaining = end - System.nanoTime()) > 0) {
			try {
				TimeUnit.NANOSECONDS.sleep(remaining);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@Test
	void testCompletedRequests() throws Exception {
		when(client.get(OID)).thenReturn("value");
		when(client.getNext(OID)).thenReturn("");
		when(client.walk(OID)).thenReturn("walk");
		when(client.table(OID, new String[] { "1" })).thenReturn(List.of(List.of("a")));
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		assertEquals("value", executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats));
		// A valid empty value is an answer
		assertEquals("", executor.executeSNMPGetNext(OID, configuration, HOSTNAME, true, requestStats));
		// A walk and a table are one attempt each
		assertEquals("walk", executor.executeSNMPWalk(OID, configuration, HOSTNAME, true, requestStats));
		assertEquals(
			List.of(List.of("a")),
			executor.executeSNMPTable(OID, new String[] { "1" }, configuration, HOSTNAME, true, requestStats)
		);

		assertCounts(requestStats, 4, 0, 0);
		verify(client, times(4)).freeResources();
	}

	/**
	 * @return The exceptions of an SNMP operation with the outcome they stand for.
	 */
	static Stream<Arguments> operationErrors() {
		return Stream.of(
			// The agent answered that there is no value
			Arguments.of(new Exception("SNMP_VAR_NOSUCHOBJECT"), RequestOutcome.COMPLETED),
			Arguments.of(new Exception("SNMP_VAR_NOSUCHINSTANCE"), RequestOutcome.COMPLETED),
			Arguments.of(new Exception("SNMP_VAR_ENDOFMIBVIEW"), RequestOutcome.COMPLETED),
			Arguments.of(new AgentException("No such name error"), RequestOutcome.COMPLETED),
			// No answer from the agent, for a PDU or for the SNMPv3 discovery
			Arguments.of(new AgentException("Timed out"), RequestOutcome.TIMEOUT),
			Arguments.of(new PduException("Engine ID discovery: Timed out"), RequestOutcome.TIMEOUT),
			Arguments.of(new PduException("Timeline discovery: Timed out"), RequestOutcome.TIMEOUT),
			Arguments.of(new IOException(new TimeoutException()), RequestOutcome.TIMEOUT),
			// Errors
			Arguments.of(new AgentException("General error"), RequestOutcome.FAILED),
			Arguments.of(new AgentException("Authorization error"), RequestOutcome.FAILED),
			Arguments.of(new DecodingException("Outside time window"), RequestOutcome.FAILED),
			Arguments.of(new PduException("Timeline discovery: Authentication failure"), RequestOutcome.FAILED),
			Arguments.of(new EncodingException("Context can no longer be used"), RequestOutcome.FAILED),
			Arguments.of(new NullPointerException(), RequestOutcome.FAILED),
			// Same message as a no-such value, but not the plain Exception of SnmpClient
			Arguments.of(new IOException("SNMP_VAR_NOSUCHOBJECT"), RequestOutcome.FAILED)
		);
	}

	@ParameterizedTest
	@MethodSource("operationErrors")
	void testOperationErrorIsClassifiedAndSwallowed(final Exception error, final RequestOutcome expected)
		throws Exception {
		when(client.get(OID)).thenThrow(error);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		// The error is still turned into null, as before
		assertNull(executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats));

		assertCounts(
			requestStats,
			expected == RequestOutcome.COMPLETED ? 1 : 0,
			expected == RequestOutcome.TIMEOUT ? 1 : 0,
			expected == RequestOutcome.FAILED ? 1 : 0
		);
		verify(client).freeResources();
	}

	@Test
	void testOperationErrorPropagatesAsBefore() throws Exception {
		final NoClassDefFoundError error = new NoClassDefFoundError("missing");
		when(client.get(OID)).thenThrow(error);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		final ExecutionException thrown = assertThrows(
			ExecutionException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);

		assertSame(error, thrown.getCause());
		assertCounts(requestStats, 0, 0, 1);
		verify(client).freeResources();
	}

	@Test
	void testUnknownHostAtClientCreationFails() {
		final UnknownHostException error = new UnknownHostException("unknown");
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> {
			throw error;
		});

		final ExecutionException thrown = assertThrows(
			ExecutionException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);

		assertSame(error, thrown.getCause());
		assertCounts(requestStats, 0, 0, 1);
	}

	@Test
	void testInvalidParametersAtClientCreationRecordNothing() {
		final IllegalArgumentException error = new IllegalArgumentException("Invalid authentication method");
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> {
			throw error;
		});

		final ExecutionException thrown = assertThrows(
			ExecutionException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);

		assertSame(error, thrown.getCause());
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testDeadlineThenLateAnswerCountsOneTimeout() throws Exception {
		final CountDownLatch finished = new CountDownLatch(1);
		doAnswer(invocation -> {
			try {
				sleepUninterruptibly(2500);
				return "late";
			} finally {
				finished.countDown();
			}
		}).when(client).get(OID);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		assertThrows(
			TimeoutException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);
		assertTrue(finished.await(10, TimeUnit.SECONDS));

		assertCounts(requestStats, 0, 1, 0);
		verify(client, times(1)).freeResources();
	}

	@Test
	void testDeadlineThenLateErrorCountsOneTimeout() throws Exception {
		final CountDownLatch finished = new CountDownLatch(1);
		doAnswer(invocation -> {
			try {
				sleepUninterruptibly(2500);
				throw new AgentException("General error");
			} finally {
				finished.countDown();
			}
		}).when(client).get(OID);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		assertThrows(
			TimeoutException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);
		assertTrue(finished.await(10, TimeUnit.SECONDS));

		assertCounts(requestStats, 0, 1, 0);
		verify(client, times(1)).freeResources();
	}

	@Test
	void testDeadlineDuringClientCreationFreesTheLateClient() throws Exception {
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> {
			sleepUninterruptibly(2500);
			return client;
		});

		assertThrows(
			TimeoutException.class,
			() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
		);

		// The worker frees the client the caller no longer waits for, and sends nothing
		verify(client, timeout(10000).times(1)).freeResources();
		verify(client, never()).get(any());
		assertCounts(requestStats, 0, 1, 0);
	}

	@Test
	void testCallerInterruptedBeforeTheRequestCountsNothing() throws Exception {
		// The request never answers: only the interruption can end the call
		final CountDownLatch never = new CountDownLatch(1);
		doAnswer(invocation -> {
			never.await();
			return "value";
		}).when(client).get(OID);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		// An earlier deadline of the job interrupted the thread
		Thread.currentThread().interrupt();
		try {
			assertThrows(
				InterruptedException.class,
				() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
			);
		} finally {
			// Clear the interrupt flag restored by the executor
			Thread.interrupted();
		}

		// Nothing was sent
		verify(client, never()).get(any());
		assertTrue(requestStats.snapshot().isEmpty());
	}

	@Test
	void testCallerInterruptedDuringTheRequestCountsTimeout() throws Exception {
		final CountDownLatch sent = new CountDownLatch(1);
		final CountDownLatch never = new CountDownLatch(1);
		doAnswer(invocation -> {
			sent.countDown();
			never.await();
			return "value";
		}).when(client).get(OID);
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);
		when(configuration.getTimeout()).thenReturn(30L);

		// The deadline of the job expires while the request waits for its answer
		final Thread caller = Thread.currentThread();
		final Thread deadline = new Thread(() -> {
			try {
				sent.await();
				caller.interrupt();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		deadline.start();
		try {
			assertThrows(
				InterruptedException.class,
				() -> executor.executeSNMPGet(OID, configuration, HOSTNAME, true, requestStats)
			);
		} finally {
			// Clear the interrupt flag restored by the executor first: join would throw on it
			Thread.interrupted();
			deadline.join();
		}

		assertCounts(requestStats, 0, 1, 0);
		verify(client, timeout(10000).times(1)).freeResources();
	}

	@Test
	void testNullStatsRecordNothing() throws Exception {
		when(client.get(OID)).thenReturn("value");
		when(client.getNext(OID)).thenThrow(new AgentException("Timed out"));
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		assertEquals("value", executor.executeSNMPGet(OID, configuration, HOSTNAME, false, null));
		assertNull(executor.executeSNMPGetNext(OID, configuration, HOSTNAME, false, null));

		final TestSnmpRequestExecutor failingExecutor = new TestSnmpRequestExecutor(() -> {
			throw new UnknownHostException("unknown");
		});
		assertThrows(
			ExecutionException.class,
			() -> failingExecutor.executeSNMPGet(OID, configuration, HOSTNAME, false, null)
		);
	}

	@Test
	void testCountsLandInTheResourceOfAnOverriddenHostname() throws Exception {
		when(client.get(OID)).thenReturn("value");
		when(configuration.getHostname()).thenReturn("protocol-host");
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname("resource-host")
					.configurations(Map.of(configuration.getClass(), configuration))
					.build()
			)
			.build();
		final TestSnmpRequestExecutor executor = new TestSnmpRequestExecutor(() -> client);

		final SourceTable result = new SnmpGetSourceProcessor(executor, manager -> configuration).process(
			SnmpGetSource.builder().oid(OID).build(),
			"connector",
			telemetryManager
		);

		assertEquals(SourceTable.ofText("value"), result);
		assertEquals("protocol-host", executor.requestedHostname);
		assertCounts(telemetryManager.getRequestStats(), 1, 0, 0);
	}
}
