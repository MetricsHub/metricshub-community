package org.metricshub.engine.connector.model;

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

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.parser.ConnectorLibraryParser;

/**
 * Manages the storage and retrieval of {@link Connector} instances.
 * The instances are stored in a map where the key is the connector file name, and the value is the corresponding {@link Connector} object.
 */
@Slf4j
@NoArgsConstructor
@Data
public class ConnectorStore implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<String, Connector> store;

	@Getter
	private transient Path connectorDirectory;

	/**
	 * Constructs a {@link ConnectorStore} using the specified connector directory.
	 *
	 * @param connectorDirectory The path to the directory containing connector files.
	 */
	public ConnectorStore(Path connectorDirectory) {
		try {
			this.connectorDirectory = connectorDirectory;
			store = deserializeConnectors();
		} catch (Exception e) {
			log.error("Error while deserializing connectors. The ConnectorStore is empty!");
			log.debug("Error while deserializing connectors. The ConnectorStore is empty!", e);
			store = new HashMap<>();
		}
	}

	/**
	 * This method retrieves Connectors data in a Map from a given connector directory
	 * The key of the Map will be the connector file name and Value will be the Connector Object
	 * @return Map<String, Connector>
	 * @throws IOException
	 */
	private Map<String, Connector> deserializeConnectors() throws IOException {
		final ConnectorLibraryParser connectorLibraryParser = new ConnectorLibraryParser();
		return connectorLibraryParser.parseConnectorsFromAllYamlFiles(connectorDirectory);
	}

	/**
	 * Add a new {@link Connector} instance
	 *
	 * @param id        the id of the connector
	 * @param connector the {@link Connector} instance to add
	 */
	public void addOne(@NonNull final String id, @NonNull final Connector connector) {
		store.put(id, connector);
	}

	/**
	 * Adds multiple instances of {@link Connector} to the connector store.
	 * The connectors are provided as a {@link Map} where each entry represents a connector with its unique identifier.
	 *
	 * @param connectors A {@link Map} containing connectors to be added, keyed by their unique identifiers.
	 */
	public void addMany(@NonNull final Map<String, Connector> connectors) {
		store.putAll(connectors);
	}

	/**
	 * Creates and returns a new instance of ConnectorStore, initialized with a copy of the current connectors.
	 *
	 * This method is useful for creating a snapshot of the current state of the ConnectorStore used for each resource.
	 * Changes made to the new ConnectorStore will not affect the original one.
	 *
	 * @return A new ConnectorStore instance with the same connectors as the current store.
	 */
	public ConnectorStore newConnectorStore() {
		final ConnectorStore newConnectorStore = new ConnectorStore();
		final Map<String, Connector> originalConnectors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		originalConnectors.putAll(store);
		newConnectorStore.setStore(originalConnectors);
		return newConnectorStore;
	}
}
