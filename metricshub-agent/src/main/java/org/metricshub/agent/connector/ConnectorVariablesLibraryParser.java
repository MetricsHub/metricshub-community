package org.metricshub.agent.connector;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AdditionalConnector;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.ConnectorDefaultVariable;
import org.metricshub.engine.connector.parser.ConnectorParser;

/**
 * Utility class for parsing connectors with variables and creating a map of custom connectors.
 * This class provides functionality to visit YAML files in a specified directory, read connector data,
 * and create Connector objects based on the parsed data. The resulting connectors are stored in a map
 * with the connector ID as the key and the corresponding Connector object as the value.
 * <p>
 * The parsing process involves checking for YAML files, validating whether the YAML structure defines a
 * final Connector (with a displayName section), and using ConnectorParser to parse the YAML file and create
 * Connector objects.
 * </p>
 */
@Slf4j
public class ConnectorVariablesLibraryParser {

	/**
	 * This inner class allows to visit the files contained within the Yaml directory
	 */
	private static class ConnectorFileVisitor extends SimpleFileVisitor<Path> {

		private final Map<String, AdditionalConnector> additionalConnectorConfig;
		private final AdditionalConnectorsParsingResult connectorsParsingResult = new AdditionalConnectorsParsingResult();

		public AdditionalConnectorsParsingResult getConnectorsParsingResult() {
			return connectorsParsingResult;
		}

		ConnectorFileVisitor(final Map<String, AdditionalConnector> additionalConnectorConfig) {
			this.additionalConnectorConfig = additionalConnectorConfig;
		}

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
			// Skip this path if it is a directory or not a YAML file
			if (Files.isDirectory(path) || !isYamlFile(path.toFile().getName())) {
				return FileVisitResult.CONTINUE;
			}
			final ObjectMapper yamlMapper = new YAMLMapper();
			final JsonNode connectorNode = yamlMapper.readTree(path.toFile());
			if (!isConnector(connectorNode)) {
				return FileVisitResult.CONTINUE;
			}

			// Get the connector's file name
			final String filename = path.getFileName().toString();
			final String connectorId = filename.substring(0, filename.lastIndexOf('.'));

			if (!connectorNode.toString().contains("${var::")) {
				return FileVisitResult.CONTINUE;
			}

			// Normalize additionalConnectors
			normalizeAdditionalConnectors();

			// Filtering all the configurations that are not using this connector.
			final Map<String, AdditionalConnector> filteredConnectors = additionalConnectorConfig
				.entrySet()
				.stream()
				.filter(entry -> connectorId.equalsIgnoreCase(entry.getValue().getUses()))
				.collect(
					Collectors.toMap(
						Map.Entry::getKey, // Keep the original key
						Map.Entry::getValue // Keep the original AdditionalConnectorConfig as the value
					)
				);

			// Construct a variables map from the default connector variables.
			final Map<String, String> defaultVariables = new HashMap<>(getDefaultConnectorVariables(connectorNode));

			if (filteredConnectors.isEmpty()) {
				// Parse the connector even if it is not configured as an additional connector.
				// This ensures that the connector will function with the default variables if the user forces it.
				parseConnectorWithModifier(path, defaultVariables, connectorId, filename, connector -> {});
			} else {
				// Parse and generate connectors that have been added and configured.
				generateNewConnectors(path, filename, filteredConnectors, defaultVariables);
			}

			return FileVisitResult.CONTINUE;
		}

		/**
		 * Whether the JsonNode is a final Connector. It means that this JsonNode defines the displayName section.
		 *
		 * @param connector JsonNode that contains connector's data
		 * @return <code>true</code> if the {@link JsonNode} is a final connector, otherwise false.
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
		 * Whether the connector is a YAML file or not
		 *
		 * @param name given fileName
		 * @return boolean value
		 */
		private boolean isYamlFile(final String name) {
			return name.toLowerCase().endsWith(".yaml");
		}

		/**
		 * Converts the "variables" section of a {@link JsonNode} into a {@link Map} where each entry consists of
		 * a variable name as the key and a {@link ConnectorDefaultVariable} as the value.
		 * Each {@link ConnectorDefaultVariable} contains a description and a default value extracted from the JSON node.
		 *
		 * @param connectorNode the {@link JsonNode} representing the connector, which includes a "variables" section.
		 * @return a map where the key is the variable name, and the value is a {@link ConnectorDefaultVariable} object
		 *         containing the description and defaultValue for that variable. If the "variables" section is not present,
		 *         an empty map is returned.
		 */
		private static Map<String, String> getDefaultConnectorVariables(final JsonNode connectorNode) {
			final JsonNode variablesNode = connectorNode.get("connector").get("variables");
			if (variablesNode == null) {
				return new HashMap<>();
			}

			final Map<String, String> connectorVariablesMap = new HashMap<>();

			// Iterate over the variables and extract description and defaultValue
			variablesNode
				.fields()
				.forEachRemaining(entry -> {
					final String variableName = entry.getKey();
					final JsonNode variableValue = entry.getValue();

					final JsonNode defaultValue = variableValue.get("defaultValue");
					if (defaultValue != null && !defaultValue.isNull()) {
						connectorVariablesMap.put(variableName, variableValue.get("defaultValue").asText());
					}
				});

			return connectorVariablesMap;
		}

