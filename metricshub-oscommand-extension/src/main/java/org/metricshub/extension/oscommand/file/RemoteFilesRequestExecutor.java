package org.metricshub.extension.oscommand.file;

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

import java.io.File;
import java.io.IOException;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.oscommand.OsCommandRequestExecutor;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.ssh.SshClient;

/**
 * Executes remote file operations via SSH connection.
 * Handles SSH client connection, authentication, and file reading operations.
 * Manages the lifecycle of SSH client resources.
 */
@Data
@Slf4j
@RequiredArgsConstructor
public class RemoteFilesRequestExecutor {

	@NonNull
	SshClient sshClient;

	@NonNull
	SshConfiguration sshConfiguration;

	/**
	 * Establishes SSH connection to the remote host.
	 *
	 * @return true if connection succeeds, false otherwise
	 */
	public boolean connectSshClient() {
		try {
			sshClient.connect(sshConfiguration.getTimeout().intValue() * 1000, sshConfiguration.getPort());
			return true;
		} catch (IOException e) {
			final String hostname = sshConfiguration.getHostname();
			log.info("Hostname {} - An error has occurred when connecting SSH client: {}", hostname, e.getMessage());
			log.debug("Hostname {} - An error has occurred when connecting SSH client: {}", hostname, e);
			return false;
		}
	}

	/**
	 * Authenticates the SSH client with the remote host using configured credentials.
	 * Supports both password and private key authentication.
	 *
	 * @return true if authentication succeeds, false otherwise
	 */
	public boolean authenticateSshClient() {
		final String privateKey = sshConfiguration.getPrivateKey();
		final String hostname = sshConfiguration.getHostname();
		final String username = sshConfiguration.getUsername();
		try {
			OsCommandRequestExecutor.authenticateSsh(
				sshClient,
				sshConfiguration.getHostname(),
				username,
				sshConfiguration.getPassword(),
				privateKey == null ? null : new File(privateKey)
			);
			return true;
		} catch (Exception e) {
			log.info("Hostname {} - Authentication as {} has failed. Message: {}", hostname, username, e.getMessage());
			log.debug("Hostname {} - Authentication as {} has failed. Exception: {}", hostname, username, e);
			return false;
		}
	}

	/**
	 * Retrieves the size of a remote file in bytes.
	 *
	 * @param path The absolute path to the remote file
	 * @return The file size in bytes
	 * @throws Exception If an error occurs during file size retrieval
	 */
	public Long getRemoteFileSize(final String path) throws Exception {
		return Long.valueOf(sshClient.fileSize(path));
	}

	/**
	 * Reads content from a remote file starting at a specified offset.
	 * If offset and length are null, reads the entire file content.
	 *
	 * @param path The absolute path to the remote file
	 * @param offset The starting position (in bytes) to read from, or null to read from beginning
	 * @param length The maximum number of bytes to read, or null to read until end of file
	 * @return The content read from the file as a String
	 * @throws IOException If an error occurs during file reading
	 */
	public String readRemoteFileOffsetContent(final String path, final Long offset, final Integer length)
		throws IOException {
		return sshClient.readFile(path, offset, length);
	}

	/**
	 * Closes the SSH client connection and releases associated resources.
	 * Should be called when file operations are complete to prevent resource leaks.
	 */
	public void closeSshClient() {
		sshClient.close();
	}
}
