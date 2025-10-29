package org.metricshub.engine.it.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.extension.ExtensionLoader;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IStrategyProviderExtension;

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
		assertEquals("StrategyProvider", strategyProviderExtensions.get(0).getClass().getSimpleName());
	}
}
