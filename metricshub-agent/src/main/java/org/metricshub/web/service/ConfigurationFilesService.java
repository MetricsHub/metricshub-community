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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConfigurationFile;
import org.springframework.beans.factory.annotation.Autowired;
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

	/**
	 * Maximum directory traversal depth when searching for configuration files.
	 */
	private static final int MAX_DEPTH = 1;

	private AgentContextHolder agentContextHolder;

	@Autowired
	public ConfigurationFilesService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves a list of all configuration files with their metadata.
	 *
	 * @return A list of {@link ConfigurationFile} representing all configuration files.
	 */
	public List<ConfigurationFile> getAllConfigurationFiles() {
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext != null) {
			final var configurationDirectory = agentContext.getConfigDirectory();
			// List all the files
			try (
				Stream<Path> files = Files.find(
					configurationDirectory,
					MAX_DEPTH,
					ConfigurationFilesService::keepOnlyRegularYamlFile
				)
			) {
				return files
					.map(this::buildConfigurationFile)
					.filter(Objects::nonNull)
					.sorted(Comparator.comparing(ConfigurationFile::getName, String.CASE_INSENSITIVE_ORDER))
					.toList();
			} catch (Exception e) {
				log.error("Failed to list configuration directory: '{}'. Error: {}", configurationDirectory, e.getMessage());
				log.debug("Failed to list configuration directory: '{}'. Exception:", configurationDirectory, e);
			}
		}
		return List.of();
	}

	/**
	 * Predicate to filter only regular files with YAML extensions.
	 * @param path                The path to the file.
	 * @param basicFileAttributes The file attributes.
	 * @return true if the file is a regular file and has a YAML extension, false otherwise.
	 */
	private static boolean keepOnlyRegularYamlFile(final Path path, final BasicFileAttributes basicFileAttributes) {
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
}
