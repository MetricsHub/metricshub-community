package org.metricshub.engine.connector.parser;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.IntermediateConnector;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.identity.ConnectorDefaultVariable;
import org.metricshub.engine.connector.update.ConnectorUpdateChain;

/**
 * Responsible for building and enhancing a {@link ConnectorStore} by processing raw connectors
 * and resolving variable substitutions where applicable.
 * <p>
 * This class is central to the connector transformation pipeline. It first produces a static
 * {@code ConnectorStore} containing all fully defined connectors that do not rely on variable substitution.
 * Then, it processes the remaining connectors containing unresolved variables either in their JSON definitions
 * or embedded files using configuration from {@link AdditionalConnector} instances to produce customized
 * connector variations.
 * </p>
 * <p>
 * It is used internally during connector initialization and extension resolution phases of the MetricsHub agent.
 * </p>
 */
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder(setterPrefix = "with")
public class ConnectorStoreComposer {

	/**
	 * Regex pattern to match connector variable of the form ${var::...}.
	 */
	private static final Pattern CONNECTOR_VARIABLE_PATTERN = Pattern.compile("\\$\\{var\\:\\:([^}]+)}");

	/**
	 * Error message used when connector deserialization fails.
	 */
	private static final String DESERIALIZATION_ERROR_MESSAGE = "Error while deserializing connector {}.";

	/**
	 * Log message template for printing exceptions.
	 */
	private static final String EXCEPTION_MESSAGE = "Exception: {}.";

	/**
	 * Deserializer used to parse JsonNode connectors into structured connector objects.
	 */
	private ConnectorDeserializer deserializer;

	/**
	 * Store containing raw connectors and their metadata.
	 */
	private RawConnectorStore rawConnectorStore;

	/**
	 * Chain of update actions to apply on connector nodes.
	 */
	private ConnectorUpdateChain updateChain;

	/**
	 * Map of user-defined additional connectors keyed by connector ID.
	 */
	@Builder.Default
	private Map<String, AdditionalConnector> additionalConnectors = new HashMap<>();

	/**
	 * Object containing the resulting connector store and the list of forced connectors.
	 */
	@Builder.Default
	private AdditionalConnectorsParsingResult connectorsParsingResult = new AdditionalConnectorsParsingResult();

	/**
	 * Generates a static {@link ConnectorStore} by processing all raw connectors.
	 * <p>
	 * Each raw connector from the {@link RawConnectorStore} is deserialized into a {@link JsonNode},
	 * processed, and added to the resulting {@code ConnectorStore}. The original {@code RawConnectorStore}
	 * is also associated with the generated store.
	 * </p>
	 * <p>
	 * Connectors that contain variables either in their body or in one of their embedded files
	 * are excluded from this processing step, as additional connector configuration is not yet available.
	 * These connectors are instead tracked and listed in the {@code connectorsWithVariables} collection of the resulting store.
	 * </p>
	 *
	 * @return a fully constructed static {@link ConnectorStore}, excluding connectors with variables
	 */
	public ConnectorStore generateStaticConnectorStore() {
		final ConnectorStore connectorStore = new ConnectorStore();

		final List<String> connectorsWithVariables = new ArrayList<>();

		if (rawConnectorStore != null) {
			// For each Raw Connector, search for variables in the JsonNode or the Embedded Files.
			rawConnectorStore
				.getStore()
				.forEach((connectorId, rawConnector) -> {
					final IntermediateConnector intermediateConnector = new IntermediateConnector(connectorId, rawConnector);

					// If the connector contains variables, do not parse it in this phase.
					// This is due to the absence of additional connectors configuration.
					// If processed, this may cause parsing errors.
					if (intermediateConnector.hasVariables()) {
						connectorsWithVariables.add(connectorId);
					} else {
						try {
							// Add all the resulting connectors in the connector store
							connectorStore.addOne(connectorId, parseConnector(intermediateConnector));
						} catch (IOException e) {
							log.error(DESERIALIZATION_ERROR_MESSAGE, connectorId);
							log.debug(EXCEPTION_MESSAGE, e);
						}
					}
				});

			connectorStore.setRawConnectorStore(rawConnectorStore);
			connectorStore.addConnectorsWithVariables(connectorsWithVariables);
		}

		return connectorStore;
	}