		/**
		 * Generates custom connectors for each additional connector configuration.
		 *
		 * <p>This method iterates over the provided map of filtered connectors, generating a new {@link Connector}
		 * object for each. It handles the default and user-configured variables, ensuring that any forced connectors
		 * are correctly identified. The method can modify the compiled filename of connectors when needed.</p>
		 *
		 * @param path The path to the directory containing the connector files.
		 * @param filename The filename of the connector file.
		 * @param filteredConnectors A map of filtered additional connectors, with their configurations.
		 * @param defaultVariables A map of default variables to be used during connector generation.
		 */
		private void generateNewConnectors(
			final Path path,
			final String filename,
			final Map<String, AdditionalConnector> filteredConnectors,
			final Map<String, String> defaultVariables
		) {
			// For each configuration, we create a new custom connector and a new variables map to be used in the connector update.
			for (final Entry<String, AdditionalConnector> connectorConfigurationEntry : filteredConnectors.entrySet()) {
				final String additionalConnectorId = connectorConfigurationEntry.getKey();
				final AdditionalConnector additionalConnectorValue = connectorConfigurationEntry.getValue();

				// Add the connector to the host connectors set.
				connectorsParsingResult
					.getHostConnectors()
					.add(additionalConnectorValue.isForce() ? "+" + additionalConnectorId : additionalConnectorId);

				// Retrieve and use default connector variables on this connector for this configuration.
				final Map<String, String> connectorVariables = new HashMap<>(defaultVariables);

				// Override the default connector variables by the connector variables that the user configured.
				final Map<String, String> configuredVariables = additionalConnectorValue.getVariables();
				if (configuredVariables != null) {
					connectorVariables.putAll(configuredVariables);
				}
				// There are at least two additional connectors that use the current connector.
				// This means that the compiled filename of these connectors needs to be modified.
				parseConnectorWithModifier(
					path,
					connectorVariables,
					additionalConnectorId,
					filename,
					connector -> connector.getConnectorIdentity().setCompiledFilename(additionalConnectorId)
				);
			}
		}

		/**
		 * Parses a connector file located at the specified path, using the provided variables and connector Id.
		 * The parsed connector is then added to the custom connectors map, with the option to perform additional
		 * operations on the connector before adding it to the map.
		 *
		 * @param path      The path to the connector file that needs to be parsed.
		 * @param variables A map of variables to be used for processing the connector, where the keys are variable names and the values are the corresponding values.
		 * @param connectorId The unique identifier for the connector being parsed, used as the key in the custom connectors map.
		 * @param filename  The name of the connector file, used for logging in case of an error.
		 * @param connectorModifier A function to apply additional changes to the parsed connector before adding it to the map.
		 */
		private void parseConnectorWithModifier(
			final Path path,
			final Map<String, String> variables,
			final String connectorId,
			final String filename,
			final Consumer<Connector> connectorModifier
		) {
			final ConnectorParser connectorParser = ConnectorParser.withNodeProcessorAndUpdateChain(
				path.getParent(),
				variables
			);
			try {
				final Connector connector = connectorParser.parse(path.toFile());
				connectorModifier.accept(connector);
				connectorsParsingResult.getCustomConnectorsMap().put(connectorId, connector);
			} catch (Exception e) {
				log.error("Error while parsing connector with variables {}: {}", filename, e.getMessage());
				log.debug("Exception: ", e);
			}
		}

		/**
		 * Updates the additional connectors configuration by ensuring that each entry has a valid
		 * {@code AdditionalConnector} object. If the connector or its {@code uses} field is null, it is set
		 * to the connector Id.
		 */
		private void normalizeAdditionalConnectors() {
			additionalConnectorConfig
				.entrySet()
				.forEach(entry -> {
					final String connectorId = entry.getKey();
					final AdditionalConnector additionalConnector = entry.getValue();

					// If additionalConnector is null, create a new object and update the entry
					if (additionalConnector == null) {
						entry.setValue(AdditionalConnector.builder().force(true).uses(connectorId).variables(null).build());
						return;
					}

					// If uses() is null, set it to the connectorId
					if (additionalConnector.getUses() == null) {
						additionalConnector.setUses(connectorId);
					}
				});
		}
	}

	/**
	 * Parses connectors with variables YAML files in the specified directory and creates a map of custom connectors.
	 *
	 * @param yamlParentDirectory   The directory containing connector YAML files
	 * @param additionalConnectorConfig A map of additional connector configurations
	 * @return {@link AdditionalConnectorsParsingResult} containing parsed connectors and any forced connectors
	 * @throws IOException if the file does not exist or an I/O error occurs during processing
	 */
	public AdditionalConnectorsParsingResult parse(
		@NonNull final Path yamlParentDirectory,
		@NonNull final Map<String, AdditionalConnector> additionalConnectorConfig
	) throws IOException {
		final long startTime = System.currentTimeMillis();
		final ConnectorFileVisitor connectorFileVisitor = new ConnectorFileVisitor(additionalConnectorConfig);
		Files.walkFileTree(yamlParentDirectory, connectorFileVisitor);
		log.info("Connectors with variables parsing duration: {} seconds", (System.currentTimeMillis() - startTime) / 1000);
		return connectorFileVisitor.getConnectorsParsingResult();
	}
}
