package org.metricshub.engine.connector.parser;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.Connector;

/**
 * Parses connectors from YAML files in a given directory.
 */
@Slf4j
public class ConnectorLibraryParser {

	public static final String CONNECTOR_PARSING_ERROR = "Error while parsing connector {}: {}";

	/**
	 * The ObjectMapper instance for handling YAML files.
	 */
	public static final ObjectMapper OBJECT_MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * This inner class allows to visit the files contained within the Yaml directory
	 */
	private class ConnectorFileVisitor extends SimpleFileVisitor<Path> {

		private final Map<String, Connector> connectorsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		public Map<String, Connector> getConnectorsMap() {
			return connectorsMap;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			final String filename = file.getFileName().toString();

			if (isZipFile(filename)) {
				readZipFile(file);
				return FileVisitResult.CONTINUE;
			}

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
				final Connector connector = connectorParser.parse(file.toFile());
				connectorsMap.put(filename.substring(0, filename.lastIndexOf('.')), connector);
			} catch (Exception e) {
				log.error(CONNECTOR_PARSING_ERROR, filename, e.getMessage());
				log.debug("Exception: ", e);
			}

			return FileVisitResult.CONTINUE;
		}

		/**
		 * Read a Zip file and try to parse its files as connectors.
		 * @param zipPath The zip file path
		 * @throws IOException
		 */
		private void readZipFile(final Path zipPath) throws IOException {
			try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipPath)) {
				final Path root = zipFileSystem.getPath("/");

				Files.walkFileTree(
					root,
					new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
							final String strPath = path.toString();

							if (!isYamlFile(strPath)) {
								return FileVisitResult.CONTINUE;
							}
							final JsonNode connectorNode = OBJECT_MAPPER.readTree(Files.newInputStream(path));

							if (!isConnector(connectorNode)) {
								return FileVisitResult.CONTINUE;
							}

							final Path connectorFolder = path.getParent();
							final URI connectorFolderUri = connectorFolder.toUri();

							final ConnectorParser connectorParser = ConnectorParser.withNodeProcessorAndUpdateChain(connectorFolder);

							final String fileName = strPath.substring(strPath.lastIndexOf('/') + 1);

							FileHelper.fileSystemTask(
								connectorFolderUri,
								Collections.emptyMap(),
								() -> {
									final Connector connector;
									try {
										connector = connectorParser.parse(Files.newInputStream(path), connectorFolderUri, fileName);
										connectorsMap.put(fileName.substring(0, fileName.lastIndexOf('.')), connector);
									} catch (Exception e) {
										log.error(CONNECTOR_PARSING_ERROR, fileName, e.getMessage());
										log.debug("Exception: ", e);
									}
								}
							);

							return FileVisitResult.CONTINUE;
						}
					}
				);
			} catch (IOException exception) {
				// In case of an IOException, we log it and throw it back
				log.error("Error while reading zip file {}: {}", zipPath.getFileName().toString(), exception.getMessage());
				throw exception;
			}
		}

		/**
		 * Whether the JsonNode is a final Connector. It means that this JsonNode defines the displayName section.
		 * Checks whether the JsonNode is a final Connector.
		 * It means that this JsonNode defines the displayName section.
		 *
		 * @param connector JsonNode that contains connector's data.
		 * @return {@code true} if the {@link JsonNode} is a final connector, otherwise {@code false}.
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
		 * @return {@code true} if the file has a YAML extension, otherwise {@code false}.
		 */
		private boolean isYamlFile(final String name) {
			return name.toLowerCase().endsWith(".yaml");
		}

		/**
		 * Whether the connector is a ZIP file or not
		 *
		 * @param name given file name
		 * @return boolean value
		 */
		private boolean isZipFile(final String name) {
			return name.toLowerCase().endsWith(".zip");
		}
	}

	/**
	 * Parses connectors from all YAML files in the given directory.
	 *
	 * @param yamlParentDirectory The directory containing connectors YAML files.
	 * @return Map&lt;String, Connector&gt; (connectors map: key=YAMLFileName, value=Connector).
	 * @throws IOException If the file does not exist.
	 */
	public Map<String, Connector> parseConnectorsFromAllYamlFiles(Path yamlParentDirectory) throws IOException {
		final long startTime = System.currentTimeMillis();
		final ConnectorFileVisitor fileVisitor = new ConnectorFileVisitor();
		Files.walkFileTree(yamlParentDirectory, fileVisitor);
		log.info("Connectors parsing duration: {} seconds", (System.currentTimeMillis() - startTime) / 1000);
		return fileVisitor.getConnectorsMap();
	}
}
