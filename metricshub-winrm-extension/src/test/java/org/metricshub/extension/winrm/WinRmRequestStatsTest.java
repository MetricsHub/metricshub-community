package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.winrm.CommandRequest;
import org.metricshub.winrm.CommandResult;
import org.metricshub.winrm.RemoteFile;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.WqlRequest;
import org.metricshub.winrm.WqlResult;
import org.metricshub.winrm.WqlRow;
import org.metricshub.winrm.exceptions.WinRMAuthenticationException;
import org.metricshub.winrm.exceptions.WinRMClientException;
import org.metricshub.winrm.exceptions.WinRMFaultException;
import org.metricshub.winrm.exceptions.WinRMTimeoutException;
import org.metricshub.winrm.exceptions.WqlSyntaxException;
import org.mockito.MockedStatic;

/**
 * Every WinRM request sent for a monitored resource is counted once, under the "winrm" label.
 */
class WinRmRequestStatsTest {

	private static final String HOSTNAME = "host";
	private static final String QUERY = "SELECT Name FROM Win32_ComputerSystem";
	private static final String NAMESPACE = "root/cimv2";
	private static final String COMMAND = "ipconfig";
	private static final String PATH = "C:/logs/app.log";
	private static final WinRmConfiguration CONFIGURATION = WinRmConfiguration.builder()
		.username("user")
		.password("pass".toCharArray())
		.timeout(30L)
		.build();

	/**
	 * The counters of the "winrm" label, as completed, timeout and failed counts.
	 */
	private static List<Long> winRmCounts(final RequestStats requestStats) {
		final Map<RequestOutcome, Long> counts = requestStats.snapshot().get(WinRmExtension.IDENTIFIER);
		return counts == null
			? List.of(0L, 0L, 0L)
			: List.of(
				counts.get(RequestOutcome.COMPLETED),
				counts.get(RequestOutcome.TIMEOUT),
				counts.get(RequestOutcome.FAILED)
			);
	}

	/**
	 * A WQL result of one row with one Name column.
	 */
	private static WqlResult oneRow() {
		final WqlRow row = mock(WqlRow.class);
		when(row.string("Name")).thenReturn("server");
		final WqlResult result = mock(WqlResult.class);
		when(result.columns()).thenReturn(List.of("Name"));
		when(result.size()).thenReturn(1);
		when(result.iterator()).thenAnswer(invocation -> List.of(row).iterator());
		return result;
	}

	/**
	 * A WQL result without any row.
	 */
	private static WqlResult noRow() {
		final WqlResult result = mock(WqlResult.class);
		when(result.columns()).thenReturn(List.of());
		when(result.iterator()).thenAnswer(invocation -> Collections.<WqlRow>emptyIterator());
		return result;
	}

	/**
	 * A client whose WQL request executes with the given result.
	 */
	private static WinRMClient wqlClient(final WqlResult result) {
		final WqlRequest request = mock(WqlRequest.class);
		when(request.execute()).thenReturn(result);
		final WinRMClient client = mock(WinRMClient.class);
		when(client.wql(any())).thenReturn(request);
		return client;
	}

	/**
	 * A client whose WQL request throws the given exception.
	 */
	private static WinRMClient failingWqlClient(final RuntimeException exception) {
		final WqlRequest request = mock(WqlRequest.class);
		when(request.execute()).thenThrow(exception);
		final WinRMClient client = mock(WinRMClient.class);
		when(client.wql(any())).thenReturn(request);
		return client;
	}

