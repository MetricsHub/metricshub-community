package org.metricshub.engine.it.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.extension.ExtensionLoader;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IStrategyProviderExtension;
import org.metricshub.engine.strategy.IStrategy;

class ExtensionLoaderIT {

	@Test
	void testLoad() throws IOException {
		// This test should be executed within the maven life-cycle to get target/it/it-extension/target prepared

		final ExtensionManager extensionManager = new ExtensionLoader(new File("target/it/it-extension/target")).load();
		assertEquals(0, extensionManager.getProtocolExtensions().size());
		assertEquals(0, extensionManager.getConnectorStoreProviderExtensions().size());
		final List<IStrategyProviderExtension> strategyProviderExtensions =
			extensionManager.getStrategyProviderExtensions();
		assertEquals(1, strategyProviderExtensions.size());
		// The extension instance is wrapped in a TCCL-managing proxy, so assert its behavior rather
		// than its concrete class name (which is now a dynamic proxy).
		final List<IStrategy> strategies = strategyProviderExtensions.get(0).generate(null, 0L);
		assertEquals(1, strategies.size());
	}
}
