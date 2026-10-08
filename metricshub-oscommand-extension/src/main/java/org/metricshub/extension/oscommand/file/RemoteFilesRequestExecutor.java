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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.FileOperations;
import org.metricshub.extension.oscommand.OsCommandRequestExecutor;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.ssh.SshClient;
import org.metricshub.ssh.SshClient.FileEntry;

/**
 * Remote file access over the SFTP subsystem of an SSH connection: sizes, byte ranges and whole files are read through
 * ssh-java, and path patterns are resolved with SFTP directory listings, without running any command on the host.
 * Windows paths ({@code C:\logs\app.log}, {@code \\server\share\app.log}) are translated to the form of the OpenSSH
 * SFTP subsystem ({@code /C:/logs/app.log}, {@code //server/share/app.log}). One instance serves a whole file source
 * poll; {@link #close()} releases the connection.
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteFilesRequestExecutor implements FileOperations {

	// A UNC path in the form of the Windows OpenSSH SFTP subsystem: //server/share
	private static final String UNC_SFTP_PREFIX = "//";

	@NonNull
	private final SshClient sshClient;

	@NonNull
	private final SshConfiguration sshConfiguration;

	private final DeviceKind deviceKind;

	// Files listed by resolve(), by absolute path: their size spares an SFTP stat in getFileSize()
	private final Map<String, FileEntry> listedFiles = new HashMap<>();

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
	 * Resolves a path pattern into the absolute paths of the regular files matching it, with SFTP listings: each
	 * wildcard directory segment lists the subdirectories of the directories matched so far, and the last segment lists
	 * their files. Symbolic links are followed. {@code *} and {@code ?} match within a single segment; on a Unix host,
	 * names are case-sensitive and a wildcard directory segment does not match dot-prefixed directories, like a shell
	 * glob. A matched directory that cannot be listed is skipped.
	 *
	 * @param pattern the parsed path pattern
	 * @return the absolute paths of the matching files, empty when nothing matches
	 * @throws IOException when the root of the pattern cannot be listed
	 */
	public Set<String> resolve(final PathPattern pattern) throws IOException {
		final Set<String> resolved = new HashSet<>();
		collectMatchingFiles(toSftpPath(pattern.root()), pattern.segments(), 0, resolved);
		return resolved;
	}

	/**
	 * Matches one segment of a path pattern in {@code directory}, recursing into the matching directories and
	 * collecting the matching files on the last segment.
	 *
	 * @param directory the SFTP path of the directory to list
	 * @param segments  all pattern segments
	 * @param index     index of the segment to match in {@code directory}
	 * @param resolved  accumulator of the resolved absolute file paths
	 * @throws IOException when {@code directory} cannot be listed
	 */
	private void collectMatchingFiles(
		final String directory,
		final List<String> segments,
		final int index,
		final Set<String> resolved
	) throws IOException {
		final String segment = segments.get(index);

		if (index == segments.size() - 1) {
			final boolean literal = !FileHelper.containsWildcard(segment);
			for (final FileEntry entry : sshClient.listFiles(directory, nameRegex(segment, false), false)) {
				// A literal file name keeps its configured case, which a Windows listing may not report
				final String path = fromSftpPath(literal ? join(directory, segment) : entry.path);
				listedFiles.put(path, entry);
				resolved.add(path);
			}
			return;
		}

		// A literal segment needs no listing: a missing directory fails when it is listed itself
		final List<String> subdirectories = FileHelper.containsWildcard(segment)
			? sshClient.listSubdirectories(directory, nameRegex(segment, true))
			: List.of(join(directory, segment));

		for (final String subdirectory : subdirectories) {
			try {
				collectMatchingFiles(subdirectory, segments, index + 1, resolved);
			} catch (IOException e) {
				// One matched directory cannot be listed: skip it and keep scanning its siblings
				log.debug(
					"Hostname {} - Unable to scan directory {}: {}",
					sshConfiguration.getHostname(),
					subdirectory,
					e.getMessage()
				);
			}
		}
	}

	/**
	 * Converts a path segment, where only {@code *} and {@code ?} are wildcards, into the regular expression ssh-java
	 * matches entry names with ({@code Matcher.find()}, case-insensitive): anchored to the whole name, and
	 * case-sensitive on a Unix host. On a Unix host, a directory segment that does not start with a dot does not match
	 * dot-prefixed names, like a shell glob; a file name segment does, like {@code find -name}.
	 *
	 * @param segment   the path segment
	 * @param directory whether the segment designates directories (any segment but the last)
	 * @return the regular expression
	 */
	String nameRegex(final String segment, final boolean directory) {
		final boolean windows = isWindows();
		final StringBuilder regex = new StringBuilder(windows ? "(?siu)\\A" : "(?s-i)\\A");
		if (directory && !windows && !segment.startsWith(".")) {
			regex.append("(?!\\.)");
		}
		// Code points, not chars: a character outside the BMP must be quoted whole
		segment
			.codePoints()
			.forEach(c -> {
				switch (c) {
					case '*' -> regex.append(".*");
					case '?' -> regex.append('.');
					default -> regex.append(Pattern.quote(Character.toString(c)));
				}
			});
		return regex.append("\\z").toString();
	}

	/**
	 * Appends a name to an SFTP directory path.
	 *
	 * @param directory the SFTP directory path
	 * @param name      the name of an entry of the directory
	 * @return the SFTP path of the entry
	 */
	private static String join(final String directory, final String name) {
		return directory.endsWith(FileHelper.SLASH) ? directory + name : directory + FileHelper.SLASH + name;
	}

	/**
	 * @return whether the remote host runs Windows, whose OpenSSH SFTP subsystem designates {@code C:\logs} as
	 * {@code /C:/logs}
	 */
	private boolean isWindows() {
		return DeviceKind.WINDOWS.equals(deviceKind);
	}

	/**
	 * Converts an absolute path of the remote host into the form of its SFTP subsystem.
	 *
	 * @param path the absolute path, {@code C:\logs\app.log} or {@code \\server\share\app.log} on Windows
	 * @return the SFTP path, {@code /C:/logs/app.log} or {@code //server/share/app.log} on Windows; the path itself
	 * otherwise
	 */
	String toSftpPath(final String path) {
		if (!isWindows()) {
			return path;
		}
		final String sftpPath = path.replace(FileHelper.BACKSLASH, FileHelper.SLASH);
		return sftpPath.startsWith(UNC_SFTP_PREFIX) ? sftpPath : FileHelper.SLASH + sftpPath;
	}

	/**
	 * Converts a path of the SFTP subsystem into the absolute path of the remote host, the reverse of
	 * {@link #toSftpPath(String)}.
	 *
	 * @param sftpPath the SFTP path
	 * @return the absolute path of the remote host
	 */
	String fromSftpPath(final String sftpPath) {
		if (!isWindows()) {
			return sftpPath;
		}
		final String path = sftpPath.startsWith(UNC_SFTP_PREFIX) ? sftpPath : sftpPath.substring(1);
		return path.replace(FileHelper.SLASH, FileHelper.BACKSLASH);
	}

	@Override
	public Long getFileSize(final String path) {
		final FileEntry listed = listedFiles.get(path);
		if (listed != null) {
			return listed.size;
		}
		try {
			return sshClient.fileSize(toSftpPath(path));
		} catch (Exception e) {
			final String hostname = sshConfiguration.getHostname();
			log.info("Hostname {} - Unable to get \"{}\" file size: {}", hostname, path, e.getMessage());
			log.debug("Hostname {} - An error has occurred when reading the file size of {}: {}", hostname, path, e);
			return null;
		}
	}

	@Override
	public String readFromOffset(final String path, final Long offset, final Integer length) throws IOException {
		return sshClient.readFile(toSftpPath(path), offset, length);
	}

	@Override
	public String readFileContent(final String path) throws IOException {
		return sshClient.readFile(toSftpPath(path), null, null);
	}

	@Override
	public void close() {
		sshClient.close();
	}
}
