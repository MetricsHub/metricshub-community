package org.metricshub.engine.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
