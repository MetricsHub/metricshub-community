package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.extension.oscommand.OsCommandExtension.OS_COMMAND_IDENTIFIER;
import static org.metricshub.extension.oscommand.OsCommandExtension.SSH_IDENTIFIER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.ssh.SshClient;
import org.mockito.MockedStatic;

/**
 * Tests the {@code metricshub.host.requests} outcomes recorded by OS commands, SSH commands and their health checks.
 */
class OsCommandRequestStatsTest {

	private static final String RESOURCE_HOSTNAME = "resource-host";
	private static final String SSH_HOSTNAME = "ssh-host";
	private static final String USERNAME = "user";
	private static final String COMMAND = "echo test";
	private static final String LONG_COMMAND = LocalOsHandler.isWindows() ? "ping -n 30 127.0.0.1" : "sleep 30";

	@AfterEach
	void clearInterrupt() {
		// A test that interrupts itself must not leak the flag to the next one
		Thread.interrupted();
	}

	/**
	 * @return the expected snapshot of the request statistics, for one operation type
	 */
	private static Map<String, Map<RequestOutcome, Long>> counts(
		final String operationType,
		final long completed,
		final long timeout,
		final long failed
	) {
		return Map.of(
			operationType,
			Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
		);
	}

	/**
	 * @return a telemetry manager of the local host, without any protocol configuration
	 */
	private static TelemetryManager localTelemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostId("localhost").hostname("localhost").configurations(Map.of()).build()
			)
			.build();
	}

	/**
	 * @param sshHostname the hostname set in the SSH configuration, {@code null} to use the resource hostname
	 * @return a telemetry manager of a remote Linux host monitored through SSH
	 */
	private static TelemetryManager remoteTelemetryManager(final String sshHostname) {
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder()
			.hostname(sshHostname)
			.username(USERNAME)
			.password("pwd".toCharArray())
			.timeout(5L)
			.build();
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(RESOURCE_HOSTNAME)
					.hostname(RESOURCE_HOSTNAME)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(SshConfiguration.class, sshConfiguration))
					.build()
			)
			.build();
	}

	/**
	 * @return the PIDs of the live descendants of this JVM
	 */
	private static Set<Long> descendants() {
		return ProcessHandle.current().descendants().map(ProcessHandle::pid).collect(Collectors.toSet());
	}

	/**
	 * Asserts that the processes started since {@code before} have all terminated.
	 */
	private static void assertNoProcessLeftSince(final Set<Long> before) throws Exception {
		for (final ProcessHandle process : ProcessHandle.current()
			.descendants()
			.filter(p -> !before.contains(p.pid()))
			.toList()) {
			// A destroyed process exits at once; a leaked one runs for 30 seconds
			process.onExit().get(10, TimeUnit.SECONDS);
		}
	}

	/**
	 * Runs a local command through runOsCommand, with the real process.
	 */
	private static OsCommandResult runLocally(final TelemetryManager telemetryManager, final String command)
		throws Exception {
		// A generous timeout: starting a shell can be slow on a loaded build machine
		return new OsCommandService().runOsCommand(command, telemetryManager, 30L, false, true, Map.of());
	}

	@Test
	void testLocalCommandSucceeds() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		assertEquals("test", runLocally(telemetryManager, COMMAND).getResult().strip());
		assertEquals(counts(OS_COMMAND_IDENTIFIER, 1, 0, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testLocalCommandWithEmptyOutputOrNonZeroExitCodeIsCompleted() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		// No output, exit code 0, then no output, exit code 3 (command data, like grep without match)
		assertEquals("", runLocally(telemetryManager, "exit 0").getResult());
		assertEquals("", runLocally(telemetryManager, "exit 3").getResult());
		assertEquals(counts(OS_COMMAND_IDENTIFIER, 2, 0, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testLocalCommandTimeoutIsRecordedAndDestroysTheProcess() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final Set<Long> before = descendants();
		final OsCommandService osCommandService = new OsCommandService();

		assertThrows(
			TimeoutException.class,
			() -> osCommandService.runOsCommand(LONG_COMMAND, telemetryManager, 1L, false, true, Map.of())
		);

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 0, 1, 0), telemetryManager.getRequestStats().snapshot());
		assertNoProcessLeftSince(before);
	}

	@Test
	void testLocalCommandInterruptedIsATimeoutAndDestroysTheProcess() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final Set<Long> before = descendants();
		final OsCommandService osCommandService = new OsCommandService();

		// The engine interrupts the job when its deadline expires, while the command runs
		final Thread caller = Thread.currentThread();
		final long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		final Thread deadline = new Thread(() -> {
			// Wait until the command has started a process
			while (before.containsAll(descendants()) && System.nanoTime() < giveUp) {
				Thread.onSpinWait();
			}
			caller.interrupt();
		});
		deadline.start();
		try {
			assertThrows(
				InterruptedException.class,
				() -> osCommandService.runOsCommand(LONG_COMMAND, telemetryManager, 20L, false, true, Map.of())
			);
		} finally {
			// Clear the interrupt flag first: join would throw on it
			Thread.interrupted();
			deadline.join(TimeUnit.SECONDS.toMillis(10));
		}

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 0, 1, 0), telemetryManager.getRequestStats().snapshot());
		assertNoProcessLeftSince(before);
	}

	@Test
	void testLocalCommandOnAnInterruptedThreadStartsNothing() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final OsCommandService osCommandService = spy(new OsCommandService());

		// An earlier deadline of the job interrupted the thread
		Thread.currentThread().interrupt();
		try {
			assertThrows(
				InterruptedException.class,
				() -> osCommandService.runOsCommand(LONG_COMMAND, telemetryManager, 20L, false, true, Map.of())
			);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}

		verify(osCommandService, never()).createProcessBuilder(any());
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testLocalCommandThatCannotStartIsFailed() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final OsCommandService osCommandService = spy(new OsCommandService());
		doReturn(new ProcessBuilder("metricshub-no-such-executable")).when(osCommandService).createProcessBuilder(any());

		assertThrows(
			IOException.class,
			() -> osCommandService.runOsCommand(COMMAND, telemetryManager, 5L, false, true, Map.of())
		);

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testLocalCommandNullResultIsFailedAndStillReturnsEmpty() throws Exception {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final OsCommandService osCommandService = spy(new OsCommandService());
		doReturn(null).when(osCommandService).runLocalCommand(anyString(), anyLong(), any());

		assertEquals("", osCommandService.runOsCommand(COMMAND, telemetryManager, 5L, false, true, Map.of()).getResult());

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testLocalCommandInvalidTimeoutRecordsNothing() {
		final TelemetryManager telemetryManager = localTelemetryManager();
		final OsCommandService osCommandService = new OsCommandService();

		// Rejected before the process starts
		assertThrows(
			IllegalArgumentException.class,
			() -> osCommandService.runOsCommand(COMMAND, telemetryManager, 0L, false, true, Map.of())
		);

		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testNullRequestStatsRecordNothing() throws Exception {
		assertEquals("out", OsCommandService.recordLocalCommand(null, () -> "out"));
		assertEquals("out", OsCommandService.recordSshCommand(null, () -> "out"));
		assertThrows(IOException.class, () -> OsCommandService.recordLocalCommand(null, () -> {
			throw new IOException("cannot start");
		}));
		assertThrows(ClientException.class, () -> OsCommandService.recordSshCommand(null, () -> {
			throw new ClientException("failed");
		}));
	}

	/**
	 * Mocks the SSH client the SSH command uses: connected, authenticated, and returning {@code commandResult}.
	 */
	private static SshClient sshClient(final SshClient.CommandResult commandResult) throws IOException {
		final SshClient sshClient = mock(SshClient.class);
		when(sshClient.authenticate(anyString(), any(char[].class))).thenReturn(true);
		when(sshClient.executeCommand(anyString(), anyInt())).thenReturn(commandResult);
		return sshClient;
	}

	/**
	 * @return the result of an execution that ran to its end
	 */
	private static SshClient.CommandResult executed(final String output, final Integer exitStatus) {
		final SshClient.CommandResult commandResult = new SshClient.CommandResult();
		commandResult.result = output;
		commandResult.exitStatus = exitStatus;
		return commandResult;
	}

	/**
	 * Runs an SSH command through runOsCommand with the given SSH client, the rest of the code path being real.
	 */
	private static String runRemotely(final TelemetryManager telemetryManager, final SshClient sshClient)
		throws Exception {
		try (
			MockedStatic<OsCommandRequestExecutor> executor = mockStatic(OsCommandRequestExecutor.class, CALLS_REAL_METHODS)
		) {
			executor.when(() -> OsCommandRequestExecutor.createSshClientInstance(anyString())).thenReturn(sshClient);
			return new OsCommandService().runOsCommand(COMMAND, telemetryManager, 5L, false, false, Map.of()).getResult();
		}
	}

	@Test
	void testSshCommandIsCompletedWhateverItsExitStatus() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);

		assertEquals("test", runRemotely(telemetryManager, sshClient(executed("test", 0))));
		// A non-zero exit status is command data, like grep without match
		assertEquals("", runRemotely(telemetryManager, sshClient(executed("", 1))));

		assertEquals(counts(SSH_IDENTIFIER, 2, 0, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshCommandExecutionTimeoutIsATimeout() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient.CommandResult timedOut = new SshClient.CommandResult();
		timedOut.success = false;
		timedOut.result = "Timeout (5 seconds)";
		final SshClient sshClient = sshClient(timedOut);

		final ClientException exception = assertThrows(
			ClientException.class,
			() -> runRemotely(telemetryManager, sshClient)
		);

		// Same message as before, the timeout is now a cause
		assertEquals(
			"Hostname resource-host - Command \"echo test\" failed with result Timeout (5 seconds).",
			exception.getMessage()
		);
		assertInstanceOf(TimeoutException.class, exception.getCause());
		assertEquals(counts(SSH_IDENTIFIER, 0, 1, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshConnectionTimeoutIsATimeout() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = mock(SshClient.class);
		final SocketTimeoutException timeout = new SocketTimeoutException(
			"The connect() operation on the socket timed out."
		);
		doThrow(timeout).when(sshClient).connect(anyInt(), anyInt());

		final ClientException exception = assertThrows(
			ClientException.class,
			() -> runRemotely(telemetryManager, sshClient)
		);

		// The caught exception is the cause, not its own (missing) cause
		assertSame(timeout, exception.getCause());
		assertEquals("Failed to run SSH command 'echo test' as user on resource-host.", exception.getMessage());
		assertEquals(counts(SSH_IDENTIFIER, 0, 1, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshConnectionErrorIsFailed() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = mock(SshClient.class);
		final IOException connectionError = new IOException(
			"There was a problem while connecting to resource-host:22",
			new UnknownHostException(RESOURCE_HOSTNAME)
		);
		doThrow(connectionError).when(sshClient).connect(anyInt(), anyInt());

		final ClientException exception = assertThrows(
			ClientException.class,
			() -> runRemotely(telemetryManager, sshClient)
		);

		// The root cause, as before
		assertSame(connectionError.getCause(), exception.getCause());
		assertEquals(counts(SSH_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshErrorWithAnErrorCauseIsAClientException() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = mock(SshClient.class);
		// The cause used to be cast to Exception: a ClassCastException
		doThrow(new IOException("broken", new StackOverflowError())).when(sshClient).connect(anyInt(), anyInt());

		final ClientException exception = assertThrows(
			ClientException.class,
			() -> runRemotely(telemetryManager, sshClient)
		);

		// An Error is no Exception cause: the caught exception is
		assertInstanceOf(IOException.class, exception.getCause());
		assertEquals(counts(SSH_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshConnectionTimeoutWrappedBySshLibraryIsATimeout() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = mock(SshClient.class);
		// sshlib wraps a connect timeout: the root cause is kept as the cause, as before
		final SocketTimeoutException timeout = new SocketTimeoutException("connect timed out");
		doThrow(new IOException("There was a problem while connecting to resource-host:22", timeout)).when(sshClient)
			.connect(anyInt(), anyInt());

		final ClientException exception = assertThrows(
			ClientException.class,
			() -> runRemotely(telemetryManager, sshClient)
		);

		assertSame(timeout, exception.getCause());
		assertEquals(counts(SSH_IDENTIFIER, 0, 1, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshAuthenticationFailureIsFailed() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = sshClient(executed("test", 0));
		when(sshClient.authenticate(anyString(), any(char[].class))).thenReturn(false);

		assertThrows(ClientException.class, () -> runRemotely(telemetryManager, sshClient));

		assertEquals(counts(SSH_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshCommandIsRecordedInTheResourceOfAnOverriddenHostname() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(SSH_HOSTNAME);
		final SshClient sshClient = sshClient(executed("test", 0));

		try (
			MockedStatic<OsCommandRequestExecutor> executor = mockStatic(OsCommandRequestExecutor.class, CALLS_REAL_METHODS)
		) {
			// Only the SSH hostname gets a client
			executor.when(() -> OsCommandRequestExecutor.createSshClientInstance(SSH_HOSTNAME)).thenReturn(sshClient);
			new OsCommandService().runOsCommand(COMMAND, telemetryManager, 5L, false, false, Map.of());
		}

		assertEquals(counts(SSH_IDENTIFIER, 1, 0, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testSshPermitWaitRecordsNothing() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final OsCommandService osCommandService = spy(new OsCommandService());
		doThrow(new ControlledSshException("no permit")).when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());

		assertThrows(
			ControlledSshException.class,
			() -> osCommandService.runOsCommand(COMMAND, telemetryManager, 5L, false, false, Map.of())
		);
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testSshPermitWaitInterruptedRecordsNothing() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(null);
		final SshClient sshClient = sshClient(executed("test", 0));

		// The semaphore wait throws at once on an interrupted thread: nothing is sent
		Thread.currentThread().interrupt();
		assertThrows(InterruptedException.class, () -> runRemotely(telemetryManager, sshClient));

		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	/**
	 * @return a telemetry manager whose SSH health check runs the local and the remote test commands
	 */
	private static TelemetryManager sshHealthCheckTelemetryManager(final boolean locally, final boolean remotely) {
		final TelemetryManager telemetryManager = remoteTelemetryManager(SSH_HOSTNAME);
		telemetryManager.getHostProperties().setMustCheckSshStatus(true);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(locally);
		telemetryManager.getHostProperties().setOsCommandExecutesRemotely(remotely);
		return telemetryManager;
	}

	@Test
	void testLocalHealthCheckIsRecorded() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final OsCommandExtension extension = new OsCommandExtension(osCommandService);
		final TelemetryManager telemetryManager = sshHealthCheckTelemetryManager(true, false);

		doReturn("test").when(osCommandService).runLocalCommand(anyString(), anyLong(), any());
		assertTrue(extension.checkProtocol(telemetryManager).get());

		doReturn(null).when(osCommandService).runLocalCommand(anyString(), anyLong(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		doThrow(new TimeoutException("timed out")).when(osCommandService).runLocalCommand(anyString(), anyLong(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 1, 1, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testLocalOsCommandHealthCheckIsRecorded() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final OsCommandExtension extension = new OsCommandExtension(osCommandService);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId("localhost")
					.hostname("localhost")
					.configurations(Map.of(OsCommandConfiguration.class, OsCommandConfiguration.builder().build()))
					.build()
			)
			.build();
		telemetryManager.getHostProperties().setLocalhost(true);

		doThrow(new IOException("cannot start")).when(osCommandService).runLocalCommand(anyString(), anyLong(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		assertEquals(counts(OS_COMMAND_IDENTIFIER, 0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testRemoteHealthCheckIsRecorded() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final OsCommandExtension extension = new OsCommandExtension(osCommandService);
		final TelemetryManager telemetryManager = sshHealthCheckTelemetryManager(false, true);

		doReturn("test").when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());
		assertTrue(extension.checkProtocol(telemetryManager).get());

		doThrow(new ClientException("timed out", new TimeoutException("Timeout (5 seconds)"))).when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		doThrow(new ClientException("refused")).when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		// No permit: nothing sent, nothing recorded, the check is still down
		doThrow(new ControlledSshException("no permit")).when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());
		assertFalse(extension.checkProtocol(telemetryManager).get());

		assertEquals(counts(SSH_IDENTIFIER, 1, 1, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testHealthCheckLocallyAndRemotelyRecordsBoth() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final OsCommandExtension extension = new OsCommandExtension(osCommandService);
		final TelemetryManager telemetryManager = sshHealthCheckTelemetryManager(true, true);

		doReturn("test").when(osCommandService).runLocalCommand(anyString(), anyLong(), any());
		doReturn("test").when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());
		assertTrue(extension.checkProtocol(telemetryManager).get());

		final RequestStats expected = new RequestStats();
		expected.record(OS_COMMAND_IDENTIFIER, RequestOutcome.COMPLETED);
		expected.record(SSH_IDENTIFIER, RequestOutcome.COMPLETED);
		assertEquals(expected.snapshot(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testExecuteQueryRecordsNothing() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final OsCommandExtension extension = new OsCommandExtension(osCommandService);
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("commandLine", new TextNode(COMMAND));
		doReturn("test").when(osCommandService)
			.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any(), any());

		try (MockedStatic<OsCommandService> service = mockStatic(OsCommandService.class, CALLS_REAL_METHODS)) {
			assertEquals(
				"test",
				extension.executeQuery(
					SshConfiguration.sshConfigurationBuilder().hostname(SSH_HOSTNAME).timeout(5L).build(),
					queryNode
				)
			);
			// The CLI and the MCP tools monitor no resource
			service.verify(() -> OsCommandService.recordSshCommand(any(), any()), never());
			service.verify(() -> OsCommandService.recordRequest(any(), any(), any()), never());
		}
	}
}
