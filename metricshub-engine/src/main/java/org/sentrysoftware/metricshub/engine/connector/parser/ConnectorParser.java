package org.sentrysoftware.metricshub.engine.connector.parser;

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
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.sentrysoftware.metricshub.engine.common.helpers.FileHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.JsonHelper;
import org.sentrysoftware.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.sentrysoftware.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.update.AvailableSourceUpdate;
import org.sentrysoftware.metricshub.engine.connector.update.CompiledFilenameUpdate;
import org.sentrysoftware.metricshub.engine.connector.update.ConnectorUpdateChain;
import org.sentrysoftware.metricshub.engine.connector.update.MonitorTaskSourceDepUpdate;
import org.sentrysoftware.metricshub.engine.connector.update.PreSourceDepUpdate;

/**
 * Utility class for parsing connector files and creating Connector objects.
 */
@AllArgsConstructor
@Builder
@Data
public class ConnectorParser {

	private ConnectorDeserializer deserializer;

	@Getter
	private AbstractNodeProcessor processor;

	private ConnectorUpdateChain connectorUpdateChain;

	/**
	 * Parses the given connector file.
	 *
	 * @param file Connector file to parse.
	 * @return New {@link Connector} object.
	 * @throws IOException If an I/O error occurs during parsing.
	 */
	public Connector parse(final File file) throws IOException {
		JsonNode node = deserializer.getMapper().readTree(file);

		// PRE-Processing
		if (processor != null) {
			final Map<URI, JsonNode> parents = new HashMap<>();
			final Path connectorDirectory = file.toPath().getParent();
			resolveParents(node, connectorDirectory.toUri(), parents);

			node = processor.process(node);

			new EmbeddedFilesResolver(node, connectorDirectory, parents.keySet()).internalize();
		}

		// POST-Processing
		final Connector connector = deserializer.deserialize(node);

		// Run the update chain
		if (connectorUpdateChain != null) {
			connectorUpdateChain.update(connector);
		}

		// Update the compiled filename
		new CompiledFilenameUpdate(file.getName()).update(connector);

		return connector;
	}

	/**
	 * Parse the given connector file
	 *
	 * @param inputStream The {@link InputStream} of the connector we want to parse
	 * @param connectorFolderUri The URI of the folder containing the connector
	 * @param fileName The connector file name
	 * @return new {@link Connector} object
	 * @throws IOException
	 */
	public Connector parse(final InputStream inputStream, final URI connectorFolderUri, final String fileName)
		throws IOException {
		JsonNode node = deserializer.getMapper().readTree(inputStream);
		// PRE-Processing
		if (processor != null) {
			final Map<URI, JsonNode> parents = new HashMap<>();
			resolveParents(node, connectorFolderUri, parents);

			node = processor.process(node);
			new EmbeddedFilesResolver(node, Paths.get(connectorFolderUri), parents.keySet()).internalize();
		}

		// POST-Processing
		final Connector connector = deserializer.deserialize(node);

		// Run the update chain
		if (connectorUpdateChain != null) {
			connectorUpdateChain.update(connector);
		}

		// Update the compiled filename
		new CompiledFilenameUpdate(fileName).update(connector);

		return connector;
	}

	/**
	 * Creates a new {@link ConnectorParser} with extends and constants
	 * {@link AbstractNodeProcessor}
	 *
	 * @param connectorDirectory The connector files directory.
	 * @return New instance of {@link ConnectorParser}.
	 */
	public static ConnectorParser withNodeProcessor(final Path connectorDirectory) {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();

		PostDeserializeHelper.addPostDeserializeSupport(mapper);

		return ConnectorParser
			.builder()
			.deserializer(new ConnectorDeserializer(mapper))
			.processor(NodeProcessorHelper.withExtendsAndConstantsProcessor(connectorDirectory, mapper))
			.build();
	}

	/**
	 * Creates a new {@link ConnectorParser} with extends and constants
	 * {@link AbstractNodeProcessor}
	 *
	 * @param connectorDirectory    The connector files directory.
	 * @param connectorVariables    Map of connector variables.
	 * @return New instance of {@link ConnectorParser}.
	 */
	public static ConnectorParser withNodeProcessor(
		final Path connectorDirectory,
		final Map<String, String> connectorVariables
	) {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();

		PostDeserializeHelper.addPostDeserializeSupport(mapper);

		return ConnectorParser
			.builder()
			.deserializer(new ConnectorDeserializer(mapper))
			.processor(
				NodeProcessorHelper.withExtendsAndTemplateVariableProcessor(connectorDirectory, mapper, connectorVariables)
			)
			.build();
	}

