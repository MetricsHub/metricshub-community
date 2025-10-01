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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

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

	/**
	 * Maximum directory traversal depth when searching for configuration files.
	 */
	private static final int MAX_DEPTH = 1;

	private final AgentContextHolder agentContextHolder;
	private final Yaml yaml = new Yaml();

	@Autowired
	public ConfigurationFilesService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves a list of all configuration files with their metadata.
	 *
	 * @return A list of {@link ConfigurationFile} representing all configuration
	 *         files.
	 */
	public List<ConfigurationFile> getAllConfigurationFiles() {
		final Path configurationDirectory = getConfigDir();

		try (
			Stream<Path> files = Files.find(configurationDirectory, MAX_DEPTH, ConfigurationFilesService::isRegularYamlFile)
		) {
			return files
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
	 */
	public String getFileContent(final String fileName) {
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
	 */
	public void saveOrUpdateFile(final String fileName, final String content) {
		final Path dir = getConfigDir();
		final Path target = resolveSafeYaml(dir, fileName);

		try {
			Files.createDirectories(dir);
			// Write to a temp file, then move atomically
			final Path tmp = Files.createTempFile(dir, fileName + ".", ".tmp");
			Files.writeString(
				tmp,
				content == null ? "" : content,
				StandardCharsets.UTF_8,
				StandardOpenOption.TRUNCATE_EXISTING
			);
			try {
				// Write atomically via temp file + move (ensures no partial writes, even if
				// process crashes)
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("Failed to save configuration file: '{}'. Error: {}", target, e.getMessage());
			log.debug("Failed to save configuration file: '{}'. Exception:", target, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to save configuration file.", e);
		}
	}

	/**
	 * Validates the YAML content of a configuration file.
	 * Returns a JSON object: { "valid": boolean, "errors": [ ... ] }
	 *
	 * @param fileName for context in messages
	 * @param content  YAML content to validate
	 * @return validation result JSON
	 */
	public ObjectNode validateFile(final String fileName, final String content) {
		final ObjectNode result = JsonNodeFactory.instance.objectNode();
		try {
			yaml.load(content == null ? "" : content);
			result.put("valid", true);
			result.putArray("errors");
			return result;
		} catch (MarkedYAMLException ye) {
			final var errors = result.put("valid", false).putArray("errors");
			errors.add(safeMessage("YAML syntax error in '%s': %s".formatted(fileName, ye.getMessage())));
			return result;
		} catch (Exception e) {
			final var errors = result.put("valid", false).putArray("errors");
			errors.add(safeMessage("Validation error in '%s': %s".formatted(fileName, e.getMessage())));
			return result;
		}
	}

	/**
	 * Validates YAML content and throws 400 if invalid.
	 */
	public void validateOrThrow(final String fileName, final String content) {
		try {
			yaml.load(content == null ? "" : content);
		} catch (MarkedYAMLException ye) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.VALIDATION_FAILED,
				safeMessage("YAML syntax error in '%s': %s".formatted(fileName, ye.getMessage())),
				ye
			);
		} catch (Exception e) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.VALIDATION_FAILED,
				safeMessage("Validation error in '%s': %s".formatted(fileName, e.getMessage())),
				e
			);
		}
	}

	/**
	 * Deletes a configuration file.
	 *
	 * @param fileName the configuration file name
	 */
	public void deleteFile(final String fileName) {
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
	 */
	public void renameFile(final String oldName, final String newName) {
		final Path dir = getConfigDir();
		final Path source = resolveSafeYaml(dir, oldName);
		final Path target = resolveSafeYaml(dir, newName);

		if (!Files.exists(source)) {
			throw new ConfigFilesException(ConfigFilesException.Code.FILE_NOT_FOUND, "Source configuration file not found.");
		}
		if (Files.exists(target)) {
			throw new ConfigFilesException(ConfigFilesException.Code.TARGET_EXISTS, "Target file name already exists.");
		}

		try {
			try {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException amnse) {
				Files.move(source, target);
			}
		} catch (IOException e) {
			log.error("Failed to rename configuration file: '{}' -> '{}'. Error: {}", source, target, e.getMessage());
			log.debug("Failed to rename configuration file: '{}' -> '{}'. Exception:", source, target, e);
			throw new ConfigFilesException(ConfigFilesException.Code.IO_FAILURE, "Failed to rename configuration file.", e);
		}
	}

	/**
	 * Checks if the given path is a regular file with a YAML extension.
	 *
	 * @param path                The path to the file.
	 * @param basicFileAttributes The file attributes.
	 * @return true if the file is a regular file and has a YAML extension, false
	 *         otherwise.
	 */
	private static boolean isRegularYamlFile(final Path path, final BasicFileAttributes basicFileAttributes) {
		return basicFileAttributes.isRegularFile() && hasYamlExtension(path);
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
		return YAML_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}

	/**
	 * Resolves and validates a YAML file name within a base directory (no
	 * traversal, correct extension).
	 *
	 * @param baseDir  configuration directory
	 * @param fileName simple file name (no path)
	 * @return resolved path
	 */
	private Path resolveSafeYaml(final Path baseDir, final String fileName) {
		if (fileName == null || fileName.isBlank()) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "File name is required.");
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_FILE_NAME, "Invalid file name.");
		}

		final var lower = fileName.toLowerCase(Locale.ROOT);
		if (YAML_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
			throw new ConfigFilesException(
				ConfigFilesException.Code.INVALID_EXTENSION,
				"Only .yml or .yaml files are allowed."
			);
		}

		final Path normalizedBase = baseDir.normalize();
		final Path resolved = normalizedBase.resolve(fileName).normalize();

		if (!resolved.startsWith(normalizedBase)) {
			throw new ConfigFilesException(ConfigFilesException.Code.INVALID_PATH, "Invalid file path.");
		}
		return resolved;
	}

	/**
	 * Ensures the agent configuration directory is available.
	 *
	 * @return configuration directory path
	 */
	private Path getConfigDir() {
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
	private String safeMessage(final String raw) {
		if (raw == null) {
			return "Unknown error";
		}
		final String trimmed = raw.strip();
		return trimmed.length() > 1000 ? trimmed.substring(0, 1000) + "…" : trimmed;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Validation {

		private String fileName;
		private boolean isValid;
		private String error;

		public static Validation ok(String fileName) {
			return Validation.builder().fileName(fileName).isValid(true).error(null).build();
		}

		public static Validation fail(String fileName, String error) {
			return Validation.builder().fileName(fileName).isValid(false).error(error).build();
		}
	}

	private void tryLoadAgentConfigViaReflection(Object agentContext, JsonNode configNode) throws Exception {
		Method m = agentContext.getClass().getDeclaredMethod("loadConfiguration", JsonNode.class);
		m.setAccessible(true);
		m.invoke(agentContext, configNode);
	}

	/**
	 * Semantic validation: parses YAML into JsonNode and tries to load AgentConfig.
	 * Any parsing/IO/schema/engine failure => invalid.
	 */
	public Validation validate(final String content, final String fileName) {
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null) {
			return Validation.fail(fileName, "Configuration directory is not available.");
		}

		try {
			final String src = content == null ? "" : content;
			JsonNode configNode = JsonHelper.buildYamlMapper().readTree(src);
			tryLoadAgentConfigViaReflection(agentContext, configNode);
			return Validation.ok(fileName);
		} catch (Exception e) {
			return Validation.fail(fileName, safeMessage(e.getMessage()));
		}
	}
}
