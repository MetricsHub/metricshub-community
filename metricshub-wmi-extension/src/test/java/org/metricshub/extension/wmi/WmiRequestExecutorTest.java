package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.wmi.exceptions.WmiComException;
import org.metricshub.wmi.exceptions.WqlQuerySyntaxException;
import org.metricshub.wmi.remotecommand.WinRemoteCommandExecutor;
import org.metricshub.wmi.wbem.WmiWbemServices;
import org.mockito.MockedStatic;

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
	 * WMI services whose query returns the given rows.
	 */
	private static WmiWbemServices servicesReturning(final List<Map<String, Object>> rows) throws Exception {
		final WmiWbemServices services = mock(WmiWbemServices.class);
		when(services.executeWql(anyString(), anyLong())).thenReturn(rows);
		return services;
	}

	/**
	 * WMI services whose query throws the given exception.
	 */
	private static WmiWbemServices servicesThrowing(final Exception exception) throws Exception {
		final WmiWbemServices services = mock(WmiWbemServices.class);
		when(services.executeWql(anyString(), anyLong())).thenThrow(exception);
		return services;
	}

	/**
	 * Run a WQL query against the given services, and return the request statistics it was counted in.
	 */
	private static RequestStats query(final WmiWbemServices services) throws Exception {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			try {
				new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats);
			} catch (ClientException e) {
				// Counted all the same
			}
		}
		return requestStats;
	}

	/**
	 * Run a WQL query whose connection throws the given exception, and return the request statistics it was
	 * counted in.
	 */
	private static RequestStats queryWithConnectionFailure(final Exception exception) {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenThrow(exception);
			final ClientException e = assertThrows(
				ClientException.class,
				() -> new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
			);
			assertSame(exception, e.getCause());
		}
		return requestStats;
	}

	@Test
	void testExecuteWmiCountsCompleted() throws Exception {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			final WmiWbemServices services = servicesReturning(List.of(Map.of("Name", "host")));
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			assertEquals(
				List.of(List.of("host")),
				new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
			);
		}
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiCountsAnEmptyResultAsCompleted() throws Exception {
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(query(servicesReturning(List.of()))));
	}

	@Test
	void testExecuteWmiCountsOnceWhenProcessingTheResultFails() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WmiRequestExecutor executor = spy(new WmiRequestExecutor());
		doThrow(new IllegalStateException("conversion")).when(executor).buildWmiTable(any(), any());
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			final WmiWbemServices services = servicesReturning(List.of(Map.of("Name", "host")));
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			assertThrows(
				ClientException.class,
				() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
			);
		}
		// The query was answered: the local conversion failure is not a second outcome
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiCountsTimeout() throws Exception {
		// wmi-java reports WBEM_S_TIMEDOUT as a TimeoutException
		assertEquals(
			List.of(0L, 1L, 0L),
			wmiCounts(query(servicesThrowing(new TimeoutException("No results after 30000 ms."))))
		);
	}

	@Test
	void testExecuteWmiCountsTimeoutHresults() {
		for (final int hResult : new int[] { 0x80041069, 0x80041088, 0x8001011F, 0x800705B4 }) {
			final WmiComException exception = new WmiComException(
				new COMException("timed out", new HRESULT(hResult)),
				"timed out"
			);
			assertEquals(List.of(0L, 1L, 0L), wmiCounts(queryWithConnectionFailure(exception)), Integer.toHexString(hResult));
		}
	}

	@Test
	void testExecuteWmiCountsFailures() throws Exception {
		// Connection refused by the host: a COM error that is not a timeout
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(
				queryWithConnectionFailure(
					new WmiComException(new COMException("RPC server unavailable", new HRESULT(0x800706BA)), "unavailable")
				)
			)
		);
		// An error the health check and the namespace detection accept
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(query(servicesThrowing(new WmiComException("WBEM_E_INVALID_NAMESPACE (0x8004100E)"))))
		);
		// A timeout mnemonic in a message only: wmi-java gives no HRESULT to classify
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(query(servicesThrowing(new WmiComException("WBEM_E_TIMED_OUT (0x80041069)"))))
		);
		// Parsed after the connection was made
		assertEquals(
			List.of(0L, 0L, 1L),
			wmiCounts(query(servicesThrowing(new WqlQuerySyntaxException("Syntax error in WQL Query"))))
		);
	}

	@Test
	void testExecuteWmiCountsNothingOnLocalArgumentChecks() {
		assertEquals(
			Map.of(),
			queryWithConnectionFailure(
				new IllegalArgumentException("A local resource must be accessed without specific credentials")
			).snapshot()
		);
	}

	@Test
	void testExecuteWmiWithoutRequestStats() throws Exception {
		try (MockedStatic<WmiWbemServices> statics = mockStatic(WmiWbemServices.class)) {
			final WmiWbemServices services = servicesReturning(List.of(Map.of("Name", "host")));
			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenReturn(services);
			assertEquals(
				List.of(List.of("host")),
				new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null)
			);

			statics.when(() -> WmiWbemServices.getInstance(any(), any(), any())).thenThrow(new WmiComException("down"));
			assertThrows(
				ClientException.class,
				() -> new WmiRequestExecutor().executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null)
			);
		}
	}

	/**
	 * Run a remote command whose execution throws the given exception, and return the thrown exception.
	 */
	private static ClientException runCommandFailing(final Exception exception, final RequestStats requestStats) {
		try (MockedStatic<WinRemoteCommandExecutor> statics = mockStatic(WinRemoteCommandExecutor.class)) {
			statics.when(
				() -> WinRemoteCommandExecutor.execute(any(), any(), any(), any(), any(), anyLong(), any(), anyBoolean())
			).thenThrow(exception);
			return assertThrows(
				ClientException.class,
				() -> new WmiRequestExecutor().executeWmiRemoteCommand(COMMAND, HOSTNAME, null, null, 30, null, requestStats)
			);
		}
	}

	@Test
	void testExecuteWmiRemoteCommandCountsCompleted() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WinRemoteCommandExecutor result = mock(WinRemoteCommandExecutor.class);
		when(result.getStdout()).thenReturn("output");
		try (MockedStatic<WinRemoteCommandExecutor> statics = mockStatic(WinRemoteCommandExecutor.class)) {
			statics.when(
				() -> WinRemoteCommandExecutor.execute(any(), any(), any(), any(), any(), anyLong(), any(), anyBoolean())
			).thenReturn(result);
			assertEquals(
				"output",
				new WmiRequestExecutor().executeWinRemoteCommand(HOSTNAME, CONFIGURATION, COMMAND, null, requestStats)
			);
		}
		assertEquals(List.of(1L, 0L, 0L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiRemoteCommandCountsTimeout() {
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
	void testExecuteWmiRemoteCommandCountsFailure() {
		final RequestStats requestStats = new RequestStats();
		final IOException ioException = new IOException("copy failed", new IOException("access denied"));
		final ClientException e = runCommandFailing(ioException, requestStats);

		// Same exception as before: the cause of the wmi-java exception
		assertEquals(ioException.getCause().toString(), e.getMessage());
		assertSame(ioException.getCause(), e.getCause());
		assertEquals(List.of(0L, 0L, 1L), wmiCounts(requestStats));
	}

	@Test
	void testExecuteWmiRemoteCommandCountsNothingOnLocalArgumentChecks() {
		final RequestStats requestStats = new RequestStats();
		runCommandFailing(new IllegalArgumentException("timeout=0 must not be negative or zero."), requestStats);
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testExecuteWmiRemoteCommandWithoutRequestStats() {
		assertDoesNotThrow(() -> runCommandFailing(new TimeoutException("No time left"), null));
	}
}
