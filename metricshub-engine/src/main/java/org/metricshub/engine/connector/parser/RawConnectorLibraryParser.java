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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.RawConnector;

/**
 * Parses connectors from YAML files in a given directory to a
 * RawConnectorStore.
 */
@Slf4j
public class RawConnectorLibraryParser {

	public static final String CONNECTOR_PARSING_ERROR = "Error while parsing connector {}: {}";

	/**
	 * The ObjectMapper instance for handling YAML files.
	 */
	public static final ObjectMapper OBJECT_MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * This inner class allows to visit the files contained within the Yaml
	 * directory
	 */
	private class ConnectorFileVisitor extends SimpleFileVisitor<Path> {

		/**
		 * Case-insensitive map of connector IDs to their {@link RawConnector} definitions.
		 */
		private final Map<String, RawConnector> connectorsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		/**
		 * Returns the map of connector IDs and their {@link RawConnector} instances.
		 *
		 * @return case-insensitive map of connectors
		 */
		public Map<String, RawConnector> getConnectorsMap() {
			return connectorsMap;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			final String filename = file.getFileName().toString();

			// Skip this path if it is a directory or not a YAML file
			if (Files.isDirectory(file) || !isYamlFile(file.toFile().getName())) {
				return FileVisitResult.CONTINUE;
			}

			final JsonNode connectorNode = OBJECT_MAPPER.readTree(file.toFile());
			if (!isConnector(connectorNode)) {
				return FileVisitResult.CONTINUE;
			}
			final ConnectorParser connectorParser = ConnectorParser.withNodeProcessorAndUpdateChain(file.getParent());

			try {
				final RawConnector connector = connectorParser.parseRaw(file.toFile());
				connectorsMap.put(filename.substring(0, filename.lastIndexOf('.')), connector);
			} catch (Exception e) {
				log.error(CONNECTOR_PARSING_ERROR, filename, e.getMessage());
				log.debug("Exception: ", e);
			}

			return FileVisitResult.CONTINUE;
		}

		/**
		 * Whether the JsonNode is a final Connector. It means that this JsonNode
		 * defines the displayName section. Checks whether the JsonNode is a final
		 * Connector. It means that this JsonNode defines the displayName section.
		 *
		 * @param connector JsonNode that contains connector's data.
		 * @return {@code true} if the {@link JsonNode} is a final connector, otherwise
		 *         {@code false}.
		 */
		private boolean isConnector(final JsonNode connector) {
			final JsonNode connectorNode = connector.get("connector");
			if (connectorNode != null && !connectorNode.isNull()) {
				final JsonNode displayName = connectorNode.get("displayName");
				return displayName != null && !displayName.isNull();
			}

			return false;
		}

		/**
		 * Checks whether the connector is a YAML file or not.
		 *
		 * @param name Given fileName.
		 * @return {@code true} if the file has a YAML extension, otherwise
		 *         {@code false}.
		 */
		private boolean isYamlFile(final String name) {
			return name.toLowerCase().endsWith(".yaml");
		}
	}

	/**
	 * Parses connectors from all YAML files in the given directory.
	 *
	 * @param yamlParentDirectory The directory containing connectors YAML files.
	 * @return a RawConnectorStore object.
	 * @throws IOException If the file does not exist.
	 */
	public Map<String, RawConnector> parseConnectorsFromAllYamlFiles(Path yamlParentDirectory) throws IOException {
		final long startTime = System.currentTimeMillis();
		final ConnectorFileVisitor fileVisitor = new ConnectorFileVisitor();
		Files.walkFileTree(yamlParentDirectory, fileVisitor);
		log.info("Connectors parsing duration: {} seconds", (System.currentTimeMillis() - startTime) / 1000);
		return fileVisitor.getConnectorsMap();
	}
}
