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

import static org.sentrysoftware.metricshub.engine.common.helpers.StringHelper.protectCaseInsensitiveRegex;
import static org.springframework.util.Assert.isTrue;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.ssh.SshClient;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;

/**
 * Provides functionality to execute remote SSH commands, manage SSH authentication, and transfer files to remote hosts.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OsCommandRequestExecutor {

	private static final String SSH_FILE_MODE = "0700";
	private static final String SSH_REMOTE_DIRECTORY = "/var/tmp/";

	/**
	 * Use ssh-client in order to run ssh command.
	 *
	 * @param hostname         The hostname or IP address to connect to.
	 * @param username         The SSH username.
	 * @param password         The SSH password as a character array.
	 * @param keyFilePath      The path to the SSH key file.
	 * @param command          The SSH command to execute.
	 * @param timeout          The timeout for the command execution in seconds.
	 * @param port             The SSH port number.
	 * @param localFiles       List of local files to be transferred to the remote host.
	 * @param noPasswordCommand The command to execute without password.
	 * @return The result of the SSH command.
	 * @throws ClientException If an error occurs during the SSH command execution.
	 */
	@WithSpan("SSH")
	public static String runRemoteSshCommand(
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		@NonNull @SpanAttribute("ssh.username") final String username,
		final char[] password,
		@SpanAttribute("ssh.key_file_path") final File keyFilePath,
		final String command,
		@SpanAttribute("ssh.timeout") final long timeout,
		@SpanAttribute("ssh.port") final Integer port,
		@SpanAttribute("ssh.local_files") final List<File> localFiles,
		@SpanAttribute("ssh.command") final String noPasswordCommand
	) throws ClientException {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing Remote SSH command:\n- hostname: {}\n- username: {}\n- key-file-path: {}\n" + // NOSONAR
				"- command: {}\n- timeout: {} s\n- local-files: {}\n",
				hostname,
				username,
				keyFilePath,
				command,
				timeout,
				port,
				localFiles
			)
		);

		isTrue(command != null && !command.trim().isEmpty(), "Command cannot be null nor empty.");
		isTrue(timeout > 0, "Timeout cannot be negative nor zero.");
		final long timeoutInMilliseconds = timeout * 1000;

		final String updatedCommand = updateCommandWithLocalList(command, localFiles);

		final String noPasswordUpdatedCommand = noPasswordCommand == null
			? updatedCommand
			: updateCommandWithLocalList(noPasswordCommand, localFiles);

		// Create the collection that will store the paths of remote files that need to be removed
		final List<String> remoteFilePaths = new ArrayList<>();

		SshClient sshClient = null;
		try {
			// Create an SSH client instance
			sshClient = createSshClientInstance(hostname);

			// Connect to the SSH server
			sshClient.connect((int) timeoutInMilliseconds, port);

			if (password == null) {
				log.warn("Hostname {} - Password could not be read. Using an empty password instead.", hostname);
			}

			// Authenticate the SSH client
			authenticateSsh(sshClient, hostname, username, password, keyFilePath);

			if (localFiles != null && !localFiles.isEmpty()) {
				// copy all local files using SCP
				for (final File file : localFiles) {
					final String filename = file.getName();
					sshClient.scp(file.getAbsolutePath(), filename, SSH_REMOTE_DIRECTORY, SSH_FILE_MODE);

					// Add the remote file path to the list
					remoteFilePaths.add(SSH_REMOTE_DIRECTORY + filename);
				}
			}

			final long startTime = System.currentTimeMillis();

			final SshClient.CommandResult commandResult = sshClient.executeCommand(
				updatedCommand,
				(int) timeoutInMilliseconds
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			if (!commandResult.success) {
				final String message = String.format(
					"Hostname %s - Command \"%s\" failed with result %s.",
					hostname,
					noPasswordUpdatedCommand,
					commandResult.result
				);
				log.error(message);
				throw new ClientException(message);
			}

			String result = commandResult.result;

			LoggingHelper.trace(() ->
				log.trace(
					"Executed Remote SSH command:\n- Hostname: {}\n- Username: {}\n- Key-file-path: {}\n" + // NOSONAR
					"- Command: {}\n- Timeout: {} s\n- Local-files: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					keyFilePath,
					command,
					timeout,
					localFiles,
					result,
					responseTime
				)
			);
			return result;
		} catch (final ClientException e) {
			throw e;
		} catch (final Exception e) {
			final String message = String.format(
				"Failed to run SSH command '%s' as %s on %s.",
				noPasswordUpdatedCommand,
				username,
				hostname
			);
			log.error("Hostname {} - {}. Exception message: {}.", hostname, message, e.getMessage());
			throw new ClientException(message, (Exception) e.getCause());
		} finally {
			release(sshClient, remoteFilePaths, hostname, username);
		}
	}

	/**
	 * Removes all remote files that were copied to the remote host
	 * and disconnects the SSH client.
	 *
	 * @param sshClient       The SSH client to close before removing the copied files.
	 * @param remoteFilePaths The paths of remote files to be removed.
	 * @param hostname        The hostname or IP address used for logging.
	 * @param username        The username used for logging.
	 */
	private static void release(
		final SshClient sshClient,
		final List<String> remoteFilePaths,
		final String hostname,
		final String username
	) {
		if (sshClient != null) {
			removeCopiedRemoteFiles(sshClient, remoteFilePaths, hostname, username);

			log.debug("Hostname {} - Disconnecting SSH client.", hostname);
			sshClient.close();
		}
	}

	/**
	 * Removes all remote files that were copied to the remote host.
	 *
	 * @param sshClient       The SSH client to close before removing the copied files.
	 * @param remoteFilePaths The paths of remote files to be removed.
	 * @param hostname        The hostname or IP address used for logging.
	 * @param username        The username used for logging.
	 */
	private static void removeCopiedRemoteFiles(
		final SshClient sshClient,
		final List<String> remoteFilePaths,
		final String hostname,
		final String username
	) {
		if (remoteFilePaths.isEmpty()) {
			return;
		}

		// Removes all remote files
		log.debug("Hostname {} - Removing remote files {}.", hostname, remoteFilePaths);
		try {
			// Removes the specified files on the remote system
			sshClient.removeFile(remoteFilePaths.toArray(new String[remoteFilePaths.size()]));
		} catch (Exception e) {
			log.error(
				"Hostname {} - Failed to remove remote files {} as {} on {}. Exception message: {}.",
				hostname,
				remoteFilePaths,
				username,
				hostname,
				e.getMessage()
			);
			log.debug("Hostname {} - Exception: ", hostname, e);
		}
	}

	/**
	 * Authenticate SSH with:
	 * <ul>
	 * 	<li>username, privateKey and password first</li>
	 * 	<li>username and password</li>
	 * 	<li>username only</li>
	 * </ul>
	 *
	 * @param sshClient  The SSH client
	 * @param hostname   The hostname
	 * @param username   The username
	 * @param password   The password
	 * @param privateKey The private key file
	 * @throws ClientException If an error occurred.
	 */
	static void authenticateSsh(
		final SshClient sshClient,
		final String hostname,
		final String username,
		final char[] password,
		final File privateKey
	) throws ClientException {
		final boolean authenticated;
		try {
			if (privateKey != null) {
				authenticated = sshClient.authenticate(username, privateKey, password);
			} else if (password != null && password.length > 0) {
				authenticated = sshClient.authenticate(username, password);
			} else {
				authenticated = sshClient.authenticate(username);
			}
		} catch (final Exception e) {
			final String message = String.format(
				"Hostname %s - Authentication as %s has failed with %s.",
				hostname,
				username,
				privateKey != null ? privateKey.getAbsolutePath() : null
			);
			log.error("Hostname {} - {}. Exception : {}.", hostname, message, e.getMessage());
			throw new ClientException(message, e);
		}

		if (!authenticated) {
			final String message = String.format(
				"Hostname %s - Authentication as %s has failed with %s.",
				hostname,
				username,
				privateKey != null ? privateKey.getAbsolutePath() : null
			);
			log.error(message);
			throw new ClientException(message);
		}
	}

	/**
	 * Replace in the SSH command all the local files path with their remote path.
	 *
	 * @param command    The SSH command.
	 * @param localFiles The local files list.
	 * @return The updated command.
	 */
	static String updateCommandWithLocalList(final String command, final List<File> localFiles) {
		return localFiles == null || localFiles.isEmpty()
			? command
			: localFiles
				.stream()
				.reduce(
					command,
					(s, file) ->
						command.replaceAll(
							protectCaseInsensitiveRegex(file.getAbsolutePath()),
							SSH_REMOTE_DIRECTORY + file.getName()
						),
					(s1, s2) -> null
				);
	}

	/**
	 * Creates a new instance of the {@link SshClient}.
	 *
	 * @param hostname The hostname.
	 * @return A {@link SshClient} instance.
	 */
	public static SshClient createSshClientInstance(final String hostname) {
		return new SshClient(hostname, StandardCharsets.UTF_8);
	}
}
