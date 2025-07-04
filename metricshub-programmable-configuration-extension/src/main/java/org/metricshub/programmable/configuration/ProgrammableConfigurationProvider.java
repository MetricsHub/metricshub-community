package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
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
 * This class lists the .vm files under the configuration directory and loads
 * them as configuration fragments.
 */
@Slf4j
public class ProgrammableConfigurationProvider implements IConfigurationProvider {

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
					readVmFragment(path)
						.ifPresent((JsonNode jsonNode) -> {
							configurations.add(jsonNode);
							log.debug("Successfully loaded YAML configuration fragment: '{}'", path);
						})
				);
		} catch (IOException e) {
			log.error("Failed to list configuration directory: '{}'. Error: {}", configDirectory, e.getMessage());
			log.debug("Failed to list configuration directory: '{}'. Exception:", configDirectory, e);
		}

		final int size = configurations.size();
		log.info("Loaded {} Velocity configuration fragment{} from '{}'.", size, size > 1 ? "s" : "", configDirectory);
		return configurations;
	}

	/**
	 * This method reads the .vm file and returns the Optional JSON node
	 * representing the configuration.
	 *
	 * @param path           The path to the configuration file.
	 * @return An Optional containing the JSON node representing the configuration, or
	 *         an empty Optional if an error occurred.
	 */
	private Optional<JsonNode> readVmFragment(final Path path) {
		try {
			var loader = new VelocityConfigurationLoader(path);
			final String yaml = loader.generateYaml();
			if (yaml != null) {
				return Optional.of(YAML_MAPPER.readTree(yaml));
			}
		} catch (Exception e) {
			log.error("Failed to load Velocity configuration fragment: '{}'. Error: {}", path, e.getMessage());
			log.debug("Failed to load Velocity configuration fragment: '{}'. Exception:", path, e);
		}
		return Optional.empty();
	}

	/**
	 * Returns the set of file extensions that this configuration provider can handle.
	 */
	@Override
	public Set<String> getFileExtensions() {
		return Set.of(".vm");
	}
}
