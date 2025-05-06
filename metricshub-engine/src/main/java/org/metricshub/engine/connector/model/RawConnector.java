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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

/**
 * Represents the raw form of a connector definition, including its serialized YAML content
 * and any associated embedded files.
 * <p>
 * This class is used during the early stages of connector deserialization and variable resolution
 * in the MetricsHub engine. It encapsulates the byte representation of the connector’s JSON/YAML definition
 * as well as a set of embedded files that may require additional parsing or transformation.
 * </p>
 *
 */
@Data
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class RawConnector implements Serializable {

	private static final long serialVersionUID = 2810L;

	private static final ObjectMapper MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * Byte array representing the raw connector Json Node.
	 */
	private byte[] byteConnector;

	/**
	 * Map representing all the related embedded files of the connector.
	 */
	private Map<Integer, EmbeddedFile> embeddedFiles = new HashMap<>();

	/**
	 * Constructs a {@code RawConnector} from a {@link JsonNode} representation and a map of embedded files.
	 *
	 * @param rawConnectorNode The connector content as a {@link JsonNode}.
	 * @param embeddedFiles A map of embedded files associated with the connector.
	 * @throws JsonProcessingException If the {@link JsonNode} cannot be converted to a byte array.
	 */
	public RawConnector(final JsonNode rawConnectorNode, final Map<Integer, EmbeddedFile> embeddedFiles)
		throws JsonProcessingException {
		byteConnector = MAPPER.writeValueAsBytes(rawConnectorNode);
		this.embeddedFiles.putAll(embeddedFiles);
	}

	/**
	 * Deserializes the raw byte representation of the connector into a {@link JsonNode}.
	 *
	 * @param connectorId The connector identifier (used for logging context).
	 * @return The deserialized {@link JsonNode}, or {@code null} if deserialization fails.
	 */
	public JsonNode getConnectorNode(final String connectorId) {
		try {
			return MAPPER.readTree(byteConnector);
		} catch (IOException e) {
			log.error("Error while deserializing byte array as JsonNode in connector {}.", connectorId);
			log.debug("Exception: ", e);
		}
		return null;
	}
}