	/**
	 * Run a WQL query through the given client, and return the request statistics it was counted in.
	 */
	private static RequestStats query(final WinRMClient client) {
		final RequestStats requestStats = new RequestStats();
		final WinRmRequestExecutor executor = new WinRmRequestExecutor((host, config) -> client, Duration.ofHours(1));
		try {
			executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats);
		} catch (ClientException e) {
			// Counted all the same
		} finally {
			executor.close();
		}
		return requestStats;
	}

	@Test
	void testWqlCountsCompleted() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WinRmRequestExecutor executor = new WinRmRequestExecutor(
			(host, config) -> wqlClient(oneRow()),
			Duration.ofHours(1)
		);
		assertEquals(
			List.of(List.of("server")),
			executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
		);
		executor.close();
		assertEquals(List.of(1L, 0L, 0L), winRmCounts(requestStats));
	}

	@Test
	void testWqlCountsAnEmptyResultAsCompleted() {
		assertEquals(List.of(1L, 0L, 0L), winRmCounts(query(wqlClient(noRow()))));
	}

	@Test
	void testWqlCountsTimeouts() {
		// The deadline of winrm-java
		assertEquals(
			List.of(0L, 1L, 0L),
			winRmCounts(query(failingWqlClient(new WinRMTimeoutException("timed out", new TimeoutException()))))
		);
		// The exception type alone says it is a timeout, whatever its cause
		assertEquals(
			List.of(0L, 1L, 0L),
			winRmCounts(query(failingWqlClient(new WinRMTimeoutException("timed out", null))))
		);
		// The server's operation timeout, reported as a fault
		assertEquals(
			List.of(0L, 1L, 0L),
			winRmCounts(
				query(
					failingWqlClient(
						new WinRMFaultException(
							"The WS-Management service cannot complete the operation within the time specified in OperationTimeout.",
							500,
							WinRmRequestExecutor.FAULT_OPERATION_TIMEOUT,
							"timeout",
							null
						)
					)
				)
			)
		);
		// Interrupted by the engine when a deadline of the strategy expired
		assertEquals(
			List.of(0L, 1L, 0L),
			winRmCounts(query(failingWqlClient(new WinRMClientException("interrupted", new InterruptedException()))))
		);
	}

	@Test
	void testRequestOnAnInterruptedThreadCountsNothing() {
		final RequestStats requestStats = new RequestStats();
		// An earlier deadline of the job interrupted the thread: the request fails on the interruption
		Thread.currentThread().interrupt();
		try {
			assertThrows(WinRMClientException.class, () -> WinRmRequestExecutor.executeRecorded(() -> {
				throw new WinRMClientException("interrupted", new InterruptedException());
			}, requestStats));
		} finally {
			Thread.interrupted();
		}
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testWqlCountsFailures() {
		assertEquals(
			List.of(0L, 0L, 1L),
			winRmCounts(query(failingWqlClient(new WinRMAuthenticationException("401 Unauthorized"))))
		);
		// An error the health check and the namespace detection accept
		assertEquals(
			List.of(0L, 0L, 1L),
			winRmCounts(
				query(
					failingWqlClient(new WinRMFaultException("Fault", 500, "2150858778", "reason", "WBEM_E_INVALID_NAMESPACE"))
				)
			)
		);
		assertEquals(
			List.of(0L, 0L, 1L),
			winRmCounts(query(failingWqlClient(new WinRMClientException("refused", new ConnectException()))))
		);
	}

	@Test
	void testWqlCountsNothingWhenNothingIsSent() {
		// winrm-java parses the WQL locally, before sending anything
		assertEquals(
			Map.of(),
			query(failingWqlClient(new WqlSyntaxException("Syntax error in WQL Query", new Exception()))).snapshot()
		);

		// The client cannot even be built (no credentials)
		final RequestStats requestStats = new RequestStats();
		final WinRmRequestExecutor executor = new WinRmRequestExecutor((host, config) -> {
			throw new IllegalStateException("credentials");
		}, Duration.ofHours(1));
		assertThrows(
			ClientException.class,
			() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, requestStats)
		);
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testWqlWithoutRequestStats() {
		final WinRmRequestExecutor executor = new WinRmRequestExecutor(
			(host, config) -> wqlClient(oneRow()),
			Duration.ofHours(1)
		);
		assertDoesNotThrow(() -> executor.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null));
		executor.close();

		final WinRmRequestExecutor failing = new WinRmRequestExecutor(
			(host, config) -> failingWqlClient(new WinRMTimeoutException("timed out", null)),
			Duration.ofHours(1)
		);
		assertThrows(
			ClientException.class,
			() -> failing.executeWmi(HOSTNAME, CONFIGURATION, QUERY, NAMESPACE, null, null)
		);
	}

	/**
	 * Run a command on a client whose command request executes with the given exit code, or throws the given
	 * exception.
	 */
	private static String runCommand(
		final int exitCode,
		final RuntimeException exception,
		final RequestStats requestStats
	) throws ClientException {
		final CommandRequest request = mock(CommandRequest.class);
		if (exception == null) {
			final CommandResult result = mock(CommandResult.class);
			when(result.exitCode()).thenReturn(exitCode);
			when(result.stdout()).thenReturn("output");
			when(result.stderr()).thenReturn("error");
			when(request.execute()).thenReturn(result);
		} else {
			when(request.execute()).thenThrow(exception);
		}
		final WinRMClient client = mock(WinRMClient.class);
		when(client.command(COMMAND)).thenReturn(request);
		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq(HOSTNAME), any())).thenReturn(client);
			return new WinRmRequestExecutor().executeWinRemoteCommand(HOSTNAME, CONFIGURATION, COMMAND, null, requestStats);
		}
	}

	@Test
	void testCommandCountsCompletedWhateverItsExitCode() throws Exception {
		final RequestStats requestStats = new RequestStats();
		assertEquals("output", runCommand(0, null, requestStats));

		// A non-zero exit code still fails the call, as before, but the command ran: it was answered
		final ClientException e = assertThrows(ClientException.class, () -> runCommand(1, null, requestStats));
		assertEquals("WinRM remote command failed on host.", e.getMessage());
		assertEquals(List.of(2L, 0L, 0L), winRmCounts(requestStats));
	}

	@Test
	void testCommandCountsTimeoutAndFailure() {
		final RequestStats requestStats = new RequestStats();
		assertThrows(
			ClientException.class,
			() -> runCommand(0, new WinRMTimeoutException("timed out", new TimeoutException()), requestStats)
		);
		assertThrows(
			ClientException.class,
			() -> runCommand(0, new WinRMAuthenticationException("401 Unauthorized"), requestStats)
		);
		assertEquals(List.of(0L, 1L, 1L), winRmCounts(requestStats));
	}

	@Test
	void testCommandCountsNothingWhenTheClientCannotBeBuilt() {
		final RequestStats requestStats = new RequestStats();
		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq(HOSTNAME), any()))
				.thenThrow(new IllegalStateException("credentials"));
			assertThrows(
				ClientException.class,
				() -> new WinRmRequestExecutor().executeWinRemoteCommand(HOSTNAME, CONFIGURATION, COMMAND, null, requestStats)
			);
		}
		assertEquals(Map.of(), requestStats.snapshot());
	}

	@Test
	void testFileOperationsCountEachOperation() throws Exception {
		final RequestStats requestStats = new RequestStats();
		final WinRMClient client = mock(WinRMClient.class, RETURNS_DEEP_STUBS);
		final RemoteFile file = mock(RemoteFile.class);
		when(client.file(PATH)).thenReturn(file);
		when(file.offset(0L)).thenReturn(file);
		when(file.length(10L)).thenReturn(file);
		final WinRmFileOperations fileOperations = new WinRmFileOperations(client, HOSTNAME, requestStats);

		// A missing file is an answer: completed, and no size as before
		when(file.info()).thenReturn(Optional.empty());
		assertNull(fileOperations.getFileSize(PATH));
		assertEquals(List.of(1L, 0L, 0L), winRmCounts(requestStats));

		// Access denied: failed, and still no size
		when(file.info()).thenThrow(new WinRMClientException("Access denied"));
		assertNull(fileOperations.getFileSize(PATH));
		assertEquals(List.of(1L, 0L, 1L), winRmCounts(requestStats));

		// Read timed out: still an IOException
		when(file.readBytes()).thenThrow(new WinRMTimeoutException("timed out", new TimeoutException()));
		assertThrows(IOException.class, () -> fileOperations.readFromOffset(PATH, 0L, 10));
		assertEquals(List.of(1L, 1L, 1L), winRmCounts(requestStats));

		// Read
		when(file.readText(StandardCharsets.UTF_8)).thenReturn("content");
		assertEquals("content", fileOperations.readFileContent(PATH));
		assertEquals(List.of(2L, 1L, 1L), winRmCounts(requestStats));

		// Not found: failed, and no content as before
		when(file.readText(StandardCharsets.UTF_8)).thenThrow(new WinRMClientException("Remote path not found"));
		assertNull(fileOperations.readFileContent(PATH));
		assertEquals(List.of(2L, 1L, 2L), winRmCounts(requestStats));

		// Listing failed: still a ClientException
		when(client.file("C:\\logs").list().maxDepth(1).filesOnly().glob("*.log").execute()).thenThrow(
			new WinRMClientException("Access denied")
		);
		assertThrows(
			ClientException.class,
			() -> fileOperations.list(FileHelper.parsePathPattern("C:\\logs\\*.log", DeviceKind.WINDOWS))
		);
		assertEquals(List.of(2L, 1L, 3L), winRmCounts(requestStats));
	}

	@Test
	void testFileOperationsWithoutRequestStats() {
		final WinRMClient client = mock(WinRMClient.class);
		final RemoteFile file = mock(RemoteFile.class);
		when(client.file(PATH)).thenReturn(file);
		when(file.info()).thenThrow(new WinRMClientException("Access denied"));
		assertNull(new WinRmFileOperations(client, HOSTNAME, null).getFileSize(PATH));
	}

	/**
	 * A resource whose WinRM configuration overrides its hostname (IP literals: nothing is resolved).
	 */
	private static TelemetryManager newTelemetryManager() {
		final WinRmConfiguration configuration = WinRmConfiguration.builder()
			.hostname("192.0.2.20")
			.username("user")
			.password("pass".toCharArray())
			.timeout(30L)
			.build();
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname("192.0.2.10")
					.hostId("192.0.2.10")
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WinRmConfiguration.class, configuration))
					.build()
			)
			.build();
	}

	@Test
	void testCheckProtocolCountsAnAcceptedFaultAsFailedOnTheResource() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final WinRMClient client = failingWqlClient(
			new WinRMFaultException("Fault", 500, "2150858778", "reason", "WBEM_E_INVALID_NAMESPACE")
		);
		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			// The request goes to the protocol hostname...
			statics.when(() -> WinRmRequestExecutor.newClient(eq("192.0.2.20"), any())).thenReturn(client);
			final WinRmExtension extension = new WinRmExtension();

			// ... which the health check still considers up
			assertEquals(Optional.of(true), extension.checkProtocol(telemetryManager));
			extension.onShutdown();
		}
		// ... and it is counted on the resource, as failed
		assertEquals(List.of(0L, 0L, 1L), winRmCounts(telemetryManager.getRequestStats()));
	}

	@Test
	void testEventLogSourceIsCountedAsWinRm() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final WinRMClient client = wqlClient(noRow());
		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(any(), any())).thenReturn(client);
			final WinRmExtension extension = new WinRmExtension();
			extension.processSource(
				EventLogSource.builder().key("eventLog").logName("System").build(),
				"connector",
				telemetryManager
			);
			extension.onShutdown();
		}
		assertEquals(
			List.of(WinRmExtension.IDENTIFIER),
			List.copyOf(telemetryManager.getRequestStats().snapshot().keySet())
		);
		assertEquals(List.of(1L, 0L, 0L), winRmCounts(telemetryManager.getRequestStats()));
	}
}
