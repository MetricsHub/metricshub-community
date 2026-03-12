package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.CONNECTORS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.common.DeviceKind;

/**
 * Utility class for common file-related operations.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileHelper {

	// Empty string constant used for default return values.
	public static final String EMPTY = "";

	// Path delimiter used in Linux/Unix file systems.
	public static final String SLASH = "/";

	// Path delimiter used in Windows file systems.
	public static final String BACKSLASH = "\\";

	/**
	 * Regex to split command output by line; accepts both {@code \n} and {@code \r\n}.
	 */
	private static final String LINE_SPLIT_REGEX = "\\r?\\n";

	/**
	 * Valid absolute Windows path: drive letter ({@code C:\...}) or UNC ({@code \\server\share\...}).
	 */
	private static final Pattern ABSOLUTE_WINDOWS_PATH = Pattern.compile("^(?:[A-Za-z]:\\\\" + ".+" + "|\\\\\\\\.+)$");

	/**
	 * Valid absolute Linux/Unix path: starts with {@code /}.
	 */
	private static final Pattern ABSOLUTE_LINUX_PATH = Pattern.compile("^/.*");

	/**
	 * Escape string for new lines.
	 */
	private static final String NEW_LINE_ESCAPE_STRING = "@{newLine}@";

	/**
	 * Returns the time of last modification of the specified Path in milliseconds since EPOCH.
	 *
	 * @param path The path to the file.
	 * @return Milliseconds since EPOCH, or 0 (zero) if the file does not exist.
	 * @throws IllegalArgumentException If the specified path is null.
	 */
	public static long getLastModifiedTime(@NonNull Path path) {
		try {
			return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	/**
	 * Return the path to the connectors directory if the {@link Path} in parameter is a path containing a "connectors" folder.
	 * @param zipUri The path where to look for the connectors directory
	 * @return The {@link Path}  of the connector directory
	 */
	public static Path findConnectorsDirectory(final URI zipUri) {
		final String strPath = zipUri.toString();
		final int connectorsIndex = strPath.lastIndexOf(SLASH + CONNECTORS + SLASH);
		if (connectorsIndex == -1) {
			return null;
		}

		// Determine the starting index based on the operating system (Windows or other)
		final int beginIndex = LocalOsHandler.isWindows() ? "jar:file:///".length() : "jar:file://".length();

		return Paths.get(strPath.substring(beginIndex, connectorsIndex + 1 + CONNECTORS.length()));
	}

	/**
	 * Executes a file system task using the provided URI, environment map, and a runnable task within a try-with-resources block.
	 *
	 * This method creates a new file system based on the specified URI and the provided environment map. It then executes the
	 * provided runnable task within the context of this file system. The file system is automatically closed when the task
	 * completes or if an exception is thrown.
	 *
	 * @param uri The non-null URI specifying the file system to be created.
	 * @param env The non-null map of file system provider-specific properties and options.
	 * @param runnable The non-null task to be executed within the created file system.
	 * @throws IOException If an I/O error occurs while creating or operating on the file system.
	 */
	public static void fileSystemTask(
		@NonNull final URI uri,
		@NonNull final Map<String, ?> env,
		@NonNull final Runnable runnable
	) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
			runnable.run();
		}
	}

	/**
	 * Executes a file system task using the provided URI, environment map, and a callable task within a try-with-resources block.
	 *
	 * This method creates a new file system based on the specified URI and the provided environment map. It then executes the
	 * provided callable task within the context of this file system. The file system is automatically closed when the task
	 * completes or if an exception is thrown.
	 * @param <T>
	 *
	 * @param uri The non-null URI specifying the file system to be created.
	 * @param env The non-null map of file system provider-specific properties and options.
	 * @param callable The non-null task to be executed within the created file system.
	 * @return
	 * @throws IOException If an I/O error occurs while creating or operating on the file system.
	 */
	public static <T> T fileSystemTask(
		@NonNull final URI uri,
		@NonNull final Map<String, ?> env,
		@NonNull final Callable<T> callable
	) throws Exception {
		try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
			return callable.call();
		}
	}

	/**
	 * Utility method to read the content of a file specified by a URI.
	 * The file content is read line by line and joined into a single string.
	 *
	 * @param filePath The path of the file to be read.
	 * @return A string representing the content of the file.
	 * @throws IOException If an I/O error occurs while reading the file.
	 */
	public static String readFileContent(final Path filePath) throws IOException {
		try (Stream<String> lines = Files.lines(filePath)) {
			return lines.collect(Collectors.joining(NEW_LINE));
		}
	}

	/**
	 * Extracts the extension of a provided filename.
	 *
	 * @param filename The filename from which to extract the extension.
	 * @return The extension of the file (E.g <b>.awk</b>)or an empty string if no extension exists.
	 */
	public static String getExtension(String filename) {
		// Find the last index of '.' in the filename
		final int lastIndex = filename.lastIndexOf('.');

		// Check if the '.' is in a valid position
		if (lastIndex > 0 && lastIndex < filename.length() - 1) {
			return filename.substring(lastIndex);
		}

		// Return an empty string if no extension found
		return MetricsHubConstants.EMPTY;
	}

	/**
	 * Extracts the filename without its extension.
	 *
	 * @param filename The filename from which to remove the extension.
	 * @return The filename without its extension.
	 */
	public static String getBaseName(String filename) {
		// Find the last index of '.' in the filename
		final int lastIndex = filename.lastIndexOf('.');

		// Check if the '.' is in a valid position
		if (lastIndex > 0) {
			return filename.substring(0, lastIndex);
		}

		// Return the whole filename if no valid '.' found
		return filename;
	}

	/**
	 * Extracts the base directory path from an absolute file path.
	 * Removes the filename portion, leaving only the directory path.
	 *
	 * @param absolutePath The absolute file path
	 * @param hostType The device kind to determine the path delimiter
	 * @return The base directory path, or empty string if path is invalid
	 */
	public static String extractBasePath(final String absolutePath, final DeviceKind hostType) {
		if (absolutePath == null || absolutePath.isBlank()) {
			return EMPTY;
		}

		// Set the path delimiter depending on the host type
		final String pathDelimiter = hostType.equals(DeviceKind.WINDOWS) ? BACKSLASH : SLASH;

		// If path doesn't contain the delimiter, it's not a valid absolute path
		if (!absolutePath.contains(pathDelimiter)) {
			return EMPTY;
		}

		return absolutePath.substring(0, absolutePath.lastIndexOf(pathDelimiter));
	}

	/**
	 * Extracts the filename from an absolute file path.
	 * Returns the last component of the path after the path delimiter.
	 *
	 * @param absolutePath The absolute file path
	 * @param hostType The device kind to determine the path delimiter
	 * @return The filename, or empty string if path is invalid
	 */
	public static String extractFilename(final String absolutePath, final DeviceKind hostType) {
		if (absolutePath == null || absolutePath.isBlank()) {
			return EMPTY;
		}

		final String pathDelimiter = hostType.equals(DeviceKind.WINDOWS) ? BACKSLASH : SLASH;

		if (!absolutePath.contains(pathDelimiter)) {
			return EMPTY;
		}

		return absolutePath.substring(absolutePath.lastIndexOf(pathDelimiter) + 1, absolutePath.length());
	}

	/**
	 * Parses remote command output into a set of validated absolute file paths.
	 * Splits on {@code \n} or {@code \r\n}, trims and skips empty lines, and keeps only lines
	 * matching the expected format for the given device kind. Non-matching lines are logged and skipped.
	 *
	 * @param result      raw command output
	 * @param deviceKind  Windows or Linux to select the path validation pattern
	 * @param hostname    hostname for logging (may be null)
	 * @param pathPattern path pattern for logging (may be null)
	 * @return set of validated absolute paths (possibly empty)
	 */
	public static Set<String> parseResolvedPathsFromCommandResult(
		final String result,
		final DeviceKind deviceKind,
		final String hostname,
		final String pathPattern
	) {
		final Set<String> resolved = new HashSet<>();
		if (result == null || result.isEmpty()) {
			return resolved;
		}
		final Pattern pathPatternMatcher = deviceKind.equals(DeviceKind.WINDOWS)
			? ABSOLUTE_WINDOWS_PATH
			: ABSOLUTE_LINUX_PATH;

		for (final String raw : result.split(LINE_SPLIT_REGEX, -1)) {
			final String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (pathPatternMatcher.matcher(line).matches()) {
				resolved.add(line);
			} else {
				log.debug("Hostname {} - Skipping non-path output when resolving path {}: {}", hostname, pathPattern, line);
			}
		}
		return resolved;
	}

	/**
	 * Reads the entire content of a file as a string using UTF-8 encoding.
	 *
	 * @param path The absolute path to the file
	 * @return The entire file content as a string
	 * @throws IOException If an I/O error occurs while reading the file
	 */
	public static String readFileContent(final String path) throws IOException {
		return Files.readString(Path.of(path), StandardCharsets.UTF_8);
	}

	public static String readOffset(final String path, final long offset, final int length)
		throws FileNotFoundException, IOException {
		try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
			// Set the file pointer to the cursor offset position
			file.seek(offset);

			// Create a byte array to read the requested length
			byte[] buffer = new byte[length];

			// Read bytes from the file into the buffer
			int bytesRead = file.read(buffer);

			// If EOF is reached, return empty string
			if (bytesRead == -1) {
				return "";
			}
			// Convert bytes to string using UTF-8 encoding
			return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
		}
	}

	public static Long getFileSize(final String path) throws IOException {
		return Files.size(Path.of(path));
	}

	/**
	 * Resolves file paths locally by matching filename patterns in the specified directories.
	 * Uses native Java file system APIs to find matching files.
	 *
	 * @param hostname   The hostname for logging purposes
	 * @param paths      The set containing path patterns to resolve
	 * @param deviceKind The device kind (Windows/Linux) to determine path delimiters
	 * @return A set of resolved absolute file paths matching the patterns
	 */
	public static Set<String> findFilesByPattern(
		final String hostname,
		final Set<String> paths,
		final DeviceKind deviceKind
	) {
		final Set<String> resolvedPaths = new HashSet<>();

		if (paths == null) {
			return resolvedPaths;
		}

		for (final String stringPath : paths) {
			// Extract the base directory path from the pattern
			final String basePath = FileHelper.extractBasePath(stringPath, deviceKind);
			// Extract the filename pattern (may contain wildcards)
			final String filename = FileHelper.extractFilename(stringPath, deviceKind);

			// Use directory stream to find all files matching the filename pattern
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(basePath), filename)) {
				stream.forEach(resolvedPath -> resolvedPaths.add(resolvedPath.toString()));
			} catch (IOException e) {
				log.info("Hostname {} - Unable to resolve path: {}. Message: {}", hostname, stringPath, e.getMessage());
				log.debug("Hostname {} - Exception occurred when resolving path {}: {}", hostname, stringPath, e);
			}
		}

		return resolvedPaths;
	}

	/**
	 * Escapes newline characters in a string by replacing them with a placeholder.
	 * Handles Windows line endings (\r\n) and Unix line endings (\n).
	 *
	 * @param value the string to escape
	 * @return the string with newlines replaced by {@value #NEW_LINE_ESCAPE_STRING}, or null if input is null
	 */
	public static String escapeNewLines(final String value) {
		if (value == null) {
			return null;
		}

		// Replace \r\n first (Windows line endings), then handle any remaining \r or \n
		// Use replace() for literal replacements to avoid regex interpretation of $ in replacement string
		return value
			.replace("\r\n", NEW_LINE_ESCAPE_STRING)
			.replace("\n", NEW_LINE_ESCAPE_STRING)
			.replace("\r", NEW_LINE_ESCAPE_STRING);
	}
}
