package org.metricshub.engine.connector.model;

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
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Represents an intermediate stage in the transformation of a {@link RawConnector}
 * into a fully modeled {@link Connector}.
 * <p>
 * This class holds the deserialized {@link JsonNode} representation of the connector,
 * as well as the map of associated {@link EmbeddedFile}s. These fields may be modified
 * during the processing steps (e.g., variable replacement, updates chain)
 * before final conversion into a {@code Connector}.
 * </p>
 *
 */
@Data
@Slf4j
@AllArgsConstructor
public class IntermediateConnector {

	/**
	 * The pattern of Connector variables
	 */
	private static final String CONNECTOR_VARIABLE_PATTERN = "${var::";

	/**
	 * The Id of the Intermediate Connector
	 */
	private String connectorId;

	/**
	 * The parsed and modifiable JSON structure of the connector.
	 */
	private JsonNode connectorNode;

	/**
	 * The map of embedded files (e.g., scripts, templates) associated with the
	 * connector.
	 */
	private Map<Integer, EmbeddedFile> embeddedFiles = new HashMap<>();

	/**
	 * Constructs an {@link IntermediateConnector} from the given connector ID and raw connector.
	 * Initializes the connector's JSON node and embedded files using a YAML mapper.
	 *
	 * @param connectorId The unique identifier for the connector.
	 * @param rawConnector   The raw connector containing the serialized data and embedded files.
	 */
	public IntermediateConnector(final String connectorId, final RawConnector rawConnector) {
		this.connectorId = connectorId;
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = rawConnector.getEmbeddedFiles();
		if (connectorEmbeddedFiles != null) {
			this.embeddedFiles.putAll(connectorEmbeddedFiles);
		}

		try {
			this.connectorNode = mapper.readTree(rawConnector.getByteConnector());
		} catch (IOException e) {
			log.error("Error while reading Raw Connector {} from Raw Connector Store.", connectorId);
			log.debug("Exception: ", e);
		}
	}

	/**
	 * Creates a deep copy of this {@link IntermediateConnector} with a new connector ID.
	 *
	 * @param newConnectorId The identifier to assign to the copied connector.
	 * @return A new {@link IntermediateConnector} instance with copied content and the specified ID.
	 */
	public IntermediateConnector getDeepCopy(final String newConnectorId) {
		final Map<Integer, EmbeddedFile> embeddedFilesCopy = new HashMap<>();
		embeddedFiles.forEach((key, embeddedFile) -> embeddedFilesCopy.put(key, embeddedFile.copy()));
		return new IntermediateConnector(newConnectorId, connectorNode.deepCopy(), embeddedFilesCopy);
	}

	/**
	 * Checks if the connector JsonNode or one of its embedded files contain variables.
	 * @return True if it contains variables, False otherwise.
	 */
	public boolean hasVariables() {
		// Detect if the connector node contains variable patterns
		if (connectorNode != null && connectorNode.toString().contains(CONNECTOR_VARIABLE_PATTERN)) {
			// Register this connector as one that contains variables for late replacement
			return true;
		}

		// Detect if any of the connector's embedded files contain variable patterns
		return embeddedFiles
			.values()
			.stream()
			.anyMatch(embeddedFile -> (embeddedFile.getContentAsString().contains(CONNECTOR_VARIABLE_PATTERN)));
	}
}