	/**
	 * Resolves all variables found in connectors and their embedded files within a {@link ConnectorStore}.
	 * <p>
	 * This method processes each connector that contains variables, as previously identified
	 * during the static connector store generation phase. It retrieves the raw connectors, applies all
	 * corresponding variable configurations including user-defined values and generates fully-resolved
	 * connector instances.
	 * </p>
	 * <p>
	 * Connectors generated from this process are collected and returned via an {@link AdditionalConnectorsParsingResult},
	 * which includes all dynamically created connectors and records of associated resource connectors.
	 * </p>
	 *
	 * @param connectorStore The input {@link ConnectorStore} containing raw connectors, some of which may have unresolved variables.
	 * @return An {@link AdditionalConnectorsParsingResult} containing all resolved connectors and their associated metadata.
	 */
	public AdditionalConnectorsParsingResult resolveConnectorStoreVariables(final ConnectorStore connectorStore) {
		// From the Set of connectors with variables that have been found in generateConnectorStore(),
		// collect all the Raw Connectors with variables.
		// In this map, either connectors or their embedded files contain variables that need to be replaced.
		final Map<String, RawConnector> connectorsWithVariables = rawConnectorStore
			.getStore()
			.entrySet()
			.stream()
			.filter(entry -> connectorStore.getConnectorsWithVariables().contains(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		// For each Raw Connector, process variables
		connectorsWithVariables.forEach((String connectorId, RawConnector rawConnector) ->
			// Put all the resulting connectors inside the output object.
			connectorsParsingResult
				.getCustomConnectorsMap()
				.putAll((processConnector(new IntermediateConnector(connectorId, rawConnector))))
		);

		return connectorsParsingResult;
	}

	/**
	 * Processes a single connector by resolving its variables and converting it into one or more concrete connectors.
	 *
	 * @param intermediateConnector The connector to process.
	 * @return A map of resolved connector IDs to their corresponding {@link Connector} instances.
	 */
	private Map<String, Connector> processConnector(final IntermediateConnector intermediateConnector) {
		// Store all the connectors that emerged from the current one in a map.
		final List<IntermediateConnector> processedConnectors = processVariables(intermediateConnector);

		final Map<String, Connector> connectors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		processedConnectors.forEach((IntermediateConnector processedIntermediateConnector) -> {
			final String connectorId = processedIntermediateConnector.getConnectorId();
			try {
				connectors.put(connectorId, parseConnector(processedIntermediateConnector));
			} catch (IOException e) {
				log.error(DESERIALIZATION_ERROR_MESSAGE, connectorId);
				log.debug(EXCEPTION_MESSAGE + e);
			}
		});
		return connectors;
	}

	/**
	 * Resolves all variable configurations for a connector and returns a list of fully processed {@link IntermediateConnector} instances.
	 * <p>
	 * It always generates a default connector using its default variables and original ID.
	 * If an additional connector is configured with the same ID, it will override the default during integration.
	 *</p>
	 *
	 * @param intermediateConnector The base connector containing variables to resolve.
	 * @return A list of {@link IntermediateConnector} instances, including the default and all configured variants.
	 */
	private List<IntermediateConnector> processVariables(final IntermediateConnector intermediateConnector) {
		// Retrieve the connectorId from the intermediate connector
		final String connectorId = intermediateConnector.getConnectorId();

		// Retrieve the connector JsonNode from the intermediate connector
		final JsonNode connectorNode = intermediateConnector.getConnectorNode();

		// List containing resulting JsonNodes
		final List<IntermediateConnector> processedConnectors = new ArrayList<>();

		// Filter additional connectors configs to keep only those for the current connector
		final Map<String, AdditionalConnector> connectorConfigurations = filterAdditionalConnectors(connectorId);

		// Construct a variables map from the default connector variables.
		final Map<String, String> defaultVariables = new HashMap<>(getDefaultConnectorVariables(connectorNode));

		// For each connector with variables, perform a replacement with the default variables
		// Thus, in case of forcing connector using [+connectorId] without configuring it with
		// AdditionalConnectors, it will work!
		// If a connector with the same name is configured, it will override this one.
		final IntermediateConnector connectorWithDefaultVariables = intermediateConnector.getDeepCopy(connectorId);
		replaceVariables(connectorWithDefaultVariables, defaultVariables);
		processedConnectors.add(connectorWithDefaultVariables);

		// Apply user-defined configurations if they exist
		if (!connectorConfigurations.isEmpty()) {
			// For each configuration, we create a new custom connector and a new variables
			// map to be used in the connector update.
			for (final Entry<String, AdditionalConnector> connectorConfigurationEntry : connectorConfigurations.entrySet()) {
				// Retrieve the additional connector id.
				final String additionalConnectorId = connectorConfigurationEntry.getKey();

				// Make a deep copy of the intermediate connector for each configuration with the user specified connector id.
				final IntermediateConnector newIntermediateConnector = intermediateConnector.getDeepCopy(additionalConnectorId);

				// Retrieve the additional connector configuration
				final AdditionalConnector additionalConnectorValue = connectorConfigurationEntry.getValue();

				// Retrieve and use default connector variables on this connector for this
				// configuration.
				final Map<String, String> connectorVariables = new HashMap<>(defaultVariables);

				// Override the default connector variables by the connector variables that the
				// user configured.
				final Map<String, String> configuredVariables = additionalConnectorValue.getVariables();
				if (configuredVariables != null) {
					connectorVariables.putAll(configuredVariables);
				}

				// Replace the connector and embedded files variables by the obtained variables values.
				replaceVariables(newIntermediateConnector, connectorVariables);

				// Add the new intermediate connector to the processed connectors set.
				processedConnectors.add(newIntermediateConnector);

				// Add the connector to the host connectors set.
				connectorsParsingResult
					.getResourceConnectors()
					.add(additionalConnectorValue.isForce() ? "+" + additionalConnectorId : additionalConnectorId);
			}
		}
		return processedConnectors;
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
			.forEachRemaining((Entry<String, JsonNode> entry) -> {
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
	 * Replaces all variable placeholders in an embedded file's content using the provided variable map.
	 *
	 * @param embeddedFileContent The String content of the embedded file.
	 * @param variables A map of variable names to their values.
	 * @return The content with all variables replaced.
	 */
	private String replaceEmbeddedFileVariables(final String embeddedFileContent, final Map<String, String> variables) {
		final Matcher matcher = CONNECTOR_VARIABLE_PATTERN.matcher(embeddedFileContent);
		final StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			// Extract key between "${var::" and "}"
			String key = matcher.group(1);
			String replacement = variables.getOrDefault(key, matcher.group());
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Performs variable substitution in both the connector's node and its embedded files.
	 *
	 * @param intermediateConnector The connector to update.
	 * @param connectorVariables A map of variable names and their resolved values.
	 */
	private void replaceVariables(
		final IntermediateConnector intermediateConnector,
		final Map<String, String> connectorVariables
	) {
		// Create a new map that will contain the new embedded files with replaced variables
		final Map<Integer, EmbeddedFile> processedEmbeddedFiles = new HashMap<>();

		// Iterate on each embedded file of the connector to replace variables
		intermediateConnector
			.getEmbeddedFiles()
			.forEach((Integer embeddedFileId, EmbeddedFile embeddedFile) -> {
				final String newEmbeddedFileContent = replaceEmbeddedFileVariables(
					embeddedFile.getContentAsString(),
					connectorVariables
				);

				// Create a copy of the embedded file
				final EmbeddedFile newEmbeddedFile = embeddedFile.copy();

				// As the content is replaced, set the new content.
				newEmbeddedFile.setContent(newEmbeddedFileContent.getBytes());

				// put the new embedded file in the final
				processedEmbeddedFiles.put(embeddedFileId, newEmbeddedFile);
			});

		// Replace the intermediate connector embedded files by the processed ones.
		intermediateConnector.setEmbeddedFiles(processedEmbeddedFiles);

		ConnectorVariableProcessor processor = ConnectorVariableProcessor
			.builder()
			.connectorVariables(connectorVariables)
			.next(null)
			.build();
		try {
			intermediateConnector.setConnectorNode(processor.processNode(intermediateConnector.getConnectorNode()));
		} catch (IOException e) {
			log.error(
				"Error while parsing raw connector {} with connector variables.",
				intermediateConnector.getConnectorId()
			);
			log.debug(EXCEPTION_MESSAGE, e);
		}
	}

	/**
	 * Filters the additional connectors configuration to retrieve only those using the specified connector.
	 *
	 * @param connectorId connector Id to use for filtering additional connectors.
	 * @return A map of additional connectors configured to use the specified connector.
	 */
	private Map<String, AdditionalConnector> filterAdditionalConnectors(final String connectorId) {
		if (additionalConnectors == null) {
			return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		}

		// Filtering all the configurations that are not using this connector.
		return additionalConnectors
			.entrySet()
			.stream()
			.filter(entry -> connectorId.equalsIgnoreCase(entry.getValue().getUses()))
			.collect(
				Collectors.toMap(
					Map.Entry::getKey,
					Map.Entry::getValue,
					(e1, e2) -> e1,
					() -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
				)
			);
	}

	/**
	 * Converts an {@link IntermediateConnector} into a fully formed {@link Connector}, applying embedded files and updates.
	 *
	 * @param intermediateConnector The connector to parse.
	 * @return A fully populated {@link Connector} instance.
	 * @throws IOException If an error occurs during deserialization.
	 */
	public Connector parseConnector(final IntermediateConnector intermediateConnector) throws IOException {
		// adding post-deserialization support to the deserializer {@link ObjectMapper}
		PostDeserializeHelper.addPostDeserializeSupport(deserializer.getMapper());

		// POST-Processing
		final Connector connector = deserializer.deserialize(intermediateConnector.getConnectorNode());

		// Retrieve the embeddedFiles from the intermediateConnector Object.
		final Map<Integer, EmbeddedFile> embeddedFiles = intermediateConnector.getEmbeddedFiles();

		// Assigns the map of embedded files to the connector
		if (embeddedFiles != null) {
			connector.setEmbeddedFiles(embeddedFiles);
		}

		// Run the update chain
		if (updateChain != null) {
			updateChain.update(connector);
		}

		// Update the compiled filename
		connector.getConnectorIdentity().setCompiledFilename(intermediateConnector.getConnectorId());

		return connector;
	}
}
