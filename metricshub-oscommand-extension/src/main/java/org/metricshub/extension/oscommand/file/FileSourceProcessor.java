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

import static org.metricshub.engine.connector.model.monitor.task.source.FileSource.UNLIMITED_SIZE_PER_POLL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.FileOperations;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.metricshub.engine.strategy.source.FileSourceProcessingResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.ssh.SshClient;

/**
 * Processor for file sources that handles both local and remote file reading operations.
 * Supports incremental file reading using cursors to track file position.
 */
@Slf4j
@NoArgsConstructor
public class FileSourceProcessor {

	// PowerShell command template for resolving file paths on Windows.
	public static final String RESOLVE_WINDOWS_FILES_COMMAND =
		"PowerShell.exe -ExecutionPolicy Bypass -Command \"Get-ChildItem -Path \\\"%s\\\" -File -Filter \\\"%s\\\" | ForEach-Object { $_.FullName }\"";

	// Linux find command template for resolving file paths.
	public static final String RESOLVE_LINUX_FILES_COMMAND = "find -L \"%s\" -maxdepth 1 -type f -name \"%s\" -print";

	// Line break sequence used in Windows (CRLF).
	public static final String WINDOWS_LINE_BREAK_SEQUENCE = "\r\n";

	// Line break sequence used in Linux/Unix (LF).
	public static final String LINUX_LINE_BREAK_SEQUENCE = "\n";

	/**
	 * Processes a file source by reading files either locally or remotely based on the host configuration.
	 * Reads file content incrementally using cursors to track position and respects the maximum size per poll limit.
	 * Any exception during path resolution or file reading is caught, logged, and results in an empty table.
	 *
	 * @param fileSource The file source configuration containing paths and settings
	 * @param connectorId The connector identifier for namespace management
	 * @param telemetryManager The telemetry manager providing host configuration and properties
	 * @return A SourceTable containing the file paths and their content, or an empty table on error
	 */
	public SourceTable process(final FileSource fileSource, String connectorId, TelemetryManager telemetryManager) {
		final String hostname = telemetryManager.getHostname();
		final boolean isLocalhost = telemetryManager.getHostProperties().isLocalhost();

		if (fileSource == null) {
			log.error("Hostname {} - Malformed file source.", hostname);
			return SourceTable.empty();
		}

		// Depending on whether the host is localhost or remote, create file operations
		final FileOperations fileOperations = isLocalhost
			? createLocalFileOperations(hostname)
			: createRemoteFileOperations(hostname, telemetryManager);

		if (fileOperations == null) {
			log.warn("Hostname {} - Cannot process files: file operations unavailable. Returning an empty table.", hostname);
			return SourceTable.empty();
		}

		final DeviceKind deviceKind = telemetryManager.getHostConfiguration().getHostType();

		try (FileOperations ops = fileOperations) {
			final Set<String> sourceResolvedPaths = new HashSet<>();

			if (isLocalhost) {
				sourceResolvedPaths.addAll(FileHelper.findFilesByPattern(hostname, fileSource.getPaths(), deviceKind));
			} else {
				sourceResolvedPaths.addAll(resolveRemoteFiles(hostname, fileSource, telemetryManager, deviceKind));
			}

			if (sourceResolvedPaths.isEmpty()) {
				return SourceTable.empty();
			}

			if (!isLocalhost) {
				final Object sshConfig = telemetryManager
					.getHostConfiguration()
					.getConfigurations()
					.get(SshConfiguration.class);
				if (sshConfig != null) {
					log.debug("Hostname {} - Configuration Type: {}", hostname, sshConfig.getClass().getSimpleName());
				}
			}
			final List<List<String>> pathsTable = sourceResolvedPaths.stream().map(List::of).collect(Collectors.toList());
			log.debug(
				"Hostname {} - Resolved paths:\n{}",
				hostname,
				TextTableHelper.generateTextTable(List.of("Path"), pathsTable)
			);

			final FileSourceProcessingMode mode = fileSource.getMode();

			if (mode.equals(FileSourceProcessingMode.FLAT)) {
				return SourceTable.builder().table(processFilesInFlatMode(ops, sourceResolvedPaths, hostname)).build();
			} else if (mode.equals(FileSourceProcessingMode.LOG)) {
				// Get the stored cursors from the connector namespace for tracking file read positions
				Map<String, Long> sourceCursors = telemetryManager
					.getHostProperties()
					.getConnectorNamespace(connectorId)
					.getFileSourceCursors(fileSource.getKey());

				return SourceTable
					.builder()
					.table(processFilesInLogMode(ops, sourceResolvedPaths, sourceCursors, fileSource, hostname))
					.build();
			} else {
				throw new IllegalArgumentException("Unknown FileSource processing mode.");
			}
		} catch (Exception e) {
			// Path resolution, FLAT/LOG read, or close failed; log and return empty table
			log.info(
				"Hostname {} - FileSource processing failed. Returning an empty table. Message: {}",
				hostname,
				e.getMessage()
			);
			log.debug("Hostname {} - FileSource processing failed.", hostname, e);
			return SourceTable.empty();
		}
	}

