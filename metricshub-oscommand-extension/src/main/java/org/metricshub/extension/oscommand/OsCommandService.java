package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_LOCK_TIMEOUT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.protectCaseInsensitiveRegex;
import static org.metricshub.engine.strategy.utils.OsCommandHelper.TEMP_FILE_CREATOR;
import static org.metricshub.engine.strategy.utils.OsCommandHelper.createOsCommandEmbeddedFiles;
import static org.metricshub.engine.strategy.utils.OsCommandHelper.replaceSudo;
import static org.springframework.util.Assert.isTrue;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ClientRuntimeException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.MacrosUpdater;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.strategy.utils.SudoInformation;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.SshSemaphoreFactory;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Service that handles OS commands, including local and remote execution.
 *
 * <p>Intended to be used as an instance so that it can be injected and mocked in tests.
 */
@NoArgsConstructor
public class OsCommandService {

	private static final String NEGATIVE_TIMEOUT = "timeout mustn't be negative nor zero.";

	private static final String[] LOCAL_SHELL_COMMAND = buildShellCommand();

	/**
	 * Run the given command on the localhost machine.
	 *
	 * @param command           The command to be executed.
	 * @param timeout           The timeout for the command execution in seconds.
	 * @param noPasswordCommand The command with the password masked (if present).
	 * @return The result of the local command execution.
	 * @throws InterruptedException When the thread is interrupted during execution.
	 * @throws IOException          When an I/O error occurs during command execution.
	 * @throws TimeoutException     When the command execution times out.
	 */
	@WithSpan("OS Command")
	public String runLocalCommand(
		@NonNull final String command,
		@SpanAttribute("OSCommand.timeout") final long timeout,
		@SpanAttribute("OSCommand.command") final String noPasswordCommand
	) throws InterruptedException, IOException, TimeoutException {
		isTrue(timeout > 0, NEGATIVE_TIMEOUT);

		final ProcessBuilder builder = createProcessBuilder(command);

		final Process process = builder.start();

		if (process == null) {
			throw new IllegalStateException("Local command Process is null.");
		}

		final ExecutorService executor = Executors.newSingleThreadExecutor();

		final Future<String> future = executor.submit(() -> {
			try (
				InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream());
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
			) {
				final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					stringJoiner.add(line);
				}

				process.waitFor();

				return stringJoiner.toString();
			}
		});

		try {
			return future.get(timeout, TimeUnit.SECONDS);
		} catch (final InterruptedException exception) {
			destroy(process);
			throw exception;
		} catch (final TimeoutException exception) {
			future.cancel(true);
			destroy(process);

			throw new TimeoutException(
				String.format(
					"Command \"%s\" execution has timed out after %d s",
					noPasswordCommand != null ? noPasswordCommand : command,
					timeout
				)
			);
		} catch (final ExecutionException exception) {
			if (exception.getCause() instanceof IOException ioException) {
				throw ioException;
			}
			return null;
		} finally {
			executor.shutdownNow();
		}
	}

	/**
	 * Forcibly destroys a local command process and its descendants (the commands started by the shell), so that
	 * neither the process nor the thread reading its output outlives a timeout or an interruption.
	 *
	 * @param process The process to destroy.
	 */
	private static void destroy(final Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	/**
	 * A call of {@link #runLocalCommand(String, long, String)}.
	 */
	@FunctionalInterface
	public interface LocalCommandCall {
		/**
		 * Runs the local command.
		 *
		 * @return The output of the command, {@code null} when its execution failed.
		 * @throws InterruptedException When the thread is interrupted during execution.
		 * @throws IOException          When the process cannot be started or its output cannot be read.
		 * @throws TimeoutException     When the command execution times out.
		 */
		String run() throws InterruptedException, IOException, TimeoutException;
	}

	/**
	 * A call of {@link #runSshCommand(String, String, SshConfiguration, long, List, String, DeviceKind)}.
	 */
	@FunctionalInterface
	public interface SshCommandCall {
		/**
		 * Runs the SSH command.
		 *
		 * @return The output of the command.
		 * @throws ClientException        When the SSH request fails.
		 * @throws InterruptedException   When the wait for an SSH permit is interrupted.
		 * @throws ControlledSshException When no SSH permit could be obtained.
		 */
		String run() throws ClientException, InterruptedException, ControlledSshException;
	}

	/**
	 * Runs a local command and records its outcome as an {@value OsCommandExtension#OS_COMMAND_IDENTIFIER} request:
	 * completed when it returns an output, whatever the exit code; failed when it returns {@code null} (an execution
	 * failure) or cannot start; timeout when it times out or is interrupted while it runs. A runtime exception (invalid
	 * argument) is thrown before the process starts and records nothing. A thread already interrupted (an expired
	 * deadline of its job) starts nothing and records nothing: an {@link InterruptedException} is thrown, the interrupt
	 * status is kept.
	 *
	 * @param requestStats The request statistics of the monitored resource, {@code null} to record nothing.
	 * @param call         The call of the local command.
	 * @return The result of the call.
	 * @throws InterruptedException When the thread is interrupted during execution.
	 * @throws IOException          When the process cannot be started or its output cannot be read.
	 * @throws TimeoutException     When the command execution times out.
	 */
	public static String recordLocalCommand(final RequestStats requestStats, final LocalCommandCall call)
		throws InterruptedException, IOException, TimeoutException {
		// A caller already interrupted (an expired deadline of its job) must not start a process it will not wait for
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("Interrupted before the command started");
		}
		final String result;
		try {
			result = call.run();
		} catch (final InterruptedException | IOException | TimeoutException e) {
			recordRequest(requestStats, OsCommandExtension.OS_COMMAND_IDENTIFIER, RequestOutcome.of(e));
			throw e;
		}
		recordRequest(
			requestStats,
			OsCommandExtension.OS_COMMAND_IDENTIFIER,
			result != null ? RequestOutcome.COMPLETED : RequestOutcome.FAILED
		);
		return result;
	}

	/**
	 * Runs an SSH command and records its outcome as an {@value OsCommandExtension#SSH_IDENTIFIER} request: completed
	 * when it returns, whatever the exit status (command data); timeout or failed for a {@link ClientException},
	 * according to its causes. The wait for an SSH permit ({@link ControlledSshException},
	 * {@link InterruptedException}) sends nothing and records nothing.
	 *
	 * @param requestStats The request statistics of the monitored resource, {@code null} to record nothing.
	 * @param call         The call of the SSH command.
	 * @return The result of the call.
	 * @throws ClientException        When the SSH request fails.
	 * @throws InterruptedException   When the wait for an SSH permit is interrupted.
	 * @throws ControlledSshException When no SSH permit could be obtained.
	 */
	public static String recordSshCommand(final RequestStats requestStats, final SshCommandCall call)
		throws ClientException, InterruptedException, ControlledSshException {
		final String result;
		try {
			result = call.run();
		} catch (final ClientException e) {
			recordRequest(requestStats, OsCommandExtension.SSH_IDENTIFIER, RequestOutcome.of(e));
			throw e;
		}
		recordRequest(requestStats, OsCommandExtension.SSH_IDENTIFIER, RequestOutcome.COMPLETED);
		return result;
	}

	/**
	 * Records the outcome of one request attempt, unless there is no monitored resource.
	 *
	 * @param requestStats  The request statistics of the monitored resource, {@code null} to record nothing.
	 * @param operationType The protocol label: {@value OsCommandExtension#SSH_IDENTIFIER} or
	 *                      {@value OsCommandExtension#OS_COMMAND_IDENTIFIER}.
	 * @param outcome       The terminal state of the attempt.
	 */
	public static void recordRequest(
		final RequestStats requestStats,
		final String operationType,
		final RequestOutcome outcome
	) {
		if (requestStats != null) {
			requestStats.record(operationType, outcome);
		}
	}

	/**
	 * Create a process builder for the given command. The start method of the
	 * builder should be called to execute the command.
	 *
	 * @param command The command to be executed.
	 * @return The process builder for the given command.
	 */
	protected ProcessBuilder createProcessBuilder(final String command) {
		return new ProcessBuilder().command(LOCAL_SHELL_COMMAND[0], LOCAL_SHELL_COMMAND[1], command);
	}

	/**
	 * Build the shell to be used for the local command execution based on the operating system.
	 *
	 * @return The shell command to be used.
	 */
	private static String[] buildShellCommand() {
		if (LocalOsHandler.isWindows()) {
			return new String[] { getComSpecEnvVar(), "/C" };
		} else {
			return new String[] { getShellEnvVar(), "-c" };
		}
	}

	/**
	 * Get the shell environment variable for Linux/Unix systems.
	 *
	 * @return The shell environment variable or /bin/sh if not found.
	 */
	private static String getShellEnvVar() {
		var shell = System.getenv("SHELL");
		if (shell == null || shell.isBlank()) {
			// List of common shells to check
			final String[] commonShells = {
				"/bin/bash",
				"/usr/bin/bash",
				"/bin/sh",
				"/usr/bin/sh",
				"/bin/zsh",
				"/usr/bin/zsh",
				"/bin/ksh",
				"/usr/bin/ksh" };

			// Find the first common shell that exists
			for (String s : commonShells) {
				if (new File(s).exists()) {
					shell = s;
					break;
				}
			}
			// Fallback if no common shell is found
			if (shell == null || shell.isBlank()) {
				shell = "/bin/sh"; // Minimal fallback
			}
		}
		return shell;
	}

	/**
	 * Get the ComSpec environment variable for Windows systems.
	 * @return The ComSpec environment variable or cmd.exe if not found.
	 */
	private static String getComSpecEnvVar() {
		var comSpec = System.getenv("ComSpec");
		if (comSpec == null || comSpec.isBlank()) {
			comSpec = "cmd.exe";
		}
		return comSpec;
	}

	/**
	 * Run an SSH command, checking if it can be executed on localhost or remotely.
	 *
	 * @param command           The SSH command to be executed.
	 * @param hostname          The hostname of the remote machine.
	 * @param sshConfiguration  The SSH configuration including username, password, and private key.
	 * @param timeout           The timeout for the command execution in seconds.
	 * @param localFiles        List of local files required for the remote execution.
	 * @param noPasswordCommand The command with password masked (if present).
	 * @return The result of the SSH command execution.
	 * @throws ClientException          When an error occurs during Client execution, an interrupt of the running
	 *                                  command included.
	 * @throws InterruptedException    When the thread is interrupted before the command started.
	 * @throws ControlledSshException   When there's an issue with controlled SSH execution.
	 */
	public String runSshCommand(
		@NonNull final String command,
		@NonNull final String hostname,
		@NonNull final SshConfiguration sshConfiguration,
		final long timeout,
		final List<File> localFiles,
		final String noPasswordCommand,
		final DeviceKind hostType
	) throws ClientException, InterruptedException, ControlledSshException {
		isTrue(timeout > 0, NEGATIVE_TIMEOUT);
		// Set once the permit is obtained: an interrupt that follows cuts short a request that was sent
		final AtomicBoolean sent = new AtomicBoolean();
		try {
			return runControlledSshCommand(() -> {
				sent.set(true);
				try {
					return OsCommandRequestExecutor.runRemoteSshCommand(
						hostname,
						sshConfiguration.getUsername(),
						sshConfiguration.getPassword(),
						sshConfiguration.getPrivateKey() == null ? null : new File(sshConfiguration.getPrivateKey()),
						command,
						timeout,
						sshConfiguration.getPort(),
						localFiles,
						noPasswordCommand,
						hostType
					);
				} catch (ClientException e) {
					throw new ClientRuntimeException(e);
				}
			}, hostname, DEFAULT_LOCK_TIMEOUT);
		} catch (final ClientRuntimeException e) {
			throw (ClientException) e.getCause();
		} catch (final InterruptedException e) {
			if (sent.get()) {
				// The request was cut short (a timeout), not the wait for a permit; the interrupt status is kept
				throw new ClientException(String.format("Hostname %s - SSH command interrupted.", hostname), e);
			}
			throw e;
		}
	}

	/**
	 * Restores the interrupt of the current thread when the given exception, or one of its causes, is an
	 * {@link InterruptedException}: the job deadline expired or the job was cancelled, and the engine stops the job
	 * only if the thread is still interrupted. Throwing an {@link InterruptedException} clears the interrupt.
	 *
	 * @param exception The exception caught by a source or criterion processor
	 */
	static void restoreInterrupt(final Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/**
	 * Run a controlled SSH command, checking if a permit can be obtained from the semaphore before running the command.
	 *
	 * @param <T>         The type of result expected from the executable.
	 * @param executable  The executable to be run.
	 * @param hostname    The hostname of the remote machine.
	 * @param timeout     The timeout for obtaining a semaphore permit in seconds.
	 * @return The result of the executable.
	 * @throws InterruptedException    When the thread is interrupted before or during execution: the caller returns at
	 *                                 once, its interrupt status kept.
	 * @throws ControlledSshException   When there's an issue with controlled SSH execution.
	 */
	<T> T runControlledSshCommand(Supplier<T> executable, String hostname, int timeout) throws InterruptedException,
		ControlledSshException {
		final Semaphore semaphore = SshSemaphoreFactory.getInstance().createOrGetSempahore(hostname);

		// The permit is taken and released by the thread doing the SSH I/O, which sshlib keeps on an interrupt: an
		// interrupted caller returns at once, the session ends on its own and holds its permit until then
		try {
			return ThreadHelper.executeInterruptibly(() -> {
				if (!semaphore.tryAcquire(timeout, TimeUnit.SECONDS)) {
					throw new ControlledSshException(
						String.format("Failed to run SSH command on %s. Timed out trying to get ssh semaphore permit.", hostname)
					);
				}
				try {
					return executable.get();
				} finally {
					semaphore.release();
				}
			});
		} catch (InterruptedException e) {
			// The job deadline expired or the job was cancelled: keep the interrupt so that the engine stops the job
			Thread.currentThread().interrupt();
			throw e;
		} catch (ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof ControlledSshException controlledSshException) {
				throw controlledSshException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			// A RuntimeException (ClientRuntimeException), the only other exception of a Supplier
			throw (RuntimeException) cause;
		}
	}

	/**
	 * Get the timeout from:
	 * <ul>
	 * 	<li>First, the command timeout</li>
	 * 	<li>Then, the command configuration timeout</li>
	 * 	<li>Then, the protocol configuration timeout</li>
	 * 	<li>Finally, the default timeout</li>
	 * </ul>
	 *
	 * @param commandTimeout         The OS command timeout in seconds.
	 * @param osCommandConfiguration The configuration specific to OS command execution.
	 * @param sshConfiguration          The general {@link SshConfiguration} configuration.
	 * @param defaultTimeout         The default timeout in seconds.
	 * @return The timeout in seconds.
	 */
	public long getTimeout(
		final Long commandTimeout,
		final OsCommandConfiguration osCommandConfiguration,
		final IConfiguration sshConfiguration,
		final long defaultTimeout
	) {
		if (commandTimeout != null) {
			return commandTimeout.intValue();
		}

		if (osCommandConfiguration != null && osCommandConfiguration.getTimeout() != null) {
			return osCommandConfiguration.getTimeout().intValue();
		}

		if (sshConfiguration == null) {
			return defaultTimeout;
		}

		final Long timeout = ((SshConfiguration) sshConfiguration).getTimeout();

		return timeout != null ? timeout : defaultTimeout;
	}

	/**
	 * Retrieves the username associated with an SSH configuration.
	 *
	 * @param configuration The configuration object of type {@link IConfiguration}.
	 * @return An {@link Optional} containing the username if the configuration is of type {@link SshConfiguration} and has a username set; otherwise, an empty optional.
	 */
	public Optional<String> getUsername(final IConfiguration configuration) {
		if (configuration == null) {
			return Optional.empty();
		}
		if (configuration instanceof SshConfiguration sshConfiguration) {
			return Optional.ofNullable(sshConfiguration.getUsername());
		}
		return Optional.empty();
	}

	/**
	 * Retrieves the password associated with an SSH configuration.
	 *
	 * @param protocolConfiguration The configuration object of type {@link IConfiguration}.
	 * @return An {@link Optional} containing the password as a char array if the configuration is of type {@link SshConfiguration} and has a password set; otherwise, an empty optional.
	 */
	public Optional<char[]> getPassword(final IConfiguration protocolConfiguration) {
		if (protocolConfiguration == null) {
			return Optional.empty();
		}
		if (protocolConfiguration instanceof SshConfiguration sshConfiguration) {
			return Optional.ofNullable(sshConfiguration.getPassword());
		}
		return Optional.empty();
	}

	/**
	 * Run the OS Command on:
	 * <ul>
	 *   <li>Local (use java Process)</li>
	 *   <li>Remote Windows (use SSH)</li>
	 *   <li>Remote Linux (use SSH)</li>
	 * </ul>
	 * <p>It replaces Host name, User name, Password, Sudo, Embedded files macros in the command line.</p>
	 * <p>If necessary, it creates embedded files and deletes them after the command execution.</p>
	 *
	 * @param commandLine            The command Line. (mandatory)
	 * @param telemetryManager       The engine configuration and host properties. (mandatory)
	 * @param commandTimeout         The OS command parameter for the timeout.
	 * @param isExecuteLocally       The OS command parameter to indicate if the command should be executed locally.
	 * @param isLocalhost            The parameter in Host Monitoring to indicate if the command is executed locally.
	 * @param connectorEmbeddedFiles All the embedded files map defined in the Connector instance.
	 * @return The command execution return and the command with password masked (if present).
	 * @throws IOException                   When an I/O error occurred on local command execution or embedded file creation.
	 * @throws ClientException               When an error occurred on remote execution.
	 * @throws InterruptedException          When the local command execution is interrupted.
	 * @throws TimeoutException              When the local command execution ends in timeout.
	 * @throws NoCredentialProvidedException When there's no user provided for a remote command.
	 * @throws ControlledSshException        When an error occurs during controlled SSH execution.
	 */
	public OsCommandResult runOsCommand(
		@NonNull final String commandLine,
		@NonNull final TelemetryManager telemetryManager,
		final Long commandTimeout,
		final boolean isExecuteLocally,
		final boolean isLocalhost,
		@NonNull final Map<Integer, EmbeddedFile> connectorEmbeddedFiles
	) throws IOException, ClientException, InterruptedException, TimeoutException, NoCredentialProvidedException,
		ControlledSshException {
		final IConfiguration sshConfiguration = telemetryManager.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		final Optional<String> maybeUsername = getUsername(sshConfiguration);

		// If remote command and no username
		if ((maybeUsername.isEmpty() || maybeUsername.get().isBlank()) && !isExecuteLocally && !isLocalhost) {
			throw new NoCredentialProvidedException();
		}

		final Optional<char[]> maybePassword = getPassword(sshConfiguration);

		final OsCommandConfiguration osCommandConfiguration = (OsCommandConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(OsCommandConfiguration.class);

		// A command run over SSH takes the sudo settings of ssh when they enable sudo, otherwise those of osCommand,
		// which used to apply to every command
		final OsCommandConfiguration sudoConfiguration = !isLocalhost &&
			!isExecuteLocally &&
			sshConfiguration instanceof SshConfiguration ssh &&
			ssh.isUseSudo() ? ssh : osCommandConfiguration;

		SudoInformation sudoInformation = null;
		if (sudoConfiguration != null) {
			sudoInformation = new SudoInformation(
				sudoConfiguration.isUseSudo(),
				sudoConfiguration.getUseSudoCommands(),
				sudoConfiguration.getSudoCommand()
			);
		}

		// Embedded files are written with the line endings of the system that executes them
		final boolean isWindows = isLocalhost || isExecuteLocally
			? LocalOsHandler.isWindows()
			: DeviceKind.WINDOWS.equals(telemetryManager.getHostConfiguration().getHostType());

		final Map<String, File> embeddedTempFiles = createOsCommandEmbeddedFiles(
			commandLine,
			sudoInformation,
			EmbeddedFileHelper.findEmbeddedFiles(commandLine, connectorEmbeddedFiles),
			TEMP_FILE_CREATOR,
			isWindows ? WINDOWS_NEW_LINE : NEW_LINE
		);

		// Retrieve the hostname from the configurations, otherwise from the telemetryManager.
		final String hostname = telemetryManager.getHostname(List.of(SshConfiguration.class, OsCommandConfiguration.class));

		// Replace the macros by their corresponding values
		final String updatedCommand = MacrosUpdater.update(
			commandLine,
			maybeUsername.orElse(null),
			maybePassword.orElse(null),
			null,
			hostname,
			false
		);
		final String updatedCommandNoPassword = MacrosUpdater.update(
			commandLine,
			maybeUsername.orElse(null),
			maybePassword.orElse(null),
			null,
			hostname,
			true
		);
		final String updatedSudoCommand = replaceSudo(updatedCommand, sudoInformation);
		final String updatedSudoCommandNoPassword = replaceSudo(updatedCommandNoPassword, sudoInformation);

		final String command = embeddedTempFiles.entrySet()
			.stream()
			.reduce(
				updatedSudoCommand,
				(s, entry) -> s.replaceAll(
					protectCaseInsensitiveRegex(entry.getKey()),
					Matcher.quoteReplacement(entry.getValue().getAbsolutePath())
				),
				(_, _) -> null
			);

		final String commandNoPassword = embeddedTempFiles.entrySet()
			.stream()
			.reduce(
				updatedSudoCommandNoPassword,
				(s, entry) -> s.replaceAll(
					protectCaseInsensitiveRegex(entry.getKey()),
					Matcher.quoteReplacement(entry.getValue().getAbsolutePath())
				),
				(_, _) -> null
			);

		try {
			final long timeout = getTimeout(
				commandTimeout,
				osCommandConfiguration,
				sshConfiguration,
				telemetryManager.getHostConfiguration().getStrategyTimeout()
			);

			final String commandResult;

			// Case local execution or command intended for a remote host but executed locally
			final RequestStats requestStats = telemetryManager.getRequestStats();
			if (isLocalhost || isExecuteLocally) {
				final String localCommandResult = recordLocalCommand(
					requestStats,
					() -> runLocalCommand(command, timeout, commandNoPassword)
				);
				commandResult = localCommandResult != null ? localCommandResult : EMPTY;
			} else {
				// Case others (Linux) Remote
				commandResult = recordSshCommand(
					requestStats,
					() -> runSshCommand(
						command,
						hostname,
						(SshConfiguration) sshConfiguration,
						timeout,
						new ArrayList<>(embeddedTempFiles.values()),
						commandNoPassword,
						telemetryManager.getHostConfiguration().getHostType()
					)
				);
			}

			// Record the OS command exchange if recording is enabled.
			final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
			if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
				OsCommandRecorder.getInstance(recordOutputDirectory).record(commandLine, commandResult);
			}

			return new OsCommandResult(commandResult, commandNoPassword);
		} finally {
			//noinspection ResultOfMethodCallIgnored
			embeddedTempFiles.values().forEach(File::delete);
		}
	}

	/**
	 * This class is used to manage exceptions that can be thrown by the functional interface
	 * implementations used to create embedded temporary files.
	 */
	static class TempFileCreationException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		TempFileCreationException(final IOException cause) {
			super(cause);
		}
	}
}
