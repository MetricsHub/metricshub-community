package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;

class ExtensionManagerTest {

	private static final String CONNECTOR_ID_1 = "connector_1";
	private static final String CONNECTOR_ID_2 = "connector_2";

	@Test
	void test() {
		final IConnectorStoreProviderExtension connectorStoreProviderExt1 = new IConnectorStoreProviderExtension() {
			private ConnectorStore connectorStore;

			@Override
			public void load() {
				final Connector connector = Connector.builder().build();

				final Map<String, Connector> store = Map.of(CONNECTOR_ID_1, connector);

				connectorStore = new ConnectorStore();
				connectorStore.setStore(store);
			}

			@Override
			public ConnectorStore getConnectorStore() {
				return connectorStore;
			}
		};
		final IConnectorStoreProviderExtension connectorStoreProviderExt2 = new IConnectorStoreProviderExtension() {
			private ConnectorStore connectorStore;

			@Override
			public void load() {
				final Connector connector = Connector.builder().build();

				final Map<String, Connector> store = Map.of(CONNECTOR_ID_2, connector);

				connectorStore = new ConnectorStore();
				connectorStore.setStore(store);
			}

			@Override
			public ConnectorStore getConnectorStore() {
				return connectorStore;
			}
		};

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConnectorStoreProviderExtensions(List.of(connectorStoreProviderExt1, connectorStoreProviderExt2))
			.build();
		final ConnectorStore connectorStore = extensionManager.aggregateExtensionConnectorStores();
		final Map<String, Connector> store = new HashMap<>(
			Map.of(CONNECTOR_ID_1, Connector.builder().build(), CONNECTOR_ID_2, Connector.builder().build())
		);

		final ConnectorStore connectorStoreExpected = new ConnectorStore();
		connectorStoreExpected.setStore(store);
		assertTrue(connectorStore.getStore() instanceof TreeMap);
		assertEquals(connectorStoreExpected, connectorStore);
	}
}
