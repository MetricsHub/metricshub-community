package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;

class ExtensionManagerTest {

	private static final String CONNECTOR_ID_1 = "connector_1";
	private static final String CONNECTOR_ID_2 = "connector_2";

	@Test
	void test() {
		final IConnectorStoreProviderExtension connectorStoreProviderExt1 = new IConnectorStoreProviderExtension() {
			private RawConnectorStore rawConnectorStore;

			@Override
			public void load() {
				final RawConnector rawConnector = new RawConnector();

				final Map<String, RawConnector> store = Map.of(CONNECTOR_ID_1, rawConnector);

				rawConnectorStore = new RawConnectorStore();
				rawConnectorStore.setStore(store);
			}

			@Override
			public RawConnectorStore getRawConnectorStore() {
				return rawConnectorStore;
			}
		};
		final IConnectorStoreProviderExtension connectorStoreProviderExt2 = new IConnectorStoreProviderExtension() {
			private RawConnectorStore rawConnectorStore;

			@Override
			public void load() {
				final RawConnector rawConnector = new RawConnector();

				final Map<String, RawConnector> store = Map.of(CONNECTOR_ID_2, rawConnector);

				rawConnectorStore = new RawConnectorStore();
				rawConnectorStore.setStore(store);
			}

			@Override
			public RawConnectorStore getRawConnectorStore() {
				return rawConnectorStore;
			}
		};

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withConnectorStoreProviderExtensions(List.of(connectorStoreProviderExt1, connectorStoreProviderExt2))
			.build();
		final RawConnectorStore rawConnectorStore = extensionManager.aggregateExtensionRawConnectorStores();
		final Map<String, RawConnector> rawStore = new HashMap<>(
			Map.of(CONNECTOR_ID_1, new RawConnector(), CONNECTOR_ID_2, new RawConnector())
		);

		final RawConnectorStore rawConnectorStoreExpected = new RawConnectorStore();
		rawConnectorStoreExpected.setStore(rawStore);
		assertInstanceOf(TreeMap.class, rawConnectorStore.getStore());
		assertEquals(rawConnectorStoreExpected.getStore(), rawConnectorStore.getStore());
	}
}
