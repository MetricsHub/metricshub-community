package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.deserialization.DeserializationFailure;
import org.metricshub.agent.deserialization.PostConfigDeserializer;
import org.metricshub.agent.deserialization.TrackingDeserializationProblemHandler;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.helper.PostConfigDeserializeHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.exception.ConfigFilesException;
import org.springframework.stereotype.Service;

/**
 * Service class for managing configuration files.
 */
@Slf4j
@Service
public class ConfigurationFilesService {

	/**
	 * Pattern to extract line and column information from error messages.
	 */
	private static final Pattern LINE_ERROR_PATTERN = Pattern.compile(
		"line (\\d+), column (\\d+)",
		Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
	);

	/**
	 * Set of all extensions the configuration-files service can manage.
	 * Includes YAML configuration files and Velocity template files.
	 */
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".yml", ".yaml", ".vm");

	/**
	 * Extension for draft configuration files.
	 */
	private static final String DRAFT_EXTENSION = ".draft";

	/**
	 * Name of the backup directory for configuration files.
	 */
	private static final String BACKUP_DIR_NAME = "backup";

	/**
	 * Provides access to the current {@link AgentContext} used for configuration
	 * processing.
	 */
	private final AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ConfigurationFilesService.
	 *
	 * @param agentContextHolder the AgentContextHolder to access the agent context
	 *                           and configuration directory.
	 */
	public ConfigurationFilesService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves a list of all configuration files with their metadata.
	 *
	 * @return A list of {@link ConfigurationFile} representing all configuration
	 *         files.
	 * @throws ConfigFilesException an IO error occurs when listing files
	 */
	public List<ConfigurationFile> getAllConfigurationFiles() throws ConfigFilesException {
		final Path configurationDirectory = getConfigDir();

		// Only list top-level YAML files, excluding any subdirectories (e.g., backup)
		try (Stream<Path> files = Files.list(configurationDirectory)) {
			return files
				.filter(Files::isRegularFile)
				.filter(ConfigurationFilesService::hasAllowedExtension)
				.map(this::buildConfigurationFile)
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(ConfigurationFile::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		} catch (Exception e) {
			log.error("Failed to list configuration directory: '{}'. Error: {}", configurationDirectory, e.getMessage());
			log.debug("Failed to list configuration directory: '{}'. Exception:", configurationDirectory, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to list configuration files.", e);
		}
	}

	/**
	 * Returns the content of a configuration file as UTF-8 text.
	 *
	 * @param fileName the configuration file name (e.g. metricshub.yaml)
	 * @return file content
	 * @throws ConfigFilesException if the file is not found or cannot be read
	 */
	public String getFileContent(final String fileName) throws ConfigFilesException {
		final Path dir = getConfigDir();
		final Path file = resolveSafeAllowed(dir, fileName);
		log.info("Reading config file: {}", file.toAbsolutePath());

		if (!Files.exists(file)) {
			throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Configuration file not found.");
		}
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Failed to read configuration file: '{}'. Error: {}", file, e.getMessage());
			log.debug("Failed to read configuration file: '{}'. Exception:", file, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to read configuration file.", e);
		}
	}

	/**
	 * Saves or updates the content of a configuration file (atomic write).
	 *
	 * @param fileName the configuration file name
	 * @param content  the content to write
	 * @return the saved ConfigurationFile with metadata
	 * @throws ConfigFilesException if the file cannot be written
	 */
	public ConfigurationFile saveOrUpdateFile(final String fileName, final String content) throws ConfigFilesException {
		final Path dir = getConfigDir();
		final Path target = resolveSafeAllowed(dir, fileName);

		try {
			Files.createDirectories(dir);
			// Write to a temp file, then move atomically
			final Path tmp = Files.createTempFile(dir, fileName + ".", ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

			try {
				// Write atomically via temp file + move (ensures no partial writes, even if
				// process crashes)
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				log.info("Atomic move not supported, falling back to non-atomic move.");
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}

			return buildConfigurationFile(target);
		} catch (IOException e) {
			log.error("Failed to save configuration file: '{}'. Error: {}", target, e.getMessage());
			log.debug("Failed to save configuration file: '{}'. Exception:", target, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to save configuration file.", e);
		}
	}

	/**
	 * Saves or updates the content of a draft configuration file (atomic write).
	 *
	 * @param fileName the configuration file name
	 * @param content  the content to write
	 * @return the saved ConfigurationFile with metadata
	 * @throws ConfigFilesException if the file cannot be written
	 */
	public ConfigurationFile saveOrUpdateDraftFile(final String fileName, final String content)
		throws ConfigFilesException {
		final Path dir = getConfigDir();
		String draftFileName = fileName;
		if (!draftFileName.endsWith(DRAFT_EXTENSION)) {
			draftFileName += DRAFT_EXTENSION;
		}
		final Path target = resolveSafeDraft(dir, draftFileName);

		try {
			Files.createDirectories(dir);
			// Write to a temp file, then move atomically
			final Path tmp = Files.createTempFile(dir, draftFileName + ".", ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

			try {
				// Write atomically via temp file + move (ensures no partial writes, even if
				// process crashes)
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				log.info("Atomic move not supported, falling back to non-atomic move.");
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}

			return buildConfigurationFile(target);
		} catch (IOException e) {
			log.error("Failed to save draft configuration file: '{}'. Error: {}", target, e.getMessage());
			log.debug("Failed to save draft configuration file: '{}'. Exception:", target, e);
			throw new ConfigFilesException(
				ConfigFilesException.Code.IO_FAILURE,
				"Failed to save draft configuration file.",
				e
			);
		}
	}

	/**
	 * Deletes a configuration file.
	 *
	 * @param fileName the configuration file name
	 * @throws ConfigFilesException if the file cannot be deleted
	 */
	public void deleteFile(final String fileName) throws ConfigFilesException {
		final Path dir = getConfigDir();
		final Path file = resolveSafeAllowed(dir, fileName);

		try {
			if (!Files.deleteIfExists(file)) {
				throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Configuration file not found.");
			}
		} catch (IOException e) {
			log.error("Failed to delete configuration file: '{}'. Error: {}", file, e.getMessage());
			log.debug("Failed to delete configuration file: '{}'. Exception:", file, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to delete configuration file.", e);
		}
	}

	/**
	 * Renames (moves) a configuration file to a new name within the same directory.
	 *
	 * @param oldName existing file name
	 * @param newName target file name (must be .yml/.yaml and not exist)
	 * @return the renamed ConfigurationFile with metadata
	 * @throws ConfigFilesException if source file is not found, target exists, or
	 *                              IO failure while renaming
	 */
	public ConfigurationFile renameFile(final String oldName, final String newName) throws ConfigFilesException {
		final Path dir = getConfigDir();
		final Path source = resolveSafeAllowed(dir, oldName);

		if (!Files.exists(source)) {
			throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Source configuration file not found.");
		}

		// Ensure the file stays within the same extension family
		final boolean sourceIsVm = isVmFile(oldName);
		final boolean targetIsVm = isVmFile(newName);
		if (sourceIsVm != targetIsVm) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.INVALID_EXTENSION,
				"Cannot change file type during rename. Keep the same extension family (.vm or .yaml/.yml)."
			);
		}

		final Path target = resolveSafeAllowed(dir, newName);
		if (Files.exists(target)) {
			throw new ConfigFilesException(ConfigFilesException.Code.TARGET_EXISTS, "Target file name already exists.");
		}

		try {
			try {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				log.info("Atomic move not supported, falling back to non-atomic move.");
				Files.move(source, target);
			}
			return buildConfigurationFile(target);
		} catch (IOException e) {
			log.error("Failed to rename configuration file: '{}' -> '{}'. Error: {}", source, target, e.getMessage());
			log.debug("Failed to rename configuration file: '{}' -> '{}'. Exception:", source, target, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to rename configuration file.", e);
		}
	}

	/**
	 * Builds a {@link ConfigurationFile} from the given file path.
	 *
	 * @param path the path to the configuration file
	 * @return the ConfigurationFile with file metadata, or null if an error occurs
	 */
	private ConfigurationFile buildConfigurationFile(final Path path) {
		try {
			return ConfigurationFile
				.builder()
				.name(path.getFileName().toString())
				.size(Files.size(path))
				.lastModificationTime(Files.getLastModifiedTime(path).toString())
				.build();
		} catch (IOException e) {
			log.error("Failed to read configuration file metadata: '{}'. Error: {}", path, e.getMessage());
			log.debug("Failed to read configuration file metadata: '{}'. Exception:", path, e);
			return null;
		}
	}

	/**
	 * Checks if the file name has an allowed extension (.yml, .yaml, or .vm),
	 * including when the file is a draft (e.g., "config.vm.draft").
	 *
	 * @param fileName the file name to check
	 * @return true if the extension is allowed
	 */
	private static boolean hasAllowedExtension(final String fileName) {
		final String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(DRAFT_EXTENSION)) {
			final String baseName = lower.substring(0, lower.length() - DRAFT_EXTENSION.length());
			return ALLOWED_EXTENSIONS.stream().anyMatch(baseName::endsWith);
		}
		return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	/**
	 * Checks if the file at the given path has an allowed extension.
	 *
	 * @param path the path to the file
	 * @return true if the extension is allowed
	 */
	private static boolean hasAllowedExtension(final Path path) {
		return hasAllowedExtension(path.getFileName().toString());
	}

	/**
	 * Checks if the file name is a Velocity template file (.vm), possibly with a
	 * .draft suffix.
	 *
	 * @param fileName the file name to check
	 * @return true if the file has a .vm extension
	 */
	public static boolean isVmFile(final String fileName) {
		final String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(DRAFT_EXTENSION)) {
			return lower.substring(0, lower.length() - DRAFT_EXTENSION.length()).endsWith(".vm");
		}
		return lower.endsWith(".vm");
	}

	/**
	 * Resolves and validates a file name with an allowed extension within a base
	 * directory (no traversal, correct extension).
	 *
	 * @param baseDir  configuration directory
	 * @param fileName simple file name (no path)
	 * @return resolved path
	 * @throws ConfigFilesException if the file name is invalid or resolution fails
	 */
	private Path resolveSafeAllowed(final Path baseDir, final String fileName) throws ConfigFilesException {
		ensureAllowedExtension(fileName, "Only .yml, .yaml, .vm or .draft files are allowed.");
		return resolveWithinDir(baseDir, fileName);
	}

	/**
	 * Ensures a file has an allowed extension, otherwise throws a
	 * {@link ConfigFilesException} with the provided message.
	 *
	 * @param fileName     the file name to validate
	 * @param errorMessage the error message to use when the extension is invalid
	 * @throws ConfigFilesException when the file does not have an allowed extension
	 */
	private static void ensureAllowedExtension(final String fileName, final String errorMessage)
		throws ConfigFilesException {
		validateSimpleFileName(fileName);
		if (!hasAllowedExtension(fileName)) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_EXTENSION, errorMessage);
		}
	}

	/**
	 * Resolves and validates a draft YAML file name within a base directory (no
	 * traversal, correct extension).
	 *
	 * @param baseDir  configuration directory
	 * @param fileName simple file name (no path)
	 * @return resolved path
	 * @throws ConfigFilesException if the file name is invalid or resolution fails
	 */
	private Path resolveSafeDraft(final Path baseDir, final String fileName) throws ConfigFilesException {
		validateSimpleFileName(fileName);

		final String baseName = fileName.substring(0, fileName.length() - DRAFT_EXTENSION.length());
		if (!hasAllowedExtension(baseName)) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.INVALID_EXTENSION,
				"Draft files must have a .yml, .yaml or .vm extension."
			);
		}
		return resolveWithinDir(baseDir, fileName);
	}

	/**
	 * Ensures the agent configuration directory is available.
	 *
	 * @return configuration directory path
	 * @throws ConfigFilesException if the directory is not available
	 */
	private Path getConfigDir() throws ConfigFilesException {
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getConfigDirectory() == null) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE,
				"Configuration directory is not available."
			);
		}
		return agentContext.getConfigDirectory();
	}

	/**
	 * Result object returned by
	 * {@link ConfigurationFilesService#validate(String, String)}.
	 * It carries the evaluated file name, the validation status and any collected
	 * errors.
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Validation {

		/** Name of the validated file. */
		private String fileName;
		/** Flag indicating whether validation succeeded. */
		private boolean isValid;

		/** Errors gathered during validation when {@link #isValid} is {@code false}. */
		@Default
		private Set<DeserializationFailure.Error> errors = new LinkedHashSet<>();

		/**
		 * Factory method for a successful validation result.
		 *
		 * @param fileName the name of the validated file
		 */
		public static Validation ok(String fileName) {
			return Validation.builder().fileName(fileName).isValid(true).build();
		}

		/**
		 * Factory method for a failed validation result.
		 *
		 * @param fileName the name of the validated file
		 * @param failure  the deserialization failure containing error details
		 * @param e        an optional exception that caused the failure
		 * @return a Validation instance representing a failed validation
		 */
		public static Validation fail(String fileName, DeserializationFailure failure, Exception e) {
			if (failure.isEmpty() && e != null) {
				failure.addError(e.getMessage());
			}
			return Validation.builder().fileName(fileName).isValid(false).errors(failure.getErrors()).build();
		}

		/**
		 * Factory method for a failed validation result without an exception.
		 *
		 * @param fileName the name of the validated file
		 * @param failure  the deserialization failure containing error details
		 * @return a Validation instance representing a failed validation
		 */
		public static Validation fail(String fileName, DeserializationFailure failure) {
			return fail(fileName, failure, null);
		}

		/**
		 * Retrieve the message of the first recorded error.
		 *
		 * @return the message of the first error or an empty string when none are
		 *         recorded
		 */
		@JsonIgnore
		public String getFirst() {
			if (errors == null || errors.isEmpty()) {
				return "";
			}

			return errors.stream().findFirst().map(DeserializationFailure.Error::getMessage).orElseGet(() -> "");
		}
	}

	/**
	 * Semantic validation: parses YAML into JsonNode and tries to load
	 * AgentConfig.<br>
	 * Any parsing/IO/schema/engine failure => invalid.
	 *
	 * @param content  the content to validate; if null, validates an empty config.
	 * @param fileName the configuration file name to include in the response
	 * @return validation result
	 */
	public Validation validate(final String content, final String fileName) {
		final var deserializationFailure = new DeserializationFailure();
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null) {
			deserializationFailure.addError("Configuration directory is not available.");
			return Validation.fail(fileName, deserializationFailure);
		}

		final var tracking = new TrackingDeserializationProblemHandler(deserializationFailure);

		try {
			final var mapper = AgentContext.newAgentConfigObjectMapper(agentContext.getExtensionManager());
			mapper.addHandler(tracking);
			mapper.readValue(content, AgentConfig.class);
			return Validation.ok(fileName);
		} catch (JsonProcessingException e) {
			enrichErrors(deserializationFailure, e);
			return Validation.fail(fileName, deserializationFailure, e);
		} catch (Exception e) {
			if (deserializationFailure.isEmpty()) {
				enrichErrors(deserializationFailure, e.getMessage());
			}
			return Validation.fail(fileName, deserializationFailure, e);
		}
	}

	/**
	 * Returns the content of a backup file as UTF-8 text.
	 *
	 * @param fileName the backup file name (e.g. mybackup.yaml)
	 * @return file content
	 * @throws ConfigFilesException if the file is not found or cannot be read
	 */
	public String getBackupFileContent(final String fileName) throws ConfigFilesException {
		final Path file = resolveBackupFile(fileName);
		log.info("Reading backup file: {}", file.toAbsolutePath());
		if (!Files.exists(file)) {
			throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Backup file not found.");
		}
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Failed to read backup file: '{}'. Error: {}", file, e.getMessage());
			log.debug("Failed to read backup file: '{}'. Exception:", file, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to read backup file.", e);
		}
	}

	/**
	 * Saves or updates the content of a backup file (atomic write).
	 *
	 * @param fileName the backup file name
	 * @param content  the content to write
	 * @return the saved ConfigurationFile with metadata
	 * @throws ConfigFilesException if the file cannot be written
	 */
	public ConfigurationFile saveOrUpdateBackupFile(final String fileName, final String content)
		throws ConfigFilesException {
		final Path dir = getBackupDir();
		final Path target = resolveBackupFile(fileName);
		try {
			Files.createDirectories(dir);
			final Path tmp = Files.createTempFile(dir, fileName + ".", ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				log.info("Atomic move not supported for backup file, falling back to non-atomic move.");
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return buildConfigurationFile(target);
		} catch (IOException e) {
			log.error("Failed to save backup file: '{}'. Error: {}", target, e.getMessage());
			log.debug("Failed to save backup file: '{}'. Exception:", target, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to save backup file.", e);
		}
	}

	/**
	 * Deletes a backup file.
	 *
	 * @param fileName the backup file name
	 * @throws ConfigFilesException if the file cannot be deleted
	 */
	public void deleteBackupFile(final String fileName) throws ConfigFilesException {
		final Path file = resolveBackupFile(fileName);
		try {
			if (!Files.deleteIfExists(file)) {
				throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Backup file not found.");
			}
		} catch (IOException e) {
			log.error("Failed to delete backup file: '{}'. Error: {}", file, e.getMessage());
			log.debug("Failed to delete backup file: '{}'. Exception:", file, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to delete backup file.", e);
		}
	}

	/**
	 * Ensures the backup directory is available.
	 *
	 * @return backup directory path
	 * @throws ConfigFilesException if the directory is not available
	 */
	private Path getBackupDir() throws ConfigFilesException {
		return getConfigDir().resolve(BACKUP_DIR_NAME);
	}

	/**
	 * Resolves and validates a backup YAML file name within the backup directory
	 * (no traversal, correct extension).
	 *
	 * @param fileName simple file name (no path)
	 * @return resolved path
	 * @throws ConfigFilesException if the file name is invalid or resolution fails
	 */
	private Path resolveBackupFile(final String fileName) throws ConfigFilesException {
		ensureAllowedExtension(fileName, "Only .yml, .yaml, .vm or .draft files are allowed for backup.");
		return resolveWithinDir(getBackupDir(), fileName);
	}

	/**
	 * Validates that the given file name is a simple name without path traversal
	 * or separators and is not blank.
	 *
	 * @param fileName the file name to validate
	 * @throws ConfigFilesException if the file name is null/blank, contains path
	 *                              separators or parent directory references
	 */
	private static void validateSimpleFileName(final String fileName) throws ConfigFilesException {
		if (fileName == null || fileName.isBlank()) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "File name is required.");
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "Invalid file name.");
		}
	}

	/**
	 * Resolves a simple file name within a base directory, ensuring no path
	 * traversal and that the result stays within the base directory.
	 *
	 * @param baseDir  the directory to resolve against
	 * @param fileName the simple file name (no path separators)
	 * @return the normalized, resolved path
	 * @throws ConfigFilesException if the file name is invalid or would escape the
	 *                              base directory
	 */
	private static Path resolveWithinDir(final Path baseDir, final String fileName) throws ConfigFilesException {
		validateSimpleFileName(fileName);
		final Path normalizedBase = baseDir.normalize();
		final Path resolved = normalizedBase.resolve(fileName).normalize();
		if (!resolved.startsWith(normalizedBase)) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_PATH, "Invalid file path.");
		}
		return resolved;
	}

	/**
	 * Lists all backup YAML files in the backup directory, returning their
	 * metadata.
	 *
	 * @return List of ConfigurationFile DTOs for backup files
	 * @throws ConfigFilesException if IO error occurs
	 */
	public List<ConfigurationFile> listAllBackupFiles() throws ConfigFilesException {
		final Path backupDir = getBackupDir();
		// If backup directory does not exist, return an empty list instead of throwing
		// an exception to avoid a 500 on the frontend.
		if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(backupDir)) {
			return files
				.filter(Files::isRegularFile)
				.filter(ConfigurationFilesService::hasAllowedExtension)
				.map(this::buildConfigurationFile)
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(ConfigurationFile::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		} catch (IOException e) {
			log.error("Failed to list backup directory: '{}'. Error: {}", backupDir, e.getMessage());
			log.debug("Failed to list backup directory: '{}'. Exception:", backupDir, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to list backup files.", e);
		}
	}

	/**
	 * Enriches deserialization errors with line/column information from
	 * JsonProcessingException.
	 *
	 * @param deserializationFailure the DeserializationFailure to enrich
	 * @param e                      the JsonProcessingException containing location
	 *                               info
	 */
	private static void enrichErrors(final DeserializationFailure deserializationFailure, JsonProcessingException e) {
		if (!deserializationFailure.isEmpty()) {
			return;
		}

		// try to extract the SnakeYAML location if present in message
		final String msg = e.getOriginalMessage();

		// first try to extract line/column from the message
		if (enrichErrors(deserializationFailure, msg)) {
			return;
		}

		// errors already tracked by the problem handler
		final JsonLocation loc = e.getLocation();
		int line = loc != null ? loc.getLineNr() : -1;
		int column = loc != null ? loc.getColumnNr() : -1;

		// Whatsoever, add the original message
		deserializationFailure.addError(msg, line, column);
	}

	/**
	 * Enriches deserialization errors by parsing line/column info from the error
	 * message.
	 *
	 * @param deserializationFailure the DeserializationFailure to enrich
	 * @param msg                    the error message potentially containing
	 *                               line/column info
	 * @return true if line/column info was found and added, false otherwise
	 */
	private static boolean enrichErrors(final DeserializationFailure deserializationFailure, final String msg) {
		final var matcher = LINE_ERROR_PATTERN.matcher(msg);
		var found = false;
		while (matcher.find()) {
			deserializationFailure.addError(msg, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
			found = true;
		}
		return found;
	}

	/**
	 * Create a new {@link ObjectMapper} instance then add to it the
	 * {@link PostConfigDeserializer}
	 *
	 * @param extensionManager Manages and aggregates various types of extensions
	 *                         used within MetricsHub.
	 * @return new {@link ObjectMapper} instance
	 */
	public static ObjectMapper newAgentConfigYamlMapper(final ExtensionManager extensionManager) {
		final ObjectMapper objectMapper = ConfigHelper.newObjectMapper();

		PostConfigDeserializeHelper.addPostDeserializeSupport(objectMapper);

		// Inject the extension manager in the deserialization context
		final var injectableValues = new InjectableValues.Std();
		injectableValues.addValue(ExtensionManager.class, extensionManager);
		objectMapper.setInjectableValues(injectableValues);

		return objectMapper;
	}
}
