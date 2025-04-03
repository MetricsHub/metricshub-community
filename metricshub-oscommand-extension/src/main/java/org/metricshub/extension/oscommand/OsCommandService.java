package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_LOCK_TIMEOUT;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.sentrysoftware.metricshub.engine.common.helpers.StringHelper.protectCaseInsensitiveRegex;
import static org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandHelper.TEMP_FILE_CREATOR;
import static org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandHelper.createOsCommandEmbeddedFiles;
import static org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandHelper.replaceSudo;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.exception.ClientRuntimeException;
import org.sentrysoftware.metricshub.engine.common.exception.ControlledSshException;
import org.sentrysoftware.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.sentrysoftware.metricshub.engine.common.helpers.LocalOsHandler;
import org.sentrysoftware.metricshub.engine.common.helpers.MacrosUpdater;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.EmbeddedFile;
import org.sentrysoftware.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.SudoInformation;
import org.sentrysoftware.metricshub.engine.telemetry.SshSemaphoreFactory;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Os Command Service that handles OS commands, including local and remote execution.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
	public static String runLocalCommand(
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
		} catch (final TimeoutException exception) {
			future.cancel(true);

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
	 * Create a process builder for the given command. The start method of the
	 * builder should be called to execute the command.
	 *
	 * @param command The command to be executed.
	 * @return The process builder for the given command.
	 */
	static ProcessBuilder createProcessBuilder(final String command) {
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
				"/usr/bin/ksh"
			};

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
	 * @throws ClientException          When an error occurs during Client execution.
	 * @throws InterruptedException    When the thread is interrupted during execution.
	 * @throws ControlledSshException   When there's an issue with controlled SSH execution.
	 */
	public static String runSshCommand(
		@NonNull final String command,
		@NonNull final String hostname,
		@NonNull final SshConfiguration sshConfiguration,
		final long timeout,
		final List<File> localFiles,
		final String noPasswordCommand
	) throws ClientException, InterruptedException, ControlledSshException {
		isTrue(timeout > 0, NEGATIVE_TIMEOUT);
		try {
			return runControlledSshCommand(
				() -> {
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
							noPasswordCommand
						);
					} catch (ClientException e) {
						throw new ClientRuntimeException(e);
					}
				},
				hostname,
				DEFAULT_LOCK_TIMEOUT
			);
		} catch (final ClientRuntimeException e) {
			throw (ClientException) e.getCause();
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
	 * @throws InterruptedException    When the thread is interrupted during execution.
	 * @throws ControlledSshException   When there's an issue with controlled SSH execution.
	 */
	static <T> T runControlledSshCommand(Supplier<T> executable, String hostname, int timeout)
		throws InterruptedException, ControlledSshException {
		final Semaphore semaphore = SshSemaphoreFactory.getInstance().createOrGetSempahore(hostname);

		try {
			if (semaphore.tryAcquire(timeout, TimeUnit.SECONDS)) {
				return executable.get();
			}

			final String message = String.format(
				"Failed to run SSH command on %s. Timed out trying to get ssh semaphore permit.",
				hostname
			);

			throw new ControlledSshException(message);
		} finally {
			semaphore.release();
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
	public static long getTimeout(
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
	public static Optional<String> getUsername(final IConfiguration configuration) {
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
	public static Optional<char[]> getPassword(final IConfiguration protocolConfiguration) {
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
	public static OsCommandResult runOsCommand(
		@NonNull final String commandLine,
		@NonNull final TelemetryManager telemetryManager,
		final Long commandTimeout,
		final boolean isExecuteLocally,
		final boolean isLocalhost,
		@NonNull final Map<Integer, EmbeddedFile> connectorEmbeddedFiles
	)
		throws IOException, ClientException, InterruptedException, TimeoutException, NoCredentialProvidedException, ControlledSshException {
		final IConfiguration sshConfiguration = telemetryManager
			.getHostConfiguration()
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

		SudoInformation sudoInformation = null;
		if (osCommandConfiguration != null) {
			sudoInformation =
				new SudoInformation(
					osCommandConfiguration.isUseSudo(),
					osCommandConfiguration.getUseSudoCommands(),
					osCommandConfiguration.getSudoCommand()
				);
		}

		final Map<String, File> embeddedTempFiles = createOsCommandEmbeddedFiles(
			commandLine,
			sudoInformation,
			EmbeddedFileHelper.findEmbeddedFiles(commandLine, connectorEmbeddedFiles),
			TEMP_FILE_CREATOR
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

		final String command = embeddedTempFiles
			.entrySet()
			.stream()
			.reduce(
				updatedSudoCommand,
				(s, entry) ->
					s.replaceAll(
						protectCaseInsensitiveRegex(entry.getKey()),
						Matcher.quoteReplacement(entry.getValue().getAbsolutePath())
					),
				(s1, s2) -> null
			);

		final String commandNoPassword = embeddedTempFiles
			.entrySet()
			.stream()
			.reduce(
				updatedSudoCommandNoPassword,
				(s, entry) ->
					s.replaceAll(
						protectCaseInsensitiveRegex(entry.getKey()),
						Matcher.quoteReplacement(entry.getValue().getAbsolutePath())
					),
				(s1, s2) -> null
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
			if (isLocalhost || isExecuteLocally) {
				final String localCommandResult = runLocalCommand(command, timeout, commandNoPassword);
				commandResult = localCommandResult != null ? localCommandResult : EMPTY;
			} else {
				// Case others (Linux) Remote
				commandResult =
					runSshCommand(
						command,
						hostname,
						(SshConfiguration) sshConfiguration,
						timeout,
						new ArrayList<>(embeddedTempFiles.values()),
						commandNoPassword
					);
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
