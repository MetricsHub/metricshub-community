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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
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
	 * Allowed file extensions for configuration files.
	 */
	private static final Set<String> YAML_EXTENSIONS = Set.of(".yml", ".yaml");

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
				.filter(ConfigurationFilesService::hasYamlExtension)
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
		final Path file = resolveSafeYaml(dir, fileName);
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
		final Path target = resolveSafeYaml(dir, fileName);

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
	 * Deletes a configuration file.
	 *
	 * @param fileName the configuration file name
	 * @throws ConfigFilesException if the file cannot be deleted
	 */
	public void deleteFile(final String fileName) throws ConfigFilesException {
		final Path dir = getConfigDir();
		final Path file = resolveSafeYaml(dir, fileName);

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
		final Path source = resolveSafeYaml(dir, oldName);

		if (!Files.exists(source)) {
			throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Source configuration file not found.");
		}

		final Path target = resolveSafeYaml(dir, newName);
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
	 * Checks if the file has a valid YAML extension.
	 *
	 * @param path the path to the file
	 * @return true if the file has a valid YAML extension, false otherwise
	 */
	private static boolean hasYamlExtension(Path path) {
		// Make sure the file has a valid yaml extension
		final var fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return hasYamlExtension(fileName);
	}

	/**
	 * Checks if the file name has a valid YAML extension.
	 *
	 * @param fileName the file name we want to check
	 * @return true if the file name has a valid YAML extension, false otherwise
	 */
	private static boolean hasYamlExtension(final String fileName) {
		return YAML_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}

	/**
	 * Resolves and validates a YAML file name within a base directory (no
	 * traversal, correct extension).
	 *
	 * @param baseDir  configuration directory
	 * @param fileName simple file name (no path)
	 * @return resolved path
	 * @throws ConfigFilesException if the file name is invalid or resolution fails
	 */
	private Path resolveSafeYaml(final Path baseDir, final String fileName) throws ConfigFilesException {
		if (fileName == null || fileName.isBlank()) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "File name is required.");
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "Invalid file name.");
		}

		if (!hasYamlExtension(fileName.toLowerCase(Locale.ROOT))) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.INVALID_EXTENSION,
				"Only .yml or .yaml files are allowed."
			);
		}

		final Path normalizedBase = baseDir.normalize();
		final Path resolved = normalizedBase.resolve(fileName).normalize();

		// Security check: ensure the resolved path is within the base directory
		if (!resolved.startsWith(normalizedBase)) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_PATH, "Invalid file path.");
		}
		return resolved;
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
	 * Produces a trimmed, safe error message for JSON payloads.
	 *
	 * @param raw raw message
	 * @return sanitized message
	 */
	private static String safeMessage(final String raw) {
		if (raw == null) {
			return "Unknown error";
		}

		return raw;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Validation {

		private String fileName;
		private boolean isValid;
		private String error;

		/**
		 * Factory method for a successful validation result.
		 *
		 * @param fileName the name of the validated file
		 */
		public static Validation ok(String fileName) {
			return Validation.builder().fileName(fileName).isValid(true).error(null).build();
		}

		/**
		 * Factory method for a failed validation result.
		 *
		 * @param fileName the name of the validated file
		 * @param error    the validation error message
		 * @return a Validation instance representing a failed validation
		 */
		public static Validation fail(String fileName, String error) {
			return Validation.builder().fileName(fileName).isValid(false).error(error).build();
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
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null) {
			return Validation.fail(fileName, "Configuration directory is not available.");
		}

		try {
			final JsonNode configNode = JsonHelper.buildYamlMapper().readTree(content);
			agentContext.loadConfiguration(configNode);
			return Validation.ok(fileName);
		} catch (Exception e) {
			return Validation.fail(fileName, safeMessage(e.getMessage()));
		}
	}

	// === BACKUP FILE OPERATIONS ===
	private static final String BACKUP_DIR_NAME = "backup";

	/**
	 * Returns the content of a backup file as UTF-8 text.
	 *
	 * @param fileName the backup file name (e.g. mybackup.yaml)
	 * @return file content
	 * @throws ConfigFilesException if the file is not found or cannot be read
	 */
	public String getBackupFileContent(final String fileName) throws ConfigFilesException {
		final Path file = resolveBackupYaml(fileName);
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
		final Path target = resolveBackupYaml(fileName);
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
		final Path file = resolveBackupYaml(fileName);
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

	private Path getBackupDir() throws ConfigFilesException {
		return getConfigDir().resolve(BACKUP_DIR_NAME);
	}

	private Path resolveBackupYaml(final String fileName) throws ConfigFilesException {
		if (fileName == null || fileName.isBlank()) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "File name is required.");
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "Invalid file name.");
		}
		final var lower = fileName.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.INVALID_EXTENSION,
				"Only .yml or .yaml files are allowed for backup."
			);
		}
		final Path normalizedBase = getBackupDir().normalize();
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
		try (Stream<Path> files = Files.list(backupDir)) {
			return files
				.filter(Files::isRegularFile)
				.filter(p -> hasYamlExtension(p))
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
}
