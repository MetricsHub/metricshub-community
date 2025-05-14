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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.parser.RawConnectorLibraryParser;

/**
 * Manages the storage and retrieval of {@link RawConnector} instances.
 * The instances are stored in a map where the key is the connector file name, and the value is the corresponding {@link RawConnector} JsonNode object.
 */
@Slf4j
@NoArgsConstructor
@Data
public class RawConnectorStore implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<String, RawConnector> store = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	@Getter
	public transient Path connectorDirectory;

	/**
	 * Used to store all subtypes that will be used to deserialize RawConnectors
	 */
	private Set<NamedType> subtypes = new HashSet<>();

	/**
	 * Constructs a {@link RawConnectorStore} by loading and deserializing all connector
	 * found in the given directory path.
	 *
	 * @param connectorDirectory The path to the directory containing raw connector definitions and embedded files.
	 */
	public RawConnectorStore(Path connectorDirectory) {
		try {
			this.connectorDirectory = connectorDirectory;
			store = createRawStore();
		} catch (Exception e) {
			log.error("Error while deserializing connectors. The RawConnectorStore is empty!");
			log.debug("Error while deserializing connectors. The RawConnectorStore is empty!", e);
			store = new HashMap<>();
		}
	}

	/**
	 * This method retrieves RawConnectors data in a Map from a given connector directory
	 * The key of the Map will be the connector file name and Value will be the RawConnector JsonNode Object
	 * @return a Map of all RawConnectors where the key is the connector identifier (compiled file name)
	 * @throws IOException if an error occurs while reading the connector files
	 */
	private Map<String, RawConnector> createRawStore() throws IOException {
		final RawConnectorLibraryParser rawConnectorLibraryParser = new RawConnectorLibraryParser();
		return rawConnectorLibraryParser.parseConnectorsFromAllYamlFiles(connectorDirectory);
	}

	/**
	 * Adds multiple instances of {@link RawConnector} to the connector store.
	 * The connectors are provided as a {@link Map} where each entry represents a connector with its unique identifier.
	 *
	 * @param rawConnectors A {@link Map} containing connectors to be added, keyed by their unique identifiers.
	 */
	public void addMany(@NonNull final Map<String, RawConnector> rawConnectors) {
		store.putAll(rawConnectors);
	}

	/**
	 * Creates and returns an ObjectMapper enriched with all subtypes.
	 *
	 * @return a Yaml object mapper with all subtypes.
	 */
	public ObjectMapper getMapperFromSubtypes() {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		subtypes.forEach(mapper::registerSubtypes);
		return mapper;
	}
}