	/**
	 * Processes files in FLAT mode by reading the entire content of each file.
	 * In FLAT mode, files are read completely from the beginning on each poll,
	 * without using cursors to track position.
	 *
	 * @param fileOperations The file operations implementation (local or remote)
	 * @param paths The set of absolute file paths to process
	 * @param hostname The hostname for logging purposes
	 * @return A list of lists containing file paths and their complete content
	 */
	List<List<String>> processFilesInFlatMode(
		final FileOperations fileOperations,
		final Set<String> paths,
		final String hostname
	) {
		final List<List<String>> results = new ArrayList<>();

		// Fetch the content of each file given its path, and add it to the results list
		for (final String path : paths) {
			try {
				final String content = fileOperations.readFileContent(path);
				if (content != null) {
					final long contentSizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
					log.debug("Hostname {} - Path [{}]: content fetched, size={} bytes", hostname, path, contentSizeBytes);
					final List<String> row = new ArrayList<>();
					row.add(path);
					row.add(FileHelper.escapeNewLines(content));
					results.add(row);
				}
			} catch (Exception e) {
				// I/O or other error reading this file; log and skip so other paths can still be processed
				log.info("Hostname {} - Unable to read file located under: {}. Message: {}", hostname, path, e.getMessage());
				log.debug("Hostname {} - Unable to read file located under: {}. Exception: {}", hostname, path, e);
			}
		}
		return results;
	}

	/**
	 * Processes files in LOG mode by reading only new content since the last cursor position.
	 * In LOG mode, cursors track the last read position to enable incremental reading.
	 * Respects the maximum size per poll limit across all files.
	 *
	 * @param fileOperations The file operations implementation (local or remote)
	 * @param paths The set of absolute file paths to process
	 * @param sourceCursors The map storing cursor positions for each file path
	 * @param fileSource The file source configuration containing max size per poll limit
	 * @param hostname The hostname for logging purposes
	 * @return A list of lists containing file paths and their new content since last read
	 */
	List<List<String>> processFilesInLogMode(
		final FileOperations fileOperations,
		final Set<String> paths,
		final Map<String, Long> sourceCursors,
		final FileSource fileSource,
		final String hostname
	) {
		// Initialize remaining size with the maximum size per poll limit (already in bytes)
		long remainingSize = fileSource.getMaxSizePerPoll();

		final List<List<String>> resultedContent = new ArrayList<>();

		try {
			for (final String path : paths) {
				// Skip if the maximum size per poll has been reached
				if (remainingSize == 0) {
					continue;
				}

				// Get the stored cursor for this file path, null if first read
				final Long cursor = sourceCursors.get(path);

				// Process the file and read new content since the last cursor position
				final FileSourceProcessingResult result = processFile(fileOperations, hostname, path, cursor, remainingSize);

				// Update the cursor for this file path in the namespace
				sourceCursors.put(path, result.getCursor());

				// Decrease the remaining size limit by the amount read
				remainingSize = result.getRemainingSize();

				// Add the file content to results if any new content was read
				final String content = result.getContent();
				log.debug(
					"Hostname {} - Path [{}]: content={}, newCursor={}, remainingSize={}",
					hostname,
					path,
					content == null ? "null" : content.length() + " bytes",
					result.getCursor(),
					remainingSize
				);
				if (content != null) {
					final List<String> row = new ArrayList<>();
					row.add(path);
					row.add(FileHelper.escapeNewLines(content));
					resultedContent.add(row);
				}
			}
		} catch (Exception e) {
			// Catches errors during LOG-mode read (e.g. readFromOffset); log and return partial results
			log.info("Hostname {} - An error has occurred during FileSource processing: {}", hostname, e.getMessage());
			log.debug("Hostname {} - An error has occurred during FileSource processing: {}", hostname, e);
		}
		return resultedContent;
	}

