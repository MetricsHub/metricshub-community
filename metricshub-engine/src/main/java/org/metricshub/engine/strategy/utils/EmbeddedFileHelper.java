package org.metricshub.engine.strategy.utils;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * The {@code EmbeddedFileHelper} class provides utility methods for handling embedded files in various contexts, such as
 * command lines, AWK directives, headers, and bodies. It allows finding and processing file references in a given string.
 * The class is designed to have a private no-argument constructor to prevent instantiation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class EmbeddedFileHelper {

	/**
	 * Finds all the embedded files that are referenced in the given string.
	 *
	 * @param value                  The value can be a command line or any value referencing multiple embedded files.
	 * @param connectorEmbeddedFiles All the embedded files referenced in the connector.
	 * @return A map of the file reference to {@link EmbeddedFile} instance.
	 */
	public static Map<Integer, EmbeddedFile> findEmbeddedFiles(
		@NonNull final String value,
		@NonNull final Map<Integer, EmbeddedFile> connectorEmbeddedFiles
	) {
		final Map<Integer, EmbeddedFile> embeddedFiles = new HashMap<>();
		final List<Integer> alreadyProcessedFiles = new ArrayList<>();

		final Matcher fileMatcher = FILE_PATTERN.matcher(value);

		// Verify the embedded file reference. Example: ${file::embedded-file-number} // NOSONAR on comment
		while (fileMatcher.find()) {
			// The number of the embedded file
			final Integer fileNumber = Integer.parseInt(fileMatcher.group(1));

			// If the embeddedFile has already been processed, no need to continue
			if (!alreadyProcessedFiles.contains(fileNumber)) {
				embeddedFiles.put(fileNumber, connectorEmbeddedFiles.get(fileNumber));
				alreadyProcessedFiles.add(fileNumber);
			}
		}

		return embeddedFiles;
	}

	/**
	 * Finds one embedded file that is referenced in the given string.
	 *
	 * @param value                  The value which references the embedded file, it can be an AWK directive, header, body, etc.
	 * @param connectorEmbeddedFiles All the embedded files referenced in the connector.
	 * @param hostname               The hostname of the host being monitored and used to log the failure where many embedded files are retrieved.
	 * @param connectorId            The identifier of the connector used for logging.
	 * @return An Optional of {@link EmbeddedFile} instance.
	 */
	public static Optional<EmbeddedFile> findEmbeddedFile(
		@NonNull final String value,
		@NonNull final Map<Integer, EmbeddedFile> connectorEmbeddedFiles,
		@NonNull String hostname,
		@NonNull String connectorId
	) {
		final Map<Integer, EmbeddedFile> embeddedFiles = findEmbeddedFiles(value, connectorEmbeddedFiles);

		if (embeddedFiles.size() > 1) {
			final String message = String.format(
				"Hostname %s - Many embedded files are referenced in value: %s. Expected 1 embedded file reference. Connector: %s.",
				hostname,
				value,
				connectorId
			);
			log.error(message);
			throw new IllegalStateException(message);
		}

		return embeddedFiles.entrySet().stream().map(Entry::getValue).findFirst();
	}
}
