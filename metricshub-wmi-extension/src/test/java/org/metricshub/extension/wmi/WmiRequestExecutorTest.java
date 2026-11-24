package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.NetworkHelper;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.wmi.exceptions.WmiComException;
import org.metricshub.wmi.exceptions.WqlQuerySyntaxException;
import org.metricshub.wmi.remotecommand.WinRemoteCommandExecutor;
import org.metricshub.wmi.wbem.WmiWbemServices;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

class WmiRequestExecutorTest {

	private static final String HOSTNAME = "localhost";
	private static final String QUERY = "SELECT Name FROM Win32_ComputerSystem";
	private static final String NAMESPACE = "root\\cimv2";
	private static final String COMMAND = "ipconfig";
	private static final WmiConfiguration CONFIGURATION = WmiConfiguration.builder().timeout(30L).build();

	@Test
	void testIsAcceptableException() {
		final WmiRequestExecutor requestExecutor = new WmiRequestExecutor();
		assertFalse(requestExecutor.isAcceptableException(null));
		assertFalse(requestExecutor.isAcceptableException(new Exception()));
		assertFalse(requestExecutor.isAcceptableException(new Exception(new Exception())));

		assertFalse(requestExecutor.isAcceptableException(new WmiComException("other")));
		assertFalse(requestExecutor.isAcceptableException(new WmiComException(new Exception())));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_CLASS)));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_NAMESPACE)));
		assertTrue(requestExecutor.isAcceptableException(new WmiComException(WmiRequestExecutor.WBEM_E_NOT_FOUND)));

		assertTrue(
			requestExecutor.isAcceptableException(
				new Exception(new WmiComException(WmiRequestExecutor.WBEM_E_INVALID_NAMESPACE))
			)
		);
	}

	/**
	 * The counters of the "wmi" label, as completed, timeout and failed counts.
	 */
	private static List<Long> wmiCounts(final RequestStats requestStats) {
		final Map<RequestOutcome, Long> counts = requestStats.snapshot().get(WmiExtension.IDENTIFIER);
		return counts == null
			? List.of(0L, 0L, 0L)
			: List.of(
				counts.get(RequestOutcome.COMPLETED),
				counts.get(RequestOutcome.TIMEOUT),
				counts.get(RequestOutcome.FAILED)
			);
	}

	/**
	 * An executor whose connection and query return the given rows. The query runs on a thread of its own, out of
	 * reach of a static mock: it is stubbed on a spy.
	 */
	private static WmiRequestExecutor executorReturning(final List<Map<String, Object>> rows) throws Exception {
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doReturn(rows).when(executor).query(any(), any(), any(), any(), anyLong());
		return executor;
	}

	/**
	 * An executor whose connection or query throws the given exception.
	 */
	private static WmiRequestExecutor executorThrowing(final Throwable exception) throws Exception {
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doThrow(exception).when(executor).query(any(), any(), any(), any(), anyLong());
		return executor;
	}

	/**
	 * Run a WQL query with the given executor, and return the request statistics it was counted in.
	 */
	private static RequestStats query(final WmiRequestExecutor executor) {
		final RequestStats requestStats = new RequestStats();
		try {
			executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats);
		} catch (ClientException e) {
			// Counted all the same
		}
		return requestStats;
	}

	/**
	 * Run a WQL query whose connection or query throws the given exception, and return the request statistics it was
	 * counted in.
	 */
	private static RequestStats queryFailing(final Exception exception) throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = executorThrowing(exception);
		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
		);
		assertSame(exception, e.getCause());
		return requestStats;
	}

	@Test
	void testQueryConnectsQueriesAndReleasesTheServices() throws Exception {
		final WmiWbemServices services = mock(WmiWbemServices.class);
		when(services.executeWql(QUERY, 30000L)).thenReturn(List.of(Map.of("Name", "host")));
		final char[] password = "pass".toCharArray();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			statics.when(() -> WmiWbemServices.getInstance("\\\\host\\root\\cimv2", "user", password)).thenReturn(services);
			assertEquals(
				List.of(Map.of("Name", "host")),
				new WmiRequestExecutor().query("\\\\host\\root\\cimv2", "user", password, QUERY, 30000L)
			);
		}
		verify(services).close();
	}

	@Test
	void testExecuteWmiCountsCompleted() throws Exception {
		final RequestStats requestStats = new RequestStats();
		assertEquals(
			List.of(List.of("host")),
			executorReturning(List.of(Map.of("Name", "host"))).executeWmi(
				HOSTNAME,
				CONFIGURATION,
				QUERY,
				NAMESPACE,
				null,
				requestStats
			)
		);
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiCountsAnEmptyResultAsCompleted() throws Exception {
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(query(executorReturning(List.of()))));
	}

	@Test
	void testExecuteWmiCountsOnceWhenProcessingTheResultFails() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = executorReturning(List.of(Map.of("Name", "host")));
		doThrow(new IllegalStateException("conversion")).when(executor).buildWmiTable(any(), any());
		assertThrows(
			ClientException.class,
			() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
		);
		// The query was answered: the local conversion failure is not a second outcome
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiCountsTimeout() throws Exception {
		// wmi-java reports WBEM_S_TIMEDOUT as a TimeoutException
		assertEquals(List.of(0L, 1L, 0L), wmiCounts(queryFailing(new TimeoutException("No results after 30000 ms."))));
	}

	@Test
	void testExecuteWmiCountsTimeoutHresults() throws Exception {
		for (final int hResult : new int[] { 0x80041069, 0x80041088, 0x8001011F, 0x800705B4 }) {
			final WmiComException exception = new WmiComException(
				new COMException("timed out", new HRESULT(hResult)),
				"timed out"
			);
			assertEquals(List.of(0L, 1L, 0L), wmiCounts(queryFailing(exception)), Integer.toHexString(hResult));
		}
	}

	@Test
	void testExecuteWmiCountsFailures() throws Exception {
		// Connection refused by the host: a COM error that is not a timeout
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(
				queryFailing(
					new WmiComException(new COMException("RPC server unavailable", new HRESULT(0x800706BA)), "unavailable")
				)
			)
		);
		// An error the health check and the namespace detection accept
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(queryFailing(new WmiComException("WBEM_E_INVALID_NAMESPACE (0x8004100E)")))
		);
		// A timeout mnemonic in a message only: wmi-java gives no HRESULT to classify
		assertEquals(List.of(0L, 0L, 1L), wmiCounts(queryFailing(new WmiComException("WBEM_E_TIMED_OUT (0x80041069)"))));
		// Parsed after the connection was made
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(queryFailing(new WqlQuerySyntaxException("Syntax error in WQL Query")))
		);
	}

	@Test
	void testExecuteWmiCountsNothingOnLocalArgumentChecks() throws Exception {
		assertEquals(
			Map.of(),
			queryFailing(new IllegalArgumentException("A local resource must be accessed without specific credentials"))
				.snapshot()
		);
	}

	@Test
	void testExecuteWmiOnAnInterruptedThreadCountsNothing() {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			// An earlier deadline of the job interrupted the thread: nothing connects
			Thread.currentThread().interrupt();
			try {
				final ClientException e = assertThrows(
					ClientException.class,
					() -> new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
				);
				assertInstanceOf(InterruptedException.class, e.getCause());
				assertTrue(Thread.currentThread().isInterrupted());
			} finally {
				Thread.interrupted();
			}
			statics.verify(() -> WmiWbemServices.getInstance(any(), any(), any()), never());
		}
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testExecuteWmiWithoutRequestStats() throws Exception {
		assertEquals(
			List.of(List.of("host")),
			executorReturning(List.of(Map.of("Name", "host"))).executeWmi(
				HOSTNAME,
				CONFIGURATION,
				QUERY,
				NAMESPACE,
				null,
				null
			)
		);

		final WmiRequestExecutor failing = executorThrowing(new WmiComException("down"));
		assertThrows(
			ClientException.class,
			() -> failing.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null)
		);
	}

	@Test
	void testExecuteWmiAcceptableErrorIsStillFoundFromTheRequestThread() throws Exception {
		final WmiComException exception = new WmiComException("WBEM_E_INVALID_NAMESPACE (0x8004100E)");
		final WmiRequestExecutor executor = executorThrowing(exception);
		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null)
		);
		assertSame(exception, e.getCause());
		assertTrue(executor.isAcceptableException(e));
	}

	@Test
	void testInterruptBeforeTheQueryStartedCountsNothing() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = executorReturning(List.of(Map.of("Name", "host")));
		try (MockedStatic<NetworkHelper> network = mockStatic(NetworkHelper.class)) {
			// The interrupt lands after the check of executeWmi, while the name of the host is resolved
			network.when(() -> NetworkHelper.isLocalhost(HOSTNAME)).thenAnswer(invocation -> {
				Thread.currentThread().interrupt();
				return true;
			});
			try {
				assertThrows(
					ClientException.class,
					() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
				);
			} finally {
				Thread.interrupted();
			}
		}
		// Nothing was sent: nothing is counted
		verify(executor, never()).query(any(), any(), any(), any(), anyLong());
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testExecuteWmiRethrowsAnError() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final LinkageError error = new LinkageError("native library");
		final WmiRequestExecutor executor = executorThrowing(error);
		assertSame(
			error,
			assertThrows(
				LinkageError.class,
				() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
			)
		);
		assertEquals(Map.of(), requestStats.snapshot());
	}

	/**
	 * Answers like a COM call that ignores interrupts: interrupts the given caller, as the deadline of its job would,
	 * then blocks until released, for 3 seconds at most, and returns the given result.
	 */
	private static Answer<Object> interruptingAndBlocking(
		final Thread caller,
		final CountDownLatch release,
		final Object result
	) {
		return invocation -> {
			caller.interrupt();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (release.getCount() > 0 && System.nanoTime() < deadline) {
				try {
					release.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					// Ignored, like the native call
				}
			}
			return result;
		};
	}

	/**
	 * Run the given request on the current thread, which the request interrupts, and check that it gives up at once
	 * with a {@link ClientException} caused by the interrupt, keeping the interrupt status.
	 */
	private static void assertGivesUpAtOnce(final Executable request) {
		final long start = System.nanoTime();
		try {
			final ClientException e = assertThrows(ClientException.class, request);
			final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			assertTrue(elapsedMillis < 1000, "Returned after " + elapsedMillis + " ms");
			assertTrue(Thread.currentThread().isInterrupted());
			assertInstanceOf(InterruptedException.class, e.getCause());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testInterruptedQueryReturnsAtOnceAndCountsOneTimeout() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final CountDownLatch release = new CountDownLatch(1);
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doAnswer(interruptingAndBlocking(Thread.currentThread(), release, List.of(Map.of("Name", "host")))).when(executor)
			.query(any(), any(), any(), any(), anyLong());
		try {
			assertGivesUpAtOnce(() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats));
		} finally {
			release.countDown();
		}
		assertEquals(List.of(0L, 1L, 0L), wmiCounts(requestStats));
		// The result of the query is never processed (nor recorded)
		verify(executor, never()).buildWmiTable(any(), any());
	}

	@Test
	void testInterruptedCallerSendsNoQuery() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = executorReturning(List.of(Map.of("Name", "host")));
		Thread.currentThread().interrupt();
		try {
			final ClientException e = assertThrows(
				ClientException.class,
				() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
			);
			assertTrue(Thread.currentThread().isInterrupted());
			assertInstanceOf(InterruptedException.class, e.getCause());
		} finally {
			Thread.interrupted();
		}
		verify(executor, never()).query(any(), any(), any(), any(), anyLong());
		assertEquals(Map.of(), requestStats.snapshot());
	}

	/**
	 * An executor whose remote command throws the given exception.
	 */
	private static WmiRequestExecutor commandExecutorThrowing(final Exception exception) throws Exception {
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doThrow(exception).when(executor).executeRemoteCommand(any(), any(), any(), any(), anyLong(), any());
		return executor;
	}

	/**
	 * Run a remote command whose execution throws the given exception, and return the thrown exception.
	 */
	private static ClientException runCommandFailing(final Exception exception, final RequestStats requestStats)
		throws Exception {
		final WmiRequestExecutor executor = commandExecutorThrowing(exception);
		return assertThrows(
			ClientException.class,
			() -> executor.executeWmiRemoteCommand(COMMAND, HOSTNAME, null, null, 30, null, requestStats)
		);
	}

	@Test
	void testExecuteRemoteCommandRunsWmiJava() throws Exception {
		final WinRemoteCommandExecutor result = mock(WinRemoteCommandExecutor.class);
		try (MockedStatic<WinRemoteCommandExecutor> statics = mockStatic(WinRemoteCommandExecutor.class)) {
			statics.when(
				() -> WinRemoteCommandExecutor.execute(
					eq(COMMAND),
					eq(HOSTNAME),
					any(),
					any(),
					any(),
					eq(30000L),
					any(),
					eq(true)
				)
			).thenReturn(result);
			assertSame(result, new WmiRequestExecutor().executeRemoteCommand(COMMAND, HOSTNAME, null, null, 30000L, null));
		}
	}

	@Test
	void testExecuteWmiRemoteCommandCountsCompleted() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WinRemoteCommandExecutor result = mock(WinRemoteCommandExecutor.class);
		when(result.getStdout()).thenReturn("output");
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doReturn(result).when(executor).executeRemoteCommand(any(), any(), any(), any(), anyLong(), any());
		assertEquals("output", executor.executeWinRemoteCommand(HOSTNAME, CONFIGURATION, COMMAND, null, requestStats));
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiRemoteCommandCountsTimeout() throws Exception {
		// The overall deadline of wmi-java is a TimeoutException without a cause
		final RequestStats requestStats = new RequestStats();
		final TimeoutException timeout = new TimeoutException("No time left to execute command");
		final ClientException e = runCommandFailing(timeout, requestStats);

		// The timeout is no longer lost: it is the cause of the ClientException
		assertSame(timeout, e.getCause());
		assertEquals(timeout.toString(), e.getMessage());
		assertEquals(List.of(0L, 1L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiRemoteCommandCountsFailure() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final IOException ioException = new IOException("copy failed", new IOException("access denied"));
		final ClientException e = runCommandFailing(ioException, requestStats);

		// Same exception as before: the cause of the wmi-java exception
		assertEquals(ioException.getCause().toString(), e.getMessage());
		assertSame(ioException.getCause(), e.getCause());
		assertEquals(List.of(0L, 0L, 1L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiRemoteCommandCountsNothingOnLocalArgumentChecks() throws Exception {
		final RequestStats requestStats = new RequestStats();
		runCommandFailing(new IllegalArgumentException("timeout=0 must not be negative or zero."), requestStats);
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testExecuteWmiRemoteCommandOnAnInterruptedThreadCountsNothing() {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WinRemoteCommandExecutor> statics = mockStatic(WinRemoteCommandExecutor.class)) {
			// An earlier deadline of the job interrupted the thread: nothing runs
			Thread.currentThread().interrupt();
			try {
				final ClientException e = assertThrows(
					ClientException.class,
					() -> new WmiRequestExecutor().executeWmiRemoteCommand(COMMAND, HOSTNAME, null, null, 30, null, requestStats)
				);
				assertInstanceOf(InterruptedException.class, e.getCause());
				assertTrue(Thread.currentThread().isInterrupted());
			} finally {
				Thread.interrupted();
			}
			statics.verify(
				() -> WinRemoteCommandExecutor.execute(any(), any(), any(), any(), any(), anyLong(), any(), anyBoolean()),
				never()
			);
		}
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testExecuteWmiRemoteCommandWithoutRequestStats() {
		assertDoesNotThrow(() -> runCommandFailing(new TimeoutException("No time left"), null));
	}

	@Test
	void testInterruptedRemoteCommandReturnsAtOnceAndCountsOneTimeout() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final CountDownLatch release = new CountDownLatch(1);
		final WinRemoteCommandExecutor result = mock(WinRemoteCommandExecutor.class);
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doAnswer(interruptingAndBlocking(Thread.currentThread(), release, result)).when(executor)
			.executeRemoteCommand(any(), any(), any(), any(), anyLong(), any());
		try {
			assertGivesUpAtOnce(
				() -> executor.executeWmiRemoteCommand(COMMAND, HOSTNAME, null, null, 30, null, requestStats)
			);
		} finally {
			release.countDown();
		}
		assertEquals(List.of(0L, 1L, 0L), wmiCounts(requestStats));
		verify(result, never()).getStdout();
	}

	@Test
	void testRemoteCommandOfAnInterruptedCallerIsNotSent() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doReturn(mock(WinRemoteCommandExecutor.class)).when(executor)
			.executeRemoteCommand(any(), any(), any(), any(), anyLong(), any());
		Thread.currentThread().interrupt();
		try {
			final ClientException e = assertThrows(
				ClientException.class,
				() -> executor.executeWmiRemoteCommand(COMMAND, HOSTNAME, null, null, 30, null, requestStats)
			);
			assertTrue(Thread.currentThread().isInterrupted());
			// The interrupt is the cause: no more null cause
			assertInstanceOf(InterruptedException.class, e.getCause());
		} finally {
			Thread.interrupted();
		}
		verify(executor, never()).executeRemoteCommand(any(), any(), any(), any(), anyLong(), any());
		assertEquals(Map.of(), requestStats.snapshot());
	}
}
