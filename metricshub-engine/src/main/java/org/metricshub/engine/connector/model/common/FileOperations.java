package org.metricshub.engine.connector.model.common;

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

import java.io.IOException;

/**
 * Defines the contract for file operations, allowing abstraction between local and remote file access.
 * This interface extends AutoCloseable to ensure proper resource management for file operations.
 * Implementations handle file size retrieval and reading file content from a specific offset.
 */
public interface FileOperations extends AutoCloseable {
	/**
	 * Retrieves the size of the file at the given path.
	 *
	 * @param path The absolute path to the file
	 * @return The size of the file in bytes, or null if the file does not exist or cannot be accessed
	 * @throws IOException If an error occurs during file size retrieval
	 */
	Long getFileSize(String path) throws IOException;

	/**
	 * Reads a specified length of content from a file starting at a given offset.
	 *
	 * @param path The absolute path to the file
	 * @param offset The starting position (in bytes) to read from
	 * @param length The maximum number of bytes to read
	 * @return The content read from the file as a String, or an empty string if EOF is reached
	 * @throws IOException If an error occurs during file reading
	 */
	String readFromOffset(String path, Long offset, Integer length) throws IOException;

	/**
	 * Reads the entire content of a file from the beginning.
	 * This method is used in FLAT mode to read complete file content on each poll.
	 *
	 * @param path The absolute path to the file
	 * @return The entire file content as a String, or null if an error occurs
	 * @throws IOException If an error occurs during file reading
	 */
	String readFileContent(String path) throws IOException;
}