	/**
	 * Creates a new {@link ConnectorParser} with extends and constants
	 * {@link AbstractNodeProcessor} and with a {@link ConnectorUpdateChain}
	 *
	 * @param connectorDirectory The connector files directory.
	 * @return New instance of {@link ConnectorParser}.
	 */
	public static ConnectorParser withNodeProcessorAndUpdateChain(final Path connectorDirectory) {
		final ConnectorParser connectorParser = withNodeProcessor(connectorDirectory);

		// Create the update objects
		final ConnectorUpdateChain updateChain = createUpdateChain();

		// Set the first update chain
		connectorParser.setConnectorUpdateChain(updateChain);

		return connectorParser;
	}

	/**
	 * Creates a new {@link ConnectorParser} with extends and constants
	 * {@link AbstractNodeProcessor} and with a {@link ConnectorUpdateChain}
	 *
	 * @param connectorDirectory The connector files directory.
	 * @param connectorVariables Map of connector variables.
	 * @return New instance of {@link ConnectorParser}.
	 */
	public static ConnectorParser withNodeProcessorAndUpdateChain(
		final Path connectorDirectory,
		final Map<String, String> connectorVariables
	) {
		final ConnectorParser connectorParser = withNodeProcessor(connectorDirectory, connectorVariables);

		// Create the update objects
		final ConnectorUpdateChain updateChain = createUpdateChain();

		// Set the first update chain
		connectorParser.setConnectorUpdateChain(updateChain);

		return connectorParser;
	}

	/**
	 * Create the update chain for this connector
	 *
	 * @return {@link ConnectorUpdateChain} instance
	 */
	public static ConnectorUpdateChain createUpdateChain() {
		final ConnectorUpdateChain availableSource = new AvailableSourceUpdate();
		final ConnectorUpdateChain preSourceDepUpdate = new PreSourceDepUpdate();
		final ConnectorUpdateChain monitorTaskSourceDepUpdate = new MonitorTaskSourceDepUpdate();

		// Create the chain
		availableSource.setNextUpdateChain(preSourceDepUpdate);
		preSourceDepUpdate.setNextUpdateChain(monitorTaskSourceDepUpdate);
		return availableSource;
	}

	/**
	 * Resolve connector parent paths
	 *
	 * @param connector    The connector object as {@link JsonNode}
	 * @param connectorUri The connector directory
	 * @param parents      The parents map to resolve
	 * @throws IOException
	 */
	private void resolveParents(final JsonNode connector, final URI connectorUri, final Map<URI, JsonNode> parents)
		throws IOException {
		final ArrayNode extended = (ArrayNode) connector.get("extends");
		if (extended == null || extended.isNull() || extended.isEmpty()) {
			return;
		}

		final List<Entry<URI, JsonNode>> nextEntries = new ArrayList<>();
		Entry<URI, JsonNode> parentEntry;
		for (final JsonNode extendedNode : extended) {
			parentEntry = getConnectorParentEntry(connectorUri, extendedNode.asText());
			nextEntries.add(parentEntry);
			parents.put(parentEntry.getKey(), parentEntry.getValue());
		}

		for (Entry<URI, JsonNode> entry : nextEntries) {
			resolveParents(entry.getValue(), entry.getKey(), parents);
		}
	}

	/**
	 * Get a connector entry where the entry key is the connector path and the value
	 * is the connector as {@link JsonNode}
	 *
	 * @param connectorCurrentDir   The current directory of the connector which extends the parent
	 * @param connectorRelativePath The relative path of the connector parent
	 * @return a Map entry defining the path as key and the {@link JsonNode} parent connector as value
	 * @throws IOException
	 */
	private Entry<URI, JsonNode> getConnectorParentEntry(
		final URI connectorCurrentDir,
		final String connectorRelativePath
	) throws IOException {
		// Resolve the Path of the parent yaml file and normalize it
		final Path connectorCurrentDirPath = Paths.get(connectorCurrentDir);
		Path connectorPath = connectorCurrentDirPath.resolve(connectorRelativePath + ".yaml").normalize();

		if (Files.exists(connectorPath)) {
			return new AbstractMap.SimpleEntry<>(
				connectorPath.getParent().toUri(),
				deserializer.getMapper().readTree(Files.newInputStream(connectorPath))
			);
		}

		// If the path is absolute, it should refer to a path within the "connectors" directory
		if (!connectorRelativePath.startsWith(".")) {
			final Path connectorsDirectoryPath = FileHelper.findConnectorsDirectory(connectorCurrentDir);
			if (connectorsDirectoryPath != null) {
				connectorPath = connectorsDirectoryPath.resolve(connectorRelativePath + ".yaml").normalize();
				if (Files.exists(connectorPath)) {
					return new AbstractMap.SimpleEntry<>(
						connectorPath.getParent().toUri(),
						deserializer.getMapper().readTree(Files.newInputStream(connectorPath))
					);
				}
			}
		}

		throw new IllegalStateException("Cannot get parent entry for connector path: " + connectorRelativePath);
	}
}
