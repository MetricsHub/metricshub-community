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

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;

/**
 * Manages the storage and retrieval of {@link Connector} instances.
 * The instances are stored in a map where the key is the connector file name, and the value is the corresponding {@link Connector} object.
 */
@Slf4j
@NoArgsConstructor
@Data
public class ConnectorStore implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<String, Connector> store = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	@Getter
	private transient Path connectorDirectory;

	private RawConnectorStore rawConnectorStore;

	private List<String> connectorsWithVariables = new ArrayList<>();

	/**
	 * Constructs a {@link ConnectorStore} using the specified connector directory.
	 * This constructor is used for unit tests only. The new approach to create a connector
	 * Store is to create a {@link RawConnectorStore}, then a {@link ConnectorStoreComposer},
	 * then generate
	 *
	 * @param connectorDirectory The path to the directory containing connector files.
	 */
	public ConnectorStore(Path connectorDirectory) {
		this.connectorDirectory = connectorDirectory;
		final RawConnectorStore rawConnectorStore = new RawConnectorStore(connectorDirectory);
		this.rawConnectorStore = rawConnectorStore;
		final Map<String, Connector> store = ConnectorStoreComposer
			.builder()
			.withRawConnectorStore(rawConnectorStore)
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.build()
			.generateStaticConnectorStore()
			.getStore();

		if (store != null) {
			addMany(store);
		}
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
	 * Add a connector with variables
	 *
	 * @param connectors A list of connectors with variables IDs to be added.
	 */
	public void addConnectorsWithVariables(List<String> connectors) {
		connectorsWithVariables.addAll(connectors);
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
		newConnectorStore.setRawConnectorStore(rawConnectorStore);
		newConnectorStore.setConnectorsWithVariables(connectorsWithVariables);
		return newConnectorStore;
	}
}
