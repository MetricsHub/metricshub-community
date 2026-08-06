package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.io.File;
import java.io.IOException;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the loading of extensions from a specified directory and produces an {@link ExtensionManager}.
 * This class is responsible for finding, loading, and initializing extensions that extend the functionality
 * of MetricsHub. The extensions are expected to be jar files located in the specified extensions directory.
 *
 * <p>Each extension jar is loaded in its own {@link ExtensionClassLoader} (see {@link ExtensionRuntime}),
 * so that one extension's {@code META-INF/services} registrations cannot change JVM-global service
 * resolution for another. This class is a thin façade preserving the historical
 * {@code new ExtensionLoader(dir).load()} entry point.
 */
@Data
@RequiredArgsConstructor
@Slf4j
public class ExtensionLoader {

	@NonNull
	private File extensionsDirectory;

	private ExtensionManager extensionManager;

	/**
	 * Loads extensions from the {@code extensionsDirectory} and returns an {@link ExtensionManager} that wraps
	 * all the loaded extensions. Each extension is loaded in its own class loader.
	 *
	 * @return An {@link ExtensionManager} containing all loaded extensions.
	 * @throws IOException If an I/O error occurs reading from the directory or a JAR file.
	 */
	public ExtensionManager load() throws IOException {
		if (extensionManager != null) {
			return extensionManager;
		}

		extensionManager = ExtensionRuntime.load(
			extensionsDirectory,
			ExtensionLoader.class.getClassLoader()
		).getExtensionManager();

		return extensionManager;
	}
}
