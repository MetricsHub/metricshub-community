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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.metricshub.engine.common.helpers.StringHelper.protectCaseInsensitiveRegex;
import static org.springframework.util.Assert.state;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Utility class for handling OS commands, including local and remote execution.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OsCommandHelper {

	private static final Pattern SUDO_COMMAND_PATTERN = Pattern.compile(
		"%\\{SUDO:([^\\}]*)\\}",
		Pattern.CASE_INSENSITIVE
	);
	public static final BiFunction<String, String, File> TEMP_FILE_CREATOR = OsCommandHelper::createEmbeddedTempFile;

	/**
	 * Create the temporary embedded files in the given command line.
	 *
	 * @param commandLine              The command line to process.
	 * @param sudoInformation          The Sudo Information of the Os Command configuration.
	 * @param commandLineEmbeddedFiles A map of embedded files referenced in the command line.
	 * @param tempFileCreator          The function that creates a temporary file.
	 * @return A map with EmbeddedFile tags as keys and corresponding temporary File objects.
	 * @throws IOException If an error occurs during temp file creation.
	 */
	public static Map<String, File> createOsCommandEmbeddedFiles(
		@NonNull final String commandLine,
		final SudoInformation sudoInformation,
		@NonNull final Map<Integer, EmbeddedFile> commandLineEmbeddedFiles,
		@NonNull final BiFunction<String, String, File> tempFileCreator
	) throws IOException {
		final Map<String, File> embeddedTempFiles = new HashMap<>();
		try {
			final Matcher matcher = FILE_PATTERN.matcher(commandLine);
			while (matcher.find()) {
				// ${file::embedded-file-number} // NOSONAR on comment
				final Integer fileNumber = Integer.parseInt(matcher.group(1));

				// Example: ${file::embedded-file-number} // NOSONAR on comment
				final String fileNameRef = matcher.group();

				embeddedTempFiles.computeIfAbsent(
					fileNameRef,
					k -> {
						// The embedded file is available in the connector
						final EmbeddedFile embeddedFile = commandLineEmbeddedFiles.get(fileNumber);

						// This means there is a design problem
						state(
							embeddedFile != null,
							() -> "Cannot get the EmbeddedFile from the Connector. File name: " + fileNumber
						);
						final byte[] content = embeddedFile.getContent();

						// This means there is a design problem, the content can never be null
						state(content != null, () -> "EmbeddedFile content is null. File name: " + fileNumber);

						try {
							return createTempFileWithEmbeddedFileContent(embeddedFile, sudoInformation, tempFileCreator);
						} catch (final IOException e) {
							throw new TempFileCreationException(e);
						}
					}
				);
			}
			return Collections.unmodifiableMap(embeddedTempFiles);
		} catch (final Exception e) {
			//noinspection ResultOfMethodCallIgnored
			embeddedTempFiles.values().forEach(File::delete);
			if (e instanceof TempFileCreationException) {
				throw (IOException) e.getCause();
			}
			throw e;
		}
	}

	/**
	 * Create a temporary file with the content of the embeddedFile.
	 *
	 * @param embeddedFile    {@link EmbeddedFile} instance used to write the file content (mandatory)
	 * @param sudoInformation The Sudo Information of the Os Command configuration.
	 * @param tempFileCreator The function that creates a temporary file.
	 * @return The File.
	 * @throws IOException
	 */
	public static File createTempFileWithEmbeddedFileContent(
		final EmbeddedFile embeddedFile,
		final SudoInformation sudoInformation,
		final BiFunction<String, String, File> tempFileCreator
	) throws IOException {
		final String extension = embeddedFile.getFileExtension();
		final String baseName = embeddedFile.getBaseName();

		final File tempFile = tempFileCreator.apply(baseName, extension);
		final Path tempFilePath = Paths.get(tempFile.getAbsolutePath());
		final String contentAsString = embeddedFile.getContentAsString();

		// Should we replace SUDO commands?
		if (contentAsString.contains("%{SUDO:")) {
			try (BufferedWriter bufferedWriter = Files.newBufferedWriter(tempFilePath, StandardCharsets.UTF_8)) {
				bufferedWriter.write(replaceSudo(contentAsString, sudoInformation));
			}
		} else {
			Files.write(tempFilePath, embeddedFile.getContent());
		}

		return tempFile;
	}

	/**
	 * Create a temporary file with the given extension.<br>
	 * The temporary file name is prefixed with "metricshub_embedded_" to easily identify
	 * and clean up files in case of issues, preventing potential filesystem overloads.
	 *
	 * @param baseName  Base name of the file. (File name without extension).
	 * @param extension File's name suffix (e.g. .bat)
	 * @return {@link File} instance
	 */
	public static File createEmbeddedTempFile(final String baseName, final String extension) {
		try {
			return File.createTempFile("metricshub_embedded_" + baseName, extension);
		} catch (IOException e) {
			throw new TempFileCreationException(e);
		}
	}

	/**
	 * Replace the %{SUDO:xxx}% tag in the given text with the sudo command.
	 * The replacement is based on the configuration in the provided OsCommandConfiguration.
	 * If the useSudo configuration is enabled and the sudo command is associated with the specified file,
	 * it replaces the tag with the sudo command; otherwise, it replaces it with an empty string.
	 *
	 * @param text            The text containing %{SUDO:xxx}% tags to be replaced.
	 * @param sudoInformation The Sudo Information of the Os Command configuration.
	 * @return The text with %{SUDO:xxx}% tags replaced with the sudo command or empty string.
	 */
	public static String replaceSudo(final String text, final SudoInformation sudoInformation) {
		if (text == null || text.isBlank()) {
			return text;
		}

		final Optional<String> maybeSudoFile = getFileNameFromSudoCommand(text);

		final String sudoReplace = maybeSudoFile.isPresent() &&
			sudoInformation != null &&
			sudoInformation.isUseSudo() &&
			sudoInformation.useSudoCommands().contains(maybeSudoFile.get())
			? sudoInformation.sudoCommand()
			: EMPTY;

		return maybeSudoFile
			.map(fileName -> text.replaceAll(protectCaseInsensitiveRegex(String.format("%%{SUDO:%s}", fileName)), sudoReplace)
			)
			.orElse(text);
	}

	/**
	 * <p>Get the file name of the sudo pattern from the command.</p>
	 * <p>Example:</p>
	 * <p>"%{SUDO:fileName}" return "fileName"</p>
	 * @param command The command.
	 * @return An Optional with The file name if found otherwise an empty optional.
	 */
	public static Optional<String> getFileNameFromSudoCommand(@NonNull final String command) {
		final Matcher matcher = SUDO_COMMAND_PATTERN.matcher(command);
		return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
	}

	/**
	 * This class is used to manage exceptions that can be thrown by the functional interface
	 * implementations used to create embedded temporary files.
	 */
	static class TempFileCreationException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		TempFileCreationException(final IOException cause) {
			super(cause);
		}
	}
}
