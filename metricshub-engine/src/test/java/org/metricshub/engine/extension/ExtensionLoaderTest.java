package org.metricshub.engine.extension;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtensionLoaderTest {

	@Test
	void testLoadFromMissingDirectoryReturnsEmptyManager() throws IOException {
		// A non-existent directory yields an empty (but non-null) manager rather than failing.
		final ExtensionLoader extensionLoader = new ExtensionLoader(new File("fake" + UUID.randomUUID()));

		final ExtensionManager extensionManager = extensionLoader.load();

		assertNotNull(extensionManager, "The extension manager must never be null");
		assertTrue(extensionManager.getProtocolExtensions().isEmpty(), "No protocol extension should be loaded");
		assertTrue(
			extensionManager.getConfigurationProviderExtensions().isEmpty(),
			"No configuration provider extension should be loaded"
		);
	}

	@Test
	void testLoadIsMemoized() throws IOException {
		final ExtensionLoader extensionLoader = new ExtensionLoader(new File("fake" + UUID.randomUUID()));

		final ExtensionManager first = extensionLoader.load();
		final ExtensionManager second = extensionLoader.load();

		assertTrue(first == second, "load() must return the same memoized instance");
	}
}
