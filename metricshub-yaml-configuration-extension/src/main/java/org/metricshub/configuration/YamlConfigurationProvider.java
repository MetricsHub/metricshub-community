package org.metricshub.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub YAML Configuration Extension
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.extension.IConfigurationProvider;

/**
 * This class lists the YAML files under the configuration directory and loads
 * them as configuration fragments.
 */
@Slf4j
public class YamlConfigurationProvider implements IConfigurationProvider {

	private static final ObjectMapper YAML_MAPPER = JsonHelper.buildYamlMapper();

	@Override
	public Collection<JsonNode> load(final Path configDirectory) {
		final List<JsonNode> configurations = new ArrayList<>();

		try (Stream<Path> stream = Files.list(configDirectory)) {
			stream
				.filter((Path path) -> !Files.isDirectory(path))
				.filter((Path path) -> {
					String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
					return getFileExtensions().stream().anyMatch(fileName::endsWith);
				})
				.forEach((Path path) ->
					readFragment(path)
						.ifPresent((JsonNode jsonNode) -> {
							configurations.add(jsonNode);
							log.debug("Successfully loaded YAML configuration fragment: '{}'", path);
						})
				);
		} catch (IOException e) {
			log.error("Failed to list configuration directory: '{}'. Error: {}", configDirectory, e.getMessage());
			if (log.isDebugEnabled()) {
				log.debug("Failed to list configuration directory: '{}'. Exception:", configDirectory, e);
			}
		}

		final int size = configurations.size();
		log.info("Loaded {} YAML configuration fragment{} from '{}'.", size > 1 ? "s" : "", size, configDirectory);
		return configurations;
	}

	/**
	 * This method reads the YAML file and returns the Optional JSON node
	 * representing the configuration.
	 *
	 * @param path           The path to the configuration file.
	 * @return An Optional containing the JSON node representing the configuration, or
	 *         an empty Optional if an error occurred.
	 */
	private Optional<JsonNode> readFragment(final Path path) {
		try {
			return Optional.of(YAML_MAPPER.readTree(path.toFile()));
		} catch (IOException e) {
			log.error("Failed to load YAML configuration fragment: '{}'. Error: {}", path, e.getMessage());
			if (log.isDebugEnabled()) {
				log.debug("Failed to load YAML configuration fragment: '{}'. Exception:", path, e);
			}
			return Optional.empty();
		}
	}

	@Override
	public Set<String> getFileExtensions() {
		return Set.of("yaml", "yml");
	}
}
