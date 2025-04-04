package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.CONNECTORS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Utility class for common file-related operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileHelper {

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
		final int connectorsIndex = strPath.lastIndexOf("/" + CONNECTORS + "/");
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
}