	/**
	 * Creates file operations implementation for local file access using native Java file APIs.
	 *
	 * @param hostname The hostname for logging purposes
	 * @return A FileOperations implementation for local file access
	 */
	protected FileOperations createLocalFileOperations(final String hostname) {
		return new FileOperations() {
			@Override
			public String readFromOffset(final String path, final Long offset, final Integer length) throws IOException {
				return FileHelper.readOffset(path, offset, length);
			}

			@Override
			public Long getFileSize(String path) {
				try {
					return FileHelper.getFileSize(path);
				} catch (Exception e) {
					log.info("Hostname {} - Unable to get {} file size: {}", hostname, path, e.getMessage());
					log.debug("Hostname {} - An error has occurred when reading the size of {}: {}", hostname, path, e);
					return null;
				}
			}

			@Override
			public void close() {
				// No cleanup needed for local files
			}

			@Override
			public String readFileContent(String path) {
				try {
					return FileHelper.readFileContent(path);
				} catch (IOException e) {
					log.info("Hostname {} - Unable to get {} file content: {}", hostname, path, e.getMessage());
					log.debug("Hostname {} - An error has occurred when reading the content of {}: {}", hostname, path, e);
					return null;
				}
			}
		};
	}

	/**
	 * Creates file operations implementation for remote file access using SSH.
	 * Establishes SSH connection and authenticates before returning the operations instance.
	 *
	 * @param hostname The hostname for SSH connection
	 * @param telemetryManager The telemetry manager providing SSH configuration
	 * @return A FileOperations implementation for remote file access, or null if SSH setup fails
	 */
	private FileOperations createRemoteFileOperations(final String hostname, final TelemetryManager telemetryManager) {
		final SshConfiguration sshConfiguration = (SshConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		if (sshConfiguration == null) {
			log.warn("Hostname {} - No SSH configuration found. Cannot process remote files.", hostname);
			return null;
		}

		final RemoteFilesRequestExecutor requestExecutor = createRemoteFilesRequestExecutor(hostname, sshConfiguration);

		// Attempt to connect to the remote host
		if (!requestExecutor.connectSshClient()) {
			log.warn("Hostname {} - Failed to connect SSH client. Cannot process remote files.", hostname);
			requestExecutor.closeSshClient();
			return null;
		}

		// Attempt to authenticate with the remote host
		if (!requestExecutor.authenticateSshClient()) {
			log.warn("Hostname {} - Failed to authenticate SSH client. Cannot process remote files.", hostname);
			requestExecutor.closeSshClient();
			return null;
		}

		return new FileOperations() {
			@Override
			public String readFromOffset(String path, Long offset, Integer length) throws IOException {
				return requestExecutor.readRemoteFileOffsetContent(path, offset, length);
			}

			@Override
			public Long getFileSize(String path) throws IOException {
				try {
					return requestExecutor.getRemoteFileSize(path);
				} catch (Exception e) {
					log.info("Hostname {} - Unable to get \"{}\" file size: {}", hostname, path, e.getMessage());
					log.debug("Hostname {} - An error has occurred when reading the file size of {}: {}", hostname, path, e);
					return null;
				}
			}

			@Override
			public void close() {
				requestExecutor.closeSshClient();
			}

			@Override
			public String readFileContent(String path) throws IOException {
				return requestExecutor.readRemoteFileOffsetContent(path, null, null);
			}
		};
	}

	/**
	 * Creates a RemoteFilesRequestExecutor instance for handling remote file operations via SSH.
	 * This method is protected to allow test subclasses to override it for mocking purposes.
	 *
	 * @param hostname The hostname for SSH connection
	 * @param sshConfiguration The SSH configuration containing connection parameters
	 * @return A RemoteFilesRequestExecutor instance configured with SSH client and configuration
	 */
	protected RemoteFilesRequestExecutor createRemoteFilesRequestExecutor(
		final String hostname,
		final SshConfiguration sshConfiguration
	) {
		return new RemoteFilesRequestExecutor(new SshClient(hostname), sshConfiguration);
	}

	/**
	 * Resolves file paths remotely by executing SSH commands to find matching files.
	 * Uses OS-specific commands (PowerShell for Windows, find for Linux) to locate files.
	 *
	 * @param hostname The hostname for SSH connection
	 * @param fileSource The file source containing path patterns to resolve
	 * @param telemetryManager The telemetry manager providing SSH configuration
	 * @param deviceKind The device kind (Windows/Linux) to determine the command format
	 * @return A set of resolved absolute file paths matching the patterns
	 */
	Set<String> resolveRemoteFiles(
		final String hostname,
		final FileSource fileSource,
		final TelemetryManager telemetryManager,
		final DeviceKind deviceKind
	) {
		final Set<String> resolvedFiles = new HashSet<>();

		final SshConfiguration sshConfiguration = (SshConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		if (sshConfiguration == null) {
			log.info("Hostname {} - No SSH configuration found. Cannot resolve remote file paths.", hostname);
			return resolvedFiles;
		}

		final Set<String> rawPaths = fileSource.getPaths();

		if (rawPaths == null || rawPaths.isEmpty()) {
			return new HashSet<>();
		}

		final Set<String> absolutePaths = new HashSet<>();

		for (final String path : rawPaths) {
			// Extract filename pattern and base path from the path pattern
			final String filename = FileHelper.extractFilename(path, deviceKind);
			final String basePath = FileHelper.extractBasePath(path, deviceKind);

			// Build OS-specific command to find matching files
			final String command = deviceKind.equals(DeviceKind.WINDOWS)
				? RESOLVE_WINDOWS_FILES_COMMAND.formatted(basePath, filename)
				: RESOLVE_LINUX_FILES_COMMAND.formatted(basePath, filename);
			try {
				// Execute SSH command to find matching files on remote host
				final String result = OsCommandService.runSshCommand(
					command,
					hostname,
					sshConfiguration,
					sshConfiguration.getTimeout(),
					null,
					command,
					deviceKind
				);

				// Split command result by line breaks, validate paths, add only valid paths
				absolutePaths.addAll(FileHelper.parseResolvedPathsFromCommandResult(result, deviceKind, hostname, path));
			} catch (ClientException | InterruptedException | ControlledSshException e) {
				log.info("Hostname {} - Error occurred when resolving path: {}. Message: {}", hostname, path, e.getMessage());
				log.debug("Hostname {} - Exception occurred when resolving path {}: {}", hostname, path, e);
			}
		}
		return absolutePaths;
	}

	/**
	 * Processes a single file by reading new content since the last cursor position.
	 * Handles first read, file growth detection, and respects the remaining size limit.
	 *
	 * @param fileOperations The file operations implementation (local or remote)
	 * @param hostname The hostname for logging purposes
	 * @param path The absolute path of the file to process
	 * @param cursor The last known cursor position, or null for first read
	 * @param remainingSize The remaining size limit for this polling cycle
	 * @return A FileSourceProcessingResult containing content, new cursor, and remaining size
	 */
	FileSourceProcessingResult processFile(
		final FileOperations fileOperations,
		final String hostname,
		final String path,
		Long cursor,
		final Long remainingSize
	) {
		try {
			// Get the current file size
			final Long fileSize = fileOperations.getFileSize(path);

			// File does not exist or cannot be accessed
			if (fileSize == null) {
				return FileSourceProcessingResult
					.builder()
					.cursor(cursor == null ? null : cursor)
					.remainingSize(remainingSize)
					.build();
			}

			// First read: cursor is null, initialize cursor to current file size without reading content
			if (cursor == null) {
				return FileSourceProcessingResult.builder().cursor(fileSize).remainingSize(remainingSize).build();
			}

			// File has not grown since the last read, no new content to read
			if (fileSize.equals(cursor)) {
				return FileSourceProcessingResult.builder().cursor(cursor).remainingSize(remainingSize).build();
			}

			// Due to log rotation, the file keeps the same name, but its content is transfered and archived elsewhere
			// Consequently, the file size becomes inferior than the cursor.
			// Cursor will be set to 0
			if (fileSize < cursor) {
				cursor = 0L;
			}

			// Calculate the size of new content added since the last cursor position
			final long additionalContentSize = fileSize - cursor;

			// Determine the actual size to read, respecting the remaining size limit
			final long sizeLimit = remainingSize == UNLIMITED_SIZE_PER_POLL
				? additionalContentSize
				: Math.min(additionalContentSize, remainingSize);

			// Cap read length to int range for readFromOffset; advance cursor by what we actually read
			final int readLength = sizeLimit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sizeLimit;

			// Read the new content starting from the cursor position
			final String additionalContent = fileOperations.readFromOffset(path, cursor, readLength);

			// Return the processing results with new content, updated cursor, and remaining size
			return FileSourceProcessingResult
				.builder()
				.content(additionalContent)
				.cursor(cursor + readLength)
				.remainingSize(remainingSize == UNLIMITED_SIZE_PER_POLL ? remainingSize : remainingSize - readLength)
				.build();
		} catch (Exception e) {
			log.info("Hostname {} - An error has occurred when processing file {}: {}", hostname, path, e.getMessage());
			log.debug("Hostname {} - An error has occurred when processing file {}: {}", hostname, path, e);
			return FileSourceProcessingResult.builder().cursor(cursor).remainingSize(remainingSize).build();
		}
	}
}
