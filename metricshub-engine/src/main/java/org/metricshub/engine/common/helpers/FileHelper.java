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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
 * Utility class for common file-related operations used by connectors and extensions (path parsing, local file discovery,
 * formatting multi-path text into delimiter-bounded segments, and small string escapes for table transport).
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileHelper {

	// Path delimiter used in Linux/Unix file systems.
	public static final String SLASH = "/";

	// Path delimiter used in Windows file systems.
	public static final String BACKSLASH = "\\";

	/**
	 * Prefix of UNC paths ({@code \\server\share\...}).
	 */
	private static final String UNC_PREFIX = "\\\\";

	/**
	 * Windows drive designator ({@code C:}) as the first segment of an absolute path.
	 */
	private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:$");

	/**
	 * Glob characters, other than the supported {@code *} and {@code ?} wildcards, that must be escaped to match literally.
	 */
	private static final String GLOB_SPECIALS = "[]{}\\";

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
	 * Opening delimiter for one text segment in {@link #buildLogBlock}; includes the file path in the segment header.
	 */
	private static final String LOG_START_MARKER = "<<<LOG:file=\"%s\">>>";

	/**
	 * Closing delimiter for one text segment in {@link #buildLogBlock}, paired with {@link #LOG_START_MARKER}.
	 */
	private static final String LOG_END_MARKER = "<<<END_LOG>>>";

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
	 *
	 * @param zipUri The path where to look for the connectors directory
	 * @return The {@link Path} of the connector directory, or {@code null} if no {@code /connectors/} segment is found in the URI string
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
		try (FileSystem _ = FileSystems.newFileSystem(uri, env)) {
			runnable.run();
		}
	}

	/**
	 * Executes a file system task using the provided URI, environment map, and a callable task within a try-with-resources block.
	 *
	 * This method creates a new file system based on the specified URI and the provided environment map. It then executes the
	 * provided callable task within the context of this file system. The file system is automatically closed when the task
	 * completes or if an exception is thrown.
	 *
	 * @param <T>      the type of value returned by the callable
	 * @param uri      The non-null URI specifying the file system to be created.
	 * @param env      The non-null map of file system provider-specific properties and options.
	 * @param callable The non-null task to be executed within the created file system.
	 * @return the value returned by {@code callable.call()}
	 * @throws Exception if {@code callable.call()} throws, or if the file system cannot be created
	 */
	public static <T> T fileSystemTask(
		@NonNull final URI uri,
		@NonNull final Map<String, ?> env,
		@NonNull final Callable<T> callable
	) throws Exception {
		try (FileSystem _ = FileSystems.newFileSystem(uri, env)) {
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
	 * A file path pattern split into a literal root directory and the remaining path segments.
	 * <p>
	 * The root never contains a wildcard and never ends with the delimiter, except for bare roots
	 * ({@code /}, {@code D:\}, {@code \\server\share}). The segments hold everything after the root; any
	 * segment, directory or filename, may contain the {@code *} and {@code ?} wildcards.
	 * </p>
	 *
	 * @param root      literal root directory
	 * @param segments  path segments after the root (at least one)
	 * @param delimiter path delimiter of the target host
	 */
	public record PathPattern(String root, List<String> segments, String delimiter) {
		/**
		 * @return true when a directory segment (any segment but the last) contains a wildcard
		 */
		public boolean hasDirectoryWildcard() {
			return segments.subList(0, segments.size() - 1).stream().anyMatch(FileHelper::containsWildcard);
		}

		/**
		 * @return the last segment, i.e. the filename pattern
		 */
		public String filename() {
			return segments.get(segments.size() - 1);
		}

		/**
		 * @return the full pattern: the root followed by every segment, joined with the delimiter
		 */
		public String fullPattern() {
			return joinPath(root, String.join(delimiter, segments), delimiter);
		}

		/**
		 * @return the directory pattern: the root followed by every segment but the last, or the root alone when the
		 * pattern has a single segment
		 */
		public String directoryPattern() {
			return segments.size() == 1
				? root
				: joinPath(root, String.join(delimiter, segments.subList(0, segments.size() - 1)), delimiter);
		}
	}

	/**
	 * Checks whether a path segment contains a wildcard ({@code *} or {@code ?}).
	 *
	 * @param segment the path segment to check
	 * @return true when the segment contains at least one wildcard
	 */
	public static boolean containsWildcard(final String segment) {
		return segment != null && (segment.indexOf('*') >= 0 || segment.indexOf('?') >= 0);
	}

	/**
	 * Parses an absolute file path pattern into a literal root and path segments.
	 * <ul>
	 * <li>A trailing delimiter designates a directory and is normalized to {@code *} (all files of the directory).</li>
	 * <li>Windows paths must start with a drive ({@code C:\}) or a UNC prefix ({@code \\server\share}); the server
	 * and share names are always literal.</li>
	 * <li>Other paths must start with {@code /}.</li>
	 * <li>The root is extended with every leading literal directory segment, but never with the last segment.</li>
	 * </ul>
	 *
	 * @param path     the path pattern, possibly containing {@code *} and {@code ?} in any segment
	 * @param hostType the device kind, which determines the path delimiter
	 * @return the parsed pattern, or null when the path is blank, relative or has a wildcard in a UNC server or share
	 */
	public static PathPattern parsePathPattern(final String path, final DeviceKind hostType) {
		if (path == null || path.isBlank()) {
			return null;
		}

		final String delimiter = DeviceKind.WINDOWS.equals(hostType) ? BACKSLASH : SLASH;
		String normalized = path.trim();

		// A trailing delimiter designates a directory: match all its files
		if (normalized.endsWith(delimiter)) {
			normalized += "*";
		}

		final List<String> parts = Arrays.stream(normalized.split(Pattern.quote(delimiter)))
			.filter(part -> !part.isEmpty())
			.toList();

		// Determine the fixed root, which can never hold a wildcard
		final String fixedRoot;
		final int fixedCount;
		if (DeviceKind.WINDOWS.equals(hostType)) {
			if (normalized.startsWith(UNC_PREFIX)) {
				if (parts.size() < 2 || containsWildcard(parts.get(0)) || containsWildcard(parts.get(1))) {
					return null;
				}
				fixedRoot = UNC_PREFIX + parts.get(0) + BACKSLASH + parts.get(1);
				fixedCount = 2;
			} else if (!parts.isEmpty() && WINDOWS_DRIVE.matcher(parts.get(0)).matches()) {
				fixedRoot = parts.get(0) + BACKSLASH;
				fixedCount = 1;
			} else {
				return null;
			}
		} else if (normalized.startsWith(SLASH)) {
			fixedRoot = SLASH;
			fixedCount = 0;
		} else {
			return null;
		}

		final List<String> remaining = parts.subList(fixedCount, parts.size());
		if (remaining.isEmpty()) {
			return null;
		}

		// Extend the root with the leading literal directory segments, never consuming the last segment
		int literalCount = 0;
		while (literalCount < remaining.size() - 1 && !containsWildcard(remaining.get(literalCount))) {
			literalCount++;
		}

		final String root =
			literalCount == 0
				? fixedRoot
				: joinPath(fixedRoot, String.join(delimiter, remaining.subList(0, literalCount)), delimiter);

		return new PathPattern(root, List.copyOf(remaining.subList(literalCount, remaining.size())), delimiter);
	}

	/**
	 * Appends a relative path to a root, inserting the delimiter unless the root already ends with it.
	 */
	private static String joinPath(final String root, final String relative, final String delimiter) {
		return root.endsWith(delimiter) ? root + relative : root + delimiter + relative;
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

	/**
	 * Reads a byte range from a file starting at {@code offset} for at most {@code length} bytes, decoded as UTF-8.
	 *
	 * @param path   absolute path to the file
	 * @param offset byte offset from the start of the file
	 * @param length maximum number of bytes to read
	 * @return the decoded text for the bytes actually read; empty string if end-of-file is reached immediately
	 * @throws FileNotFoundException if the file does not exist
	 * @throws IOException           if an I/O error occurs while reading
	 */
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

	/**
	 * Returns the size of a regular file in bytes.
	 *
	 * @param path absolute path to the file
	 * @return file size in bytes
	 * @throws IOException if the file cannot be accessed or is not a regular file
	 */
	public static Long getFileSize(final String path) throws IOException {
		return Files.size(Path.of(path));
	}

	/**
	 * Resolves file path patterns locally using the native Java file system APIs.
	 * Each pattern is walked segment by segment from its literal root, so {@code *} and {@code ?} are honored in
	 * directory segments as well as in the filename. Only regular files are returned. A pattern that cannot be
	 * parsed or resolved is logged and skipped without affecting the other patterns.
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
			final PathPattern pattern = parsePathPattern(stringPath, deviceKind);
			if (pattern == null) {
				log.info("Hostname {} - Skipping invalid file path pattern: {}", hostname, stringPath);
				continue;
			}

			try {
				collectMatchingFiles(Path.of(pattern.root()), pattern.segments(), 0, resolvedPaths, hostname);
			} catch (IOException | RuntimeException e) {
				// I/O failure or invalid path (e.g. InvalidPathException): log and continue with the other patterns
				log.info("Hostname {} - Unable to resolve path: {}. Message: {}", hostname, stringPath, e.getMessage());
				log.debug("Hostname {} - Exception occurred when resolving path {}: {}", hostname, stringPath, e);
			}
		}

		return resolvedPaths;
	}

	/**
	 * Walks one segment of a path pattern under {@code directory}, recursing into matching directories and
	 * collecting matching regular files on the last segment.
	 *
	 * @param directory     the directory to scan
	 * @param segments      all pattern segments
	 * @param index         index of the segment to match in {@code directory}
	 * @param resolvedPaths accumulator of resolved absolute file paths
	 * @param hostname      the hostname for logging purposes
	 * @throws IOException if {@code directory} cannot be listed
	 */
	private static void collectMatchingFiles(
		final Path directory,
		final List<String> segments,
		final int index,
		final Set<String> resolvedPaths,
		final String hostname
	) throws IOException {
		final String segment = segments.get(index);
		final boolean isLast = index == segments.size() - 1;

		// Literal segment: resolve it directly, no directory listing needed
		if (!containsWildcard(segment)) {
			final Path candidate = directory.resolve(segment);
			if (isLast) {
				if (Files.isRegularFile(candidate)) {
					resolvedPaths.add(candidate.toString());
				}
			} else if (Files.isDirectory(candidate)) {
				collectMatchingFiles(candidate, segments, index + 1, resolvedPaths, hostname);
			}
			return;
		}

		// Wildcard segment: list the directory with a glob restricted to '*' and '?'
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, escapeGlobSpecials(segment))) {
			for (final Path match : stream) {
				if (isLast) {
					if (Files.isRegularFile(match)) {
						resolvedPaths.add(match.toString());
					}
				} else if (Files.isDirectory(match)) {
					try {
						collectMatchingFiles(match, segments, index + 1, resolvedPaths, hostname);
					} catch (IOException e) {
						// One matched directory cannot be listed: skip it and keep scanning its siblings
						log.debug("Hostname {} - Unable to scan directory {}: {}", hostname, match, e.getMessage());
					}
				}
			}
		}
	}

	/**
	 * Escapes the glob characters other than {@code *} and {@code ?} so that they match literally.
	 *
	 * @param segment the path segment to escape
	 * @return a glob expression where only {@code *} and {@code ?} act as wildcards
	 */
	static String escapeGlobSpecials(final String segment) {
		final StringBuilder escaped = new StringBuilder(segment.length());
		for (final char c : segment.toCharArray()) {
			if (GLOB_SPECIALS.indexOf(c) >= 0) {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	/**
	 * Escapes {@code [} and {@code ]}, which PowerShell wildcards treat as character classes, so that they match
	 * literally in a {@code Get-Item -Path} pattern and only {@code *} and {@code ?} act as wildcards.
	 * The backtick is doubled because the pattern is embedded in a double-quoted PowerShell string, where a single
	 * backtick would be consumed as the string escape character before reaching the wildcard engine.
	 *
	 * @param pattern the PowerShell path pattern
	 * @return the pattern with literal brackets
	 */
	public static String escapePowerShellBrackets(final String pattern) {
		return pattern.replace("[", "``[").replace("]", "``]");
	}

	/**
	 * Escapes newline characters in a string by replacing them with a placeholder.
	 * Handles Windows line endings (\r\n) and Unix line endings (\n).
	 *
	 * @param value the string to escape
	 * @return the string with newlines replaced by the configured placeholder, or null if input is null
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

	/**
	 * Normalizes {@code [path, text]} rows into a single raw payload for {@link org.metricshub.engine.strategy.source.SourceTable}.
	 * <p>
	 * When {@link #isSinglePathMapping(Set, Set, List)} is true, the sole row text is returned as-is (path and segment
	 * markers are omitted).
	 * Otherwise, each {@code [path, text]} pair is appended as a segment bounded by {@code LOG_START_MARKER} and
	 * {@code LOG_END_MARKER}, with the path embedded in the opening marker.
	 * </p>
	 *
	 * @param monitoringResults rows with at least two columns: absolute path and associated text
	 * @param paths             configured path literals or patterns
	 * @param resolvedPaths     paths after resolution on the host
	 * @return formatted raw data, or {@code null} when there is nothing to emit
	 */
	public static String buildLogBlock(
		final List<List<String>> monitoringResults,
		final Set<String> paths,
		final Set<String> resolvedPaths
	) {
		if (monitoringResults.isEmpty()) {
			return null;
		}

		// One-to-one path resolution with a single read result: emit text only, without segment markers.
		if (isSinglePathMapping(paths, resolvedPaths, monitoringResults)) {
			return escapeSemiColon(monitoringResults.get(0).get(1));
		}

		final StringBuilder logBlocks = new StringBuilder();

		monitoringResults.forEach(row -> {
			final String path = row.get(0);
			final String content = escapeSemiColon(row.get(1));

			appendLogBlock(logBlocks, path, content);
		});

		return logBlocks.toString();
	}

	/**
	 * Appends a formatted log block to the provided raw data buffer.
	 * <p>
	 * Each log block contains:
	 * <ul>
	 *   <li>a start marker containing the file path</li>
	 *   <li>the file content, if not empty</li>
	 *   <li>an end marker</li>
	 * </ul>
	 *
	 * @param rawData the buffer that accumulates the formatted log blocks
	 * @param path the path associated with the log content
	 * @param content the escaped log content to append
	 */
	public static void appendLogBlock(final StringBuilder rawData, final String path, final String content) {
		rawData.append(LOG_START_MARKER.formatted(path)).append("\n");

		if (!content.isEmpty()) {
			rawData.append(content).append("\n");
		}

		rawData.append(LOG_END_MARKER).append("\n\n");
	}

	/**
	 * Escapes semicolons in a string so delimiter-sensitive downstream processing is not broken.
	 *
	 * @param value text to escape; must not be {@code null}
	 * @return a new string with each {@code ';'} replaced by the configured escape token
	 */
	public static String escapeSemiColon(final String value) {
		return value.replace(";", ",");
	}

	/**
	 * Returns {@code true} when configured and resolved paths match one-to-one (same literal, no wildcard expansion) and
	 * {@code monitoringResults} contains exactly one {@code [path, text]} row.
	 *
	 * @param paths             configured path literals or patterns
	 * @param resolvedPaths     paths after resolution on the host
	 * @param monitoringResults rows with at least two columns: absolute path and associated text
	 * @return {@code true} when path sets match one-to-one and there is a single monitoring row
	 */
	public static boolean isSinglePathMapping(
		final Set<String> paths,
		final Set<String> resolvedPaths,
		final List<List<String>> monitoringResults
	) {
		if (paths.size() != 1 || resolvedPaths.size() != 1 || monitoringResults.size() != 1) {
			return false;
		}

		return paths.iterator().next().equals(resolvedPaths.iterator().next());
	}
}
