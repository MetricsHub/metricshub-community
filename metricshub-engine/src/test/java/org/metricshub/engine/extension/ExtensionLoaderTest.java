package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader.Provider;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

class ExtensionLoaderTest {

	@Test
	void testConvertProviderStreamToList() throws IOException {
		// Initialize the extension loader using a non existent file because this test doesn't rely on the extension directory
		final ExtensionLoader extensionLoader = new ExtensionLoader(new File("fake" + UUID.randomUUID().toString()));

		// Create the expected extension implementation
		final IStrategyProviderExtension expected = new TestStrategyProvider();

		// Call the converter method which transforms the extension provider stream to a list of extensions
		final List<IStrategyProviderExtension> strategyProviderExtensions = extensionLoader.convertProviderStreamToList(
			Stream.of(
				new Provider<IStrategyProviderExtension>() {
					@Override
					public Class<? extends IStrategyProviderExtension> type() {
						return TestStrategyProvider.class;
					}

					@Override
					public IStrategyProviderExtension get() {
						return expected;
					}
				}
			)
		);

		// Check the expected results
		assertEquals(1, strategyProviderExtensions.size());
		assertEquals(expected, strategyProviderExtensions.get(0));
	}

	@Test
	void testFilterDefaultProtocolExtensions() {
		final ExtensionLoader extensionLoader = new ExtensionLoader(new File("fake" + UUID.randomUUID().toString()));
		final TestProtocolExtension httpExtension = new TestProtocolExtension("http");
		final TestProtocolExtension emulationExtension = new TestProtocolExtension("emulation");

		assertIterableEquals(
			List.of(httpExtension),
			extensionLoader.filterDefaultProtocolExtensions(List.of(httpExtension, emulationExtension))
		);
	}

	private static final class TestProtocolExtension implements IProtocolExtension {

		private final String identifier;

		private TestProtocolExtension(final String identifier) {
			this.identifier = identifier;
		}

		@Override
		public boolean isValidConfiguration(final org.metricshub.engine.configuration.IConfiguration configuration) {
			return false;
		}

		@Override
		public java.util.Set<
			Class<? extends org.metricshub.engine.connector.model.monitor.task.source.Source>
		> getSupportedSources() {
			return java.util.Set.of();
		}

		@Override
		public java.util.Map<
			Class<? extends org.metricshub.engine.configuration.IConfiguration>,
			java.util.Set<Class<? extends org.metricshub.engine.connector.model.monitor.task.source.Source>>
		> getConfigurationToSourceMapping() {
			return java.util.Map.of();
		}

		@Override
		public java.util.Set<
			Class<? extends org.metricshub.engine.connector.model.identity.criterion.Criterion>
		> getSupportedCriteria() {
			return java.util.Set.of();
		}

		@Override
		public java.util.Optional<Boolean> checkProtocol(final TelemetryManager telemetryManager) {
			return java.util.Optional.empty();
		}

		@Override
		public org.metricshub.engine.strategy.source.SourceTable processSource(
			final org.metricshub.engine.connector.model.monitor.task.source.Source source,
			final String connectorId,
			final TelemetryManager telemetryManager
		) {
			return org.metricshub.engine.strategy.source.SourceTable.empty();
		}

		@Override
		public org.metricshub.engine.strategy.detection.CriterionTestResult processCriterion(
			final org.metricshub.engine.connector.model.identity.criterion.Criterion criterion,
			final String connectorId,
			final TelemetryManager telemetryManager,
			final boolean logMode
		) {
			return org.metricshub.engine.strategy.detection.CriterionTestResult.empty();
		}

		@Override
		public boolean isSupportedConfigurationType(final String configurationType) {
			return identifier.equalsIgnoreCase(configurationType);
		}

		@Override
		public org.metricshub.engine.configuration.IConfiguration buildConfiguration(
			final String configurationType,
			final com.fasterxml.jackson.databind.JsonNode jsonNode,
			final java.util.function.UnaryOperator<char[]> decrypt
		) {
			return null;
		}

		@Override
		public String getIdentifier() {
			return identifier;
		}

		@Override
		public String executeQuery(
			final org.metricshub.engine.configuration.IConfiguration configuration,
			final com.fasterxml.jackson.databind.JsonNode queryNode
		) {
			return null;
		}
	}

	class TestStrategyProvider implements IStrategyProviderExtension {

		@Override
		public List<IStrategy> generate(TelemetryManager telemetryManager, Long strategyTime) {
			return List.of(
				new IStrategy() {
					@Override
					public void run() {}

					@Override
					public long getStrategyTimeout() {
						return 0;
					}

					@Override
					public Long getStrategyTime() {
						return System.currentTimeMillis();
					}
				}
			);
		}
	}
}
