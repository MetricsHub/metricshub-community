package org.metricshub.engine.connector.parser;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.CANT_FIND_EMBEDDED_FILE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Resolves and internalizes embedded files within a JsonNode.
 */
@Slf4j
public class EmbeddedFilesResolver {

	private final JsonNode connector;
	private final Path connectorDirectory;
	private final Set<URI> parents;

	/**
	 *  Mapping of embedded files where each embedded file is associated with the path.
	 */
	private final Map<String, EmbeddedFile> processedEmbeddedFiles;

	/**
	 * Constructs an EmbeddedFilesResolver with the given parameters.
	 *
	 * @param connector           The JsonNode representing the connector.
	 * @param connectorDirectory  The directory of the connector.
	 * @param parents             Set of parent directories URIs.
	 */
	public EmbeddedFilesResolver(final JsonNode connector, final Path connectorDirectory, final Set<URI> parents) {
		this.connector = connector;
		this.connectorDirectory = connectorDirectory;
		this.parents = parents;
		processedEmbeddedFiles = new HashMap<>();
	}

	/**
	 * Look for all references of embedded files that look like: ${file::},
	 * find the referenced file, load its content as byte array to create a new {@link EmbeddedFile} instance,
	 * store the created embedded file in the {@link #processedEmbeddedFiles} lookup and replace the reference
	 * to the external file by a reference to the internalized embedded file in the {@link JsonNode} representing
	 * the connector.
	 * @throws IOException If there is an issue finding the embedded file or processing the JSON structure.
	 */
	public void process() throws IOException {
		final JsonParser jsonParser = connector.traverse();
		JsonToken token = jsonParser.nextToken();

		while (token != null) {
			final String currentValue = jsonParser.getValueAsString();

			if (currentValue == null) {
				token = jsonParser.nextToken();
				continue;
			}

			final Matcher fileMatcher = FILE_PATTERN.matcher(currentValue);
			while (fileMatcher.find()) {
				final String fileName = fileMatcher.group(1);

				processedEmbeddedFiles.computeIfAbsent(
					fileName,
					name -> {
						try {
							return processFile(name, connectorDirectory);
						} catch (Exception e) {
							final String errorMessage = String.format(
								"Error while processing file: %s. Current Connector directory: %s .",
								name,
								connectorDirectory
							);
							log.error(errorMessage, e);
							throw new EmbeddedFileProcessingException(errorMessage, e);
						}
					}
				);
			}

			token = jsonParser.nextToken();
		}

		// If there were no embedded files to process, no need to continue
		if (processedEmbeddedFiles.isEmpty()) {
			return;
		}

		final List<EmbeddedFile> embeddedFilesWithEmbeddedFiles = new ArrayList<>();

		// Parse the embedded files to find eventual new embedded files within them.
		Collection<EmbeddedFile> temporaryEmbeddedFilesList = new ArrayList<>(processedEmbeddedFiles.values());
		while (!temporaryEmbeddedFilesList.isEmpty()) {
			// This is the list of new embedded files that we will find in the embedded files to process
			final List<EmbeddedFile> newEmbeddedFiles = new ArrayList<>();
			for (EmbeddedFile processedEmbeddedFile : temporaryEmbeddedFilesList) {
				// Look for "${file::" pattern
				final Matcher fileMatcher = FILE_PATTERN.matcher(processedEmbeddedFile.getContentAsString());
				while (fileMatcher.find()) {
					final String fileName = fileMatcher.group(1);
					embeddedFilesWithEmbeddedFiles.add(processedEmbeddedFile);

					// If we encounter a new embedded file we process it, add it to the map of processed files
					// and add it to the temporary list to process in the next loop iteration.
					processedEmbeddedFiles.computeIfAbsent(
						fileName,
						name -> {
							try {
								final EmbeddedFile newEmbeddedFile = processFile(name, connectorDirectory);
								newEmbeddedFiles.add(newEmbeddedFile);
								return newEmbeddedFile;
							} catch (Exception e) {
								final String errorMessage = String.format(
									"Error while processing file: %s. Current Connector directory: %s .",
									name,
									connectorDirectory
								);
								log.error(errorMessage, e);
								throw new EmbeddedFileProcessingException(errorMessage, e);
							}
						}
					);
				}
			}
			temporaryEmbeddedFilesList = new ArrayList<>(newEmbeddedFiles);
		}

		JsonNodeUpdater
			.jsonNodeUpdaterBuilder()
			.withJsonNode(connector)
			.withPredicate(value -> value.indexOf("${file::") != -1)
			.withUpdater(this::performFileRefReplacements)
			.build()
			.update();

		// Update the embedded files that are referencing other embedded files.
		for (EmbeddedFile processedEmbeddedFile : embeddedFilesWithEmbeddedFiles) {
			processedEmbeddedFile.setContent(
				performFileRefReplacements(processedEmbeddedFile.getContentAsString()).getBytes(StandardCharsets.UTF_8)
			);
		}
	}

