package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.common.helpers.ThreadHelper.NotStartedException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.wbem.client.WbemExecutor;
import org.metricshub.wbem.client.WbemQueryResult;
import org.metricshub.wbem.client.exceptions.WqlQuerySyntaxException;
import org.metricshub.wbem.javax.wbem.WBEMException;
import org.mockito.MockedStatic;
import org.mockito.stubbing.OngoingStubbing;

class WbemRequestExecutorTest {

	private static final String HOST_NAME = "test-host" + UUID.randomUUID();
	private static final String RESOURCE_HOSTNAME = "resource-host";
	private static final String VCENTER = "vcenter";
	private static final String USERNAME = "testUser";
	private static final String PASSWORD = "testPassword";
	private static final String NAMESPACE = "testNamespace";
	private static final String QUERY = "testQuery";

	WbemRequestExecutor wbemRequestExecutor = new WbemRequestExecutor();

	@Test
	void testIsAcceptableException() {
		assertFalse(wbemRequestExecutor.isAcceptableException(null));
		assertFalse(wbemRequestExecutor.isAcceptableException(new Exception()));
		assertFalse(wbemRequestExecutor.isAcceptableException(new Exception(new Exception())));

		assertFalse(wbemRequestExecutor.isAcceptableException(new WBEMException("other")));
		assertFalse(wbemRequestExecutor.isAcceptableException(new WBEMException(0)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_NOT_FOUND)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_INVALID_NAMESPACE)));
		assertTrue(wbemRequestExecutor.isAcceptableException(new WBEMException(WBEMException.CIM_ERR_INVALID_CLASS)));

		assertTrue(
			wbemRequestExecutor.isAcceptableException(new Exception(new WBEMException(WBEMException.CIM_ERR_NOT_FOUND)))
		);
	}

	@Test
	void testDoWbemQuery() throws ClientException {
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			final WbemConfiguration wbemConfiguration = WbemConfiguration.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.timeout(120L)
				.build();

			final List<String> properties = Arrays.asList("value1a", "value2a", "value3a");

			final List<List<String>> values = Arrays.asList(
				Arrays.asList("value1a", "value2a", "value3a"),
				Arrays.asList("value1b", "value2b", "value3b")
			);

			assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.doWbemQuery(HOST_NAME, wbemConfiguration, QUERY, NAMESPACE, null)
			);

			WbemQueryResult wbemQueryResult = new WbemQueryResult(properties, values);

			wbemExecutorMock.when(
				() -> WbemExecutor.executeWql(
					any(URL.class),
					anyString(),
					anyString(),
					eq(PASSWORD.toCharArray()),
					anyString(),
					anyInt(),
					eq(null)
				)
			).thenReturn(wbemQueryResult);

			assertEquals(values, wbemRequestExecutor.doWbemQuery(HOST_NAME, wbemConfiguration, QUERY, NAMESPACE, null));
		}
	}

	@Test
	void testExecuteWbemRecordEnabled(@TempDir final Path tempDir) throws Exception {
		WbemRecorder.clearInstances();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			final WbemConfiguration wbemConfiguration = WbemConfiguration.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.timeout(120L)
				.build();

			final List<String> properties = Arrays.asList("value1a", "value2a");
			final List<List<String>> values = Arrays.asList(Arrays.asList("value1a", "value2a"));
			final WbemQueryResult wbemQueryResult = new WbemQueryResult(properties, values);

			wbemExecutorMock.when(
				() -> WbemExecutor.executeWql(
					any(URL.class),
					anyString(),
					anyString(),
					eq(PASSWORD.toCharArray()),
					anyString(),
					anyInt(),
					eq(null)
				)
			).thenReturn(wbemQueryResult);

			final TelemetryManager telemetryManager = TelemetryManager.builder()
				.recordOutputDirectory(tempDir.toString())
				.build();

			final List<List<String>> result = wbemRequestExecutor.executeWbem(
				HOST_NAME,
				wbemConfiguration,
				QUERY,
				NAMESPACE,
				telemetryManager
			);

			assertEquals(values, result);
			WbemRecorder.flushAndRemoveInstance(tempDir.toString());
			final Path wbemDir = tempDir.resolve(WbemRecorder.WBEM_SUBDIR);
			final Path imageFile = wbemDir.resolve(WbemRecorder.IMAGE_YAML);
			assertTrue(Files.isRegularFile(imageFile));
			assertNotNull(Files.readString(imageFile));
		}
	}

	/**
	 * Builds a WBEM configuration with credentials, optionally through a vCenter server.
	 *
	 * @param vCenter The vCenter server, or {@code null}
	 * @return A new configuration
	 */
	private static WbemConfiguration buildConfiguration(final String vCenter) {
		return WbemConfiguration.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.vCenter(vCenter)
			.timeout(120L)
			.build();
	}

	/**
	 * Builds the telemetry manager of the monitored resource {@link #RESOURCE_HOSTNAME}.
	 *
	 * @return A new telemetry manager
	 */
	private static TelemetryManager buildTelemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(RESOURCE_HOSTNAME).hostId(RESOURCE_HOSTNAME).build())
			.build();
	}

	/**
	 * Stubs any call to {@link WbemExecutor#executeWql}.
	 *
	 * @param wbemExecutorMock The static mock of {@link WbemExecutor}
	 * @return The stubbing to complete
	 */
	private static OngoingStubbing<WbemQueryResult> whenExecuteWql(final MockedStatic<WbemExecutor> wbemExecutorMock) {
		return wbemExecutorMock.when(() -> WbemExecutor.executeWql(any(), any(), any(), any(), any(), anyInt(), any()));
	}

	/**
	 * Asserts the WBEM request counts of the given statistics.
	 *
	 * @param requestStats The request statistics
	 * @param completed    Expected completed requests
	 * @param timeout      Expected timed out requests
	 * @param failed       Expected failed requests
	 */
	private static void assertCounts(
		final RequestStats requestStats,
		final long completed,
		final long timeout,
		final long failed
	) {
		assertEquals(
			Map.of(
				WbemRequestExecutor.WBEM_OPERATION_TYPE,
				Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
			),
			requestStats.snapshot()
		);
	}

	/**
	 * Runs one WBEM query, without vCenter, whose {@link WbemExecutor} call throws the given exception.
	 *
	 * @param exception The exception thrown by {@link WbemExecutor#executeWql}
	 * @return The request statistics of the resource
	 */
	private RequestStats executeWbemThrowing(final Exception exception) {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			whenExecuteWql(wbemExecutorMock).thenThrow(exception);
			final ClientException thrown = assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(null), QUERY, NAMESPACE, telemetryManager)
			);
			// The exception reaches the caller unchanged
			assertSame(exception, thrown.getCause());
		}
		return telemetryManager.getRequestStats();
	}

	@Test
	void testExecuteWbemCountsCompletedOnTheResource() throws ClientException {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			whenExecuteWql(wbemExecutorMock).thenReturn(new WbemQueryResult(List.of("Name"), List.of(List.of("value"))));

			// The network target (HOST_NAME) differs from the resource hostname: the count goes to the resource
			assertEquals(
				List.of(List.of("value")),
				wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(null), QUERY, NAMESPACE, telemetryManager)
			);
		}
		assertCounts(telemetryManager.getRequestStats(), 1, 0, 0);
	}

	@Test
	void testExecuteWbemCountsEmptyResultAsCompleted() throws ClientException {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			whenExecuteWql(wbemExecutorMock).thenReturn(new WbemQueryResult(List.of("Name"), List.of()));

			assertEquals(
				List.of(),
				wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(null), QUERY, NAMESPACE, telemetryManager)
			);
		}
		assertCounts(telemetryManager.getRequestStats(), 1, 0, 0);
	}

	@Test
	void testExecuteWbemCountsTimeouts() {
		assertCounts(executeWbemThrowing(new TimeoutException()), 0, 1, 0);
		assertCounts(
			executeWbemThrowing(
				new WBEMException(WBEMException.CIM_ERR_FAILED, "Connection timed out", null, new SocketTimeoutException())
			),
			0,
			1,
			0
		);
		assertCounts(executeWbemThrowing(new InterruptedException()), 0, 1, 0);
	}

	@Test
	void testThreadInterruptedBeforeTheRequestCountsNothing() {
		// An earlier deadline of the job interrupted the thread: the query is never handed to WbemExecutor, whose
		// worker would send it anyway
		Thread.currentThread().interrupt();
		try {
			final TelemetryManager queryTelemetryManager = buildTelemetryManager();
			try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
				final ClientException thrown = assertThrows(
					ClientException.class,
					() -> wbemRequestExecutor.executeWbem(
						HOST_NAME,
						buildConfiguration(null),
						QUERY,
						NAMESPACE,
						queryTelemetryManager
					)
				);
				assertInstanceOf(InterruptedException.class, thrown.getCause());
				wbemExecutorMock.verifyNoInteractions();
			}
			assertTrue(Thread.currentThread().isInterrupted());
			assertTrue(queryTelemetryManager.getRequestStats().snapshot().isEmpty());

			// ThreadHelper fails the vCenter ticket request before sending
			final TelemetryManager telemetryManager = buildTelemetryManager();
			executeVCenterTicketFailure(
				telemetryManager,
				stubbing -> stubbing.thenThrow(new NotStartedException("Interrupted before the task started"))
			);
			assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testVCenterTicketTimedOutBeforeItStartedCountsNothing() {
		// The worker never got to the ticket request before the timeout: nothing was sent
		final TimeoutException notStarted = new TimeoutException();
		notStarted.initCause(new NotStartedException("Timed out before the task started"));
		final TelemetryManager telemetryManager = buildTelemetryManager();
		executeVCenterTicketFailure(telemetryManager, stubbing -> stubbing.thenThrow(notStarted));
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testExecuteWbemCountsWbemErrorsAsFailed() {
		assertCounts(executeWbemThrowing(new WBEMException(WBEMException.CIM_ERR_ACCESS_DENIED)), 0, 0, 1);
		assertCounts(executeWbemThrowing(new WBEMException(WBEMException.CIM_ERR_INVALID_NAMESPACE)), 0, 0, 1);
		assertCounts(
			executeWbemThrowing(
				new WBEMException(WBEMException.CIM_ERR_FAILED, "Unable to connect", null, new ConnectException())
			),
			0,
			0,
			1
		);
		// An IllegalArgumentException with credentials set comes from the worker of WbemExecutor
		assertCounts(executeWbemThrowing(new IllegalArgumentException("Not a valid CIM_DATETIME value")), 0, 0, 1);
	}

	@Test
	void testExecuteWbemCountsNothingWhenRejectedBeforeSending() {
		assertTrue(executeWbemThrowing(new WqlQuerySyntaxException(QUERY)).snapshot().isEmpty());

		// WbemExecutor rejects a null password before sending anything
		final TelemetryManager telemetryManager = buildTelemetryManager();
		final WbemConfiguration noPassword = WbemConfiguration.builder().username(USERNAME).timeout(120L).build();
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			whenExecuteWql(wbemExecutorMock).thenThrow(new IllegalArgumentException("password must not be null."));
			assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.executeWbem(HOST_NAME, noPassword, QUERY, NAMESPACE, telemetryManager)
			);
		}
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testDoWbemQueryWithoutRequestStats() throws ClientException {
		try (MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class)) {
			whenExecuteWql(wbemExecutorMock).thenReturn(new WbemQueryResult(List.of("Name"), List.of(List.of("value"))))
				.thenThrow(new WBEMException(WBEMException.CIM_ERR_FAILED));

			assertEquals(
				List.of(List.of("value")),
				wbemRequestExecutor.doWbemQuery(HOST_NAME, buildConfiguration(null), QUERY, NAMESPACE, null)
			);
			assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.doWbemQuery(HOST_NAME, buildConfiguration(null), QUERY, NAMESPACE, null)
			);
		}
	}

	@Test
	void testVCenterTicketCacheHitCountsOnlyTheQuery() throws Exception {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		telemetryManager.getHostProperties().setVCenterTicket("ticket");
		try (
			MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class);
			MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)
		) {
			whenExecuteWql(wbemExecutorMock).thenReturn(new WbemQueryResult(List.of("Name"), List.of(List.of("value"))));

			wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(VCENTER), QUERY, NAMESPACE, telemetryManager);

			threadHelperMock.verify(() -> ThreadHelper.execute(any(), anyLong()), never());
		}
		assertCounts(telemetryManager.getRequestStats(), 1, 0, 0);
	}

	@Test
	void testVCenterTicketMissCountsTheTicketAndTheQuery() throws Exception {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		try (
			MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class);
			MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)
		) {
			threadHelperMock.when(() -> ThreadHelper.execute(any(), anyLong())).thenReturn("ticket");
			whenExecuteWql(wbemExecutorMock).thenReturn(new WbemQueryResult(List.of("Name"), List.of(List.of("value"))));

			// The vCenter server is an intermediary: its ticket request is counted on the resource
			wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(VCENTER), QUERY, NAMESPACE, telemetryManager);
		}
		assertCounts(telemetryManager.getRequestStats(), 2, 0, 0);
		assertEquals("ticket", telemetryManager.getHostProperties().getVCenterTicket());
	}

	/**
	 * Runs one WBEM query through vCenter, with no cached ticket, whose ticket request ends with the given stubbing.
	 *
	 * @param telemetryManager The telemetry manager of the resource
	 * @param ticketStubbing   Completes the stubbing of the ticket request
	 * @return The exception thrown to the caller
	 */
	private ClientException executeVCenterTicketFailure(
		final TelemetryManager telemetryManager,
		final Consumer<OngoingStubbing<Object>> ticketStubbing
	) {
		try (
			MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class);
			MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)
		) {
			ticketStubbing.accept(threadHelperMock.when(() -> ThreadHelper.execute(any(), anyLong())));
			threadHelperMock.when(() -> ThreadHelper.neverStarted(any())).thenCallRealMethod();
			final ClientException thrown = assertThrows(
				ClientException.class,
				() -> wbemRequestExecutor.executeWbem(
					HOST_NAME,
					buildConfiguration(VCENTER),
					QUERY,
					NAMESPACE,
					telemetryManager
				)
			);
			wbemExecutorMock.verifyNoInteractions();
			return thrown;
		}
	}

	@Test
	void testVCenterNullTicketCountsFailed() {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		executeVCenterTicketFailure(telemetryManager, stubbing -> stubbing.thenReturn(null));
		assertCounts(telemetryManager.getRequestStats(), 0, 0, 1);
	}

	@Test
	void testVCenterTicketTimeoutAndInterruptionCountTimeout() {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		final TimeoutException timeoutException = new TimeoutException();
		assertSame(
			timeoutException,
			executeVCenterTicketFailure(telemetryManager, stubbing -> stubbing.thenThrow(timeoutException)).getCause()
		);
		assertCounts(telemetryManager.getRequestStats(), 0, 1, 0);

		executeVCenterTicketFailure(telemetryManager, stubbing -> stubbing.thenThrow(new InterruptedException()));
		// The interrupt flag is restored for the caller
		assertTrue(Thread.interrupted());
		assertCounts(telemetryManager.getRequestStats(), 0, 2, 0);
	}

	@Test
	void testVCenterTicketExecutionFailures() {
		final TelemetryManager telemetryManager = buildTelemetryManager();

		// The vCenter URL is built locally: nothing was sent
		executeVCenterTicketFailure(
			telemetryManager,
			stubbing -> stubbing.thenThrow(new ExecutionException(new MalformedURLException("bad URL")))
		);
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());

		final ExecutionException failure = new ExecutionException(new Exception("Unable to find host"));
		assertSame(
			failure,
			executeVCenterTicketFailure(telemetryManager, stubbing -> stubbing.thenThrow(failure)).getCause()
		);
		assertCounts(telemetryManager.getRequestStats(), 0, 0, 1);

		executeVCenterTicketFailure(
			telemetryManager,
			stubbing -> stubbing.thenThrow(new ExecutionException(new RemoteException("read", new SocketTimeoutException())))
		);
		assertCounts(telemetryManager.getRequestStats(), 0, 1, 1);
	}

	@Test
	void testVCenterAccessDeniedCountsEveryAttempt() throws Exception {
		final TelemetryManager telemetryManager = buildTelemetryManager();
		try (
			MockedStatic<WbemExecutor> wbemExecutorMock = mockStatic(WbemExecutor.class);
			MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)
		) {
			threadHelperMock.when(() -> ThreadHelper.execute(any(), anyLong())).thenReturn("ticket1", "ticket2");
			whenExecuteWql(wbemExecutorMock).thenThrow(new WBEMException(WBEMException.CIM_ERR_ACCESS_DENIED))
				.thenReturn(new WbemQueryResult(List.of("Name"), List.of(List.of("value"))));

			assertEquals(
				List.of(List.of("value")),
				wbemRequestExecutor.executeWbem(HOST_NAME, buildConfiguration(VCENTER), QUERY, NAMESPACE, telemetryManager)
			);
			threadHelperMock.verify(() -> ThreadHelper.execute(any(), anyLong()), times(2));
		}
		// 2 tickets and the retried query completed, the first query was denied
		assertCounts(telemetryManager.getRequestStats(), 3, 0, 1);
		assertEquals("ticket2", telemetryManager.getHostProperties().getVCenterTicket());
	}
}
