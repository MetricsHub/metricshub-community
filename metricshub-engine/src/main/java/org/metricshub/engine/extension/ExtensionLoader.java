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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.classloader.agent.ClassLoaderAgent;

/**
 * Manages the loading of extensions from a specified directory and produces an {@link ExtensionManager}.
 * This class is responsible for finding, loading, and initializing extensions that extend the functionality
 * of MetricsHub. The extensions are expected to be jar files located in the specified extensions directory.
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
	 * all the loaded extensions. Extensions are loaded as JAR files and are expected to implement certain
	 * extension interfaces to be recognized and used by the system.
	 *
	 * @return An {@link ExtensionManager} containing all loaded extensions.
	 * @throws IOException If an I/O error occurs reading from the directory or a JAR file.
	 */
	public ExtensionManager load() throws IOException {
		if (extensionManager != null) {
			return extensionManager;
		}

		// Get the jar files located under the extension directory
		final File[] extensionJars = extensionsDirectory.listFiles((unused, fileName) -> fileName.endsWith(".jar"));

		// If there is no extension then create an empty extension manger and skip the current loading
		if (extensionJars == null || extensionJars.length == 0) {
			log.debug("No extension to load from {}. Stop extension loading.", extensionsDirectory);
			return ExtensionManager.empty();
		}

		// Create JAR URLs
		final URL[] urls = new URL[extensionJars.length];
		for (int i = 0; i < extensionJars.length; i++) {
			final File jarFile = extensionJars[i];

			// Make sure to add the jar to the system class loader search
			ClassLoaderAgent.addToClassPath(new JarFile(jarFile));
			urls[i] = jarFile.toURI().toURL();
		}

		// Create the class loader
		final URLClassLoader classLoader = new URLClassLoader(urls);

		// Load protocol extensions
		final ServiceLoader<IProtocolExtension> protocolExtensions = ServiceLoader.load(
			IProtocolExtension.class,
			classLoader
		);
		protocolExtensions.forEach(extension ->
			log.info("Loaded protocol extension {}.", extension.getClass().getSimpleName())
		);

		// Load strategy provider extensions
		final ServiceLoader<IStrategyProviderExtension> strategyProviderExtensions = ServiceLoader.load(
			IStrategyProviderExtension.class,
			classLoader
		);
		strategyProviderExtensions.forEach(extension ->
			log.info("Loaded strategy provider extension {}.", extension.getClass().getSimpleName())
		);

		// Load connector store provider extensions
		final ServiceLoader<IConnectorStoreProviderExtension> connectorStoreProviderExtensions = ServiceLoader.load(
			IConnectorStoreProviderExtension.class,
			classLoader
		);
		connectorStoreProviderExtensions.forEach(extension ->
			log.info("Loaded connector store provider extension {}.", extension.getClass().getSimpleName())
		);

		// Load source computation extensions
		final ServiceLoader<ISourceComputationExtension> sourceComputationExtensions = ServiceLoader.load(
			ISourceComputationExtension.class,
			classLoader
		);
		sourceComputationExtensions.forEach(extension ->
			log.info("Loaded source computation extension {}.", extension.getClass().getSimpleName())
		);

		// Load Jawk extensions
		final ServiceLoader<ICompositeSourceScriptExtension> compositeSourceScriptExtensions = ServiceLoader.load(
			ICompositeSourceScriptExtension.class,
			classLoader
		);
		compositeSourceScriptExtensions.forEach(extension ->
			log.info("Loaded composite source script extension {}.", extension.getClass().getSimpleName())
		);

		// Load Configuration provider extensions
		final ServiceLoader<IConfigurationProvider> configurationProviderExtensions = ServiceLoader.load(
			IConfigurationProvider.class,
			classLoader
		);
		configurationProviderExtensions.forEach(extension ->
			log.info("Loaded configuration provider extension {}.", extension.getClass().getSimpleName())
		);

		// Build the extension manager
		extensionManager =
			ExtensionManager
				.builder()
				.withProtocolExtensions(convertProviderStreamToList(protocolExtensions.stream()))
				.withStrategyProviderExtensions(convertProviderStreamToList(strategyProviderExtensions.stream()))
				.withConnectorStoreProviderExtensions(convertProviderStreamToList(connectorStoreProviderExtensions.stream()))
				.withSourceComputationExtensions(convertProviderStreamToList(sourceComputationExtensions.stream()))
				.withCompositeSourceScriptExtensions(convertProviderStreamToList(compositeSourceScriptExtensions.stream()))
				.withConfigurationProviderExtensions(convertProviderStreamToList(configurationProviderExtensions.stream()))
				.build();

		return extensionManager;
	}

	/**
	 * Converts a stream of {@link ServiceLoader.Provider} objects into a list of their extension instances.
	 *
	 * @param <T> The type of the extension interface.
	 * @param providerStream The stream of {@link ServiceLoader.Provider} objects to convert.
	 * @return A list of extension instances provided by the input stream.
	 */
	<T> List<T> convertProviderStreamToList(final Stream<Provider<T>> providerStream) {
		return providerStream.map(Provider::get).collect(Collectors.toCollection(ArrayList::new));
	}
}
