package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.mockito.InOrder;

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

		final ExtensionManager extensionManager = ExtensionManager.builder()
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

	@Test
	void testCloseRunsShutdownHooksBeforeClosingLoaders() throws Exception {
		final IProtocolExtension protocolExtension = mock(IProtocolExtension.class);
		final AutoCloseable classLoader = mock(AutoCloseable.class);

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtension))
			.withClassLoaders(List.of(classLoader))
			.build();

		extensionManager.close();

		// The extension's own resources (e.g. isolated JDBC driver loaders) must be released
		// before its class loader disappears.
		final InOrder order = inOrder(protocolExtension, classLoader);
		order.verify(protocolExtension).onShutdown();
		order.verify(classLoader).close();
	}

	@Test
	void testCloseSurvivesLinkageErrorFromShutdownHook() throws Exception {
		// A hook lazily touching an absent class throws NoClassDefFoundError (a LinkageError):
		// the remaining hooks and every class loader must still be closed.
		final IProtocolExtension failing = mock(IProtocolExtension.class);
		final IProtocolExtension healthy = mock(IProtocolExtension.class);
		final AutoCloseable classLoader = mock(AutoCloseable.class);
		doThrow(new NoClassDefFoundError("com/acme/Gone")).when(failing).onShutdown();

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(healthy, failing))
			.withClassLoaders(List.of(classLoader))
			.build();

		extensionManager.close();

		verify(healthy).onShutdown();
		verify(classLoader).close();
	}

	@Test
	void testCloseRunsShutdownHooksInReverseDiscoveryOrder() {
		// Providers are discovered dependency-first ([dependency, dependent]); shutdown must run in
		// reverse so a dependent can still use its dependency's state while cleaning up.
		final IProtocolExtension dependency = mock(IProtocolExtension.class);
		final IProtocolExtension dependent = mock(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(dependency, dependent))
			.build();

		extensionManager.close();

		final InOrder order = inOrder(dependency, dependent);
		order.verify(dependent).onShutdown();
		order.verify(dependency).onShutdown();
	}
}
