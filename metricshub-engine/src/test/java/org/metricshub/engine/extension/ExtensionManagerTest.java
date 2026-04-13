package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

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

	@Test
	void testActivateAndKeepOnlyProtocolExtension() {
		final IProtocolExtension httpExtension = new TestProtocolExtension("http");
		final IProtocolExtension emulationExtension = new TestProtocolExtension("emulation");

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(httpExtension))
			.withAvailableProtocolExtensions(List.of(httpExtension, emulationExtension))
			.build();

		extensionManager.activateProtocolExtension("emulation");
		assertIterableEquals(List.of(httpExtension, emulationExtension), extensionManager.getProtocolExtensions());

		extensionManager.keepOnlyProtocolExtension("emulation");
		assertIterableEquals(List.of(emulationExtension), extensionManager.getProtocolExtensions());
	}

	private static final class TestProtocolExtension implements IProtocolExtension {

		private final String identifier;

		private TestProtocolExtension(final String identifier) {
			this.identifier = identifier;
		}

		@Override
		public boolean isValidConfiguration(final IConfiguration configuration) {
			return false;
		}

		@Override
		public Set<Class<? extends Source>> getSupportedSources() {
			return Set.of();
		}

		@Override
		public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
			return Map.of();
		}

		@Override
		public Set<Class<? extends Criterion>> getSupportedCriteria() {
			return Set.of();
		}

		@Override
		public Optional<Boolean> checkProtocol(final TelemetryManager telemetryManager) {
			return Optional.empty();
		}

		@Override
		public SourceTable processSource(
			final Source source,
			final String connectorId,
			final TelemetryManager telemetryManager
		) {
			return SourceTable.empty();
		}

		@Override
		public CriterionTestResult processCriterion(
			final Criterion criterion,
			final String connectorId,
			final TelemetryManager telemetryManager,
			final boolean logMode
		) {
			return CriterionTestResult.empty();
		}

		@Override
		public boolean isSupportedConfigurationType(final String configurationType) {
			return identifier.equalsIgnoreCase(configurationType);
		}

		@Override
		public IConfiguration buildConfiguration(
			final String configurationType,
			final com.fasterxml.jackson.databind.JsonNode jsonNode,
			final UnaryOperator<char[]> decrypt
		) throws InvalidConfigurationException {
			return null;
		}

		@Override
		public String getIdentifier() {
			return identifier;
		}

		@Override
		public String executeQuery(
			final IConfiguration configuration,
			final com.fasterxml.jackson.databind.JsonNode queryNode
		) throws Exception {
			return null;
		}
	}
}