	/**
	 * Reads the file content and generate an {@link EmbeddedFile} instance.
	 *
	 * @param fileName           The name or relative path of the file
	 * @param connectorDirectory The name of the connector directory where to look for the file
	 * @return The absolute path of the file if found, null otherwise.
	 * @throws IOException
	 * @throws IllegalStateException when the file can't be found
	 */
	public EmbeddedFile processFile(final String fileName, final Path connectorDirectory) throws IOException {
		// Let's check if the file exists.
		final Path filePath = connectorDirectory.resolve(fileName).normalize();

		// If the file is in the zip, then checking its existence is easy
		if (Files.exists(filePath)) {
			return createEmbeddedFile(filePath);
		}

		// If the file doesn't exist in the zip using the fileName, lets check the parents
		final Iterator<URI> iterator = parents.iterator();

		while (iterator.hasNext()) {
			Path path = Paths.get(iterator.next()).resolve(fileName).normalize();

			// We do the same checks with the parents
			if (Files.exists(path)) {
				return createEmbeddedFile(path);
			}
		}

		// Last attempt using path from connectors directory
		if (!fileName.startsWith(".")) {
			final Path connectorsDirectoryPath = FileHelper.findConnectorsDirectory(connectorDirectory.toUri());
			if (connectorsDirectoryPath != null) {
				final Path connectorPathFile = connectorsDirectoryPath.resolve(fileName).normalize();
				if (Files.exists(connectorPathFile)) {
					return createEmbeddedFile(connectorPathFile);
				}
			}
		}

		throw new IllegalStateException(CANT_FIND_EMBEDDED_FILE + fileName);
	}

	/**
	 * Creates an {@link EmbeddedFile} instance from a file specified by the given {@code filePath}.
	 * This method reads all bytes from the file and sets these as the content of the new embedded file.
	 * It also assigns a unique identifier based on the current count of already processed embedded files
	 * and sets the file name from the file path.
	 *
	 * @param filePath The {@link Path} to the file from which the embedded file will be created.
	 * @return An {@link EmbeddedFile} with the unique ID, filename, and content loaded from the specified file.
	 * @throws IOException If there is an error reading the file at the specified path, such as if the file does not exist,
	 *         or if there is an issue with file permissions.
	 */
	private EmbeddedFile createEmbeddedFile(final Path filePath) throws IOException {
		return EmbeddedFile
			.builder()
			.id(processedEmbeddedFiles.size() + 1)
			.filename(filePath.getFileName().toString())
			.content(Files.readAllBytes(filePath))
			.build();
	}

	/**
	 * Perform replacements on the given value using the key-value pairs
	 * provided in the replacements {@link Map}.
	 *
	 * @param value to replace
	 * @return new {@link String} value
	 */
	private String performFileRefReplacements(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		// No need to transform value if it doesn't have the placeholder
		final Matcher matcher = FILE_PATTERN.matcher(value);
		while (matcher.find()) {
			value = replaceFileReference(matcher, value);
		}

		return value;
	}

	/**
	 * Replace the file reference with the corresponding reference located in
	 *
	 * @param matcher The matcher for the file reference.
	 * @param value   The original value containing the file reference.
	 * @return The modified value with the file reference replaced.
	 */
	private String replaceFileReference(final Matcher matcher, final String value) {
		final String groupOne = matcher.group(1);
		final Integer replacement = processedEmbeddedFiles.get(groupOne).getId();
		return value.replace(groupOne, replacement.toString());
	}

	/**
	 * Collects all processed embedded files into a map where each file is indexed
	 * by its unique identifier.
	 *
	 * @return A {@link Map} with integer keys representing the unique ID of each
	 *         embedded file and values as the corresponding {@link EmbeddedFile} instances.
	 */
	public Map<Integer, EmbeddedFile> collectEmbeddedFiles() {
		return processedEmbeddedFiles
			.values()
			.stream()
			.collect(Collectors.toMap(EmbeddedFile::getId, Function.identity(), (k1, k2) -> k1));
	}

	/**
	 * A custom exception class for errors that occur during the processing of embedded files.
	 * This class extends {@link RuntimeException} and provides constructor that allows the
	 * inclusion of an error message and a throwable cause.
	 */
	public class EmbeddedFileProcessingException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		/**
		 * Constructs a new exception with the specified detail message and cause.
		 *
		 * @param message The detail message. The detail message is saved for later
		 *                retrieval by the {@link Throwable#getMessage()} method.
		 * @param cause   The cause (which is saved for later retrieval by the
		 *                {@link Throwable#getCause()} method). (A null value is
		 *                permitted, and indicates that the cause is nonexistent or
		 *                unknown.)
		 */
		private EmbeddedFileProcessingException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
