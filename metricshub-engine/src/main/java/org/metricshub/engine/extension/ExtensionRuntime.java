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
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the extensions found in a directory, each in its own {@link ExtensionClassLoader}, and
 * builds the {@link ExtensionManager} that exposes them to the engine.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Scan the extensions directory for {@code *.jar} files and read each one's
 *       {@link ExtensionDescriptor} from its manifest.</li>
 *   <li>Order the extensions so that declared dependencies are built before their dependents
 *       (topological sort); disable — with an error, never a crash — any extension whose declared
 *       dependency is missing or forms a cycle.</li>
 *   <li>Create one {@link ExtensionClassLoader} per extension, wiring dependency loaders as
 *       delegates.</li>
 *   <li>Run the seven MetricsHub SPI {@link ServiceLoader}s against each loader, keeping only the
 *       providers that loader actually owns (so a provider visible through the parent or a delegate
 *       is not registered twice), and wrap each provider so it runs under its extension's TCCL.</li>
 * </ol>
 */
@Slf4j
public final class ExtensionRuntime implements AutoCloseable {

	/**
	 * Package prefixes that must always resolve from the parent (engine/agent) class loader to keep
	 * a single {@link Class} identity across all extensions. A child-first declaration overlapping
	 * one of these is ignored.
	 */
	private static final List<String> FORCED_PARENT_PREFIXES = List.of(
		"org.metricshub.engine.",
		"com.fasterxml.jackson.",
		"org.slf4j.",
		"org.apache.logging.log4j.",
		"io.opentelemetry."
	);

	@Getter
	private final ExtensionManager extensionManager;

	private ExtensionRuntime(final ExtensionManager extensionManager) {
		this.extensionManager = extensionManager;
	}

	/**
	 * Loads the extensions located in {@code extensionsDirectory}.
	 *
	 * @param extensionsDirectory the directory containing extension jars.
	 * @param parent              the shared engine/agent class loader used as the parent of every
	 *                            extension loader.
	 * @return a runtime exposing the built {@link ExtensionManager}. When the directory is empty or
	 *         missing, the runtime wraps an {@link ExtensionManager#empty()}.
	 * @throws IOException if a jar cannot be read.
	 */
	public static ExtensionRuntime load(final File extensionsDirectory, final ClassLoader parent) throws IOException {
		final File[] jars = extensionsDirectory.listFiles((_, name) -> name.endsWith(".jar"));

		if (jars == null || jars.length == 0) {
			log.debug("No extension to load from {}. Stop extension loading.", extensionsDirectory);
			return new ExtensionRuntime(ExtensionManager.empty());
		}

		// Read manifests.
		final Map<String, ExtensionDescriptor> byId = new LinkedHashMap<>();
		for (final File jar : jars) {
			final ExtensionDescriptor descriptor = ExtensionDescriptor.from(jar);
			if (byId.putIfAbsent(descriptor.id(), descriptor) != null) {
				log.error("Duplicate extension id '{}' (jar '{}'). Ignoring the duplicate.", descriptor.id(), jar.getName());
			}
		}

		// Order dependencies before dependents; drop the ones we cannot satisfy.
		final List<ExtensionDescriptor> ordered = orderByDependencies(byId);

		// Build one loader per extension, wiring dependency loaders as delegates.
		final Map<String, ExtensionClassLoader> loadersById = new LinkedHashMap<>();
		final List<ExtensionClassLoader> classLoaders = new ArrayList<>();
		for (final ExtensionDescriptor descriptor : ordered) {
			final List<ExtensionClassLoader> delegates = new ArrayList<>();
			for (final String requiredId : descriptor.requires()) {
				final ExtensionClassLoader delegate = loadersById.get(requiredId);
				if (delegate != null) {
					delegates.add(delegate);
				}
			}

			final URL url = toUrl(descriptor.jarFile());
			if (url == null) {
				continue;
			}

			final ExtensionClassLoader loader = new ExtensionClassLoader(
				descriptor.id(),
				new URL[] { url },
				parent,
				delegates,
				sanitizeChildFirst(descriptor)
			);
			loadersById.put(descriptor.id(), loader);
			classLoaders.add(loader);
		}

		// Discover and wrap the SPI providers of every loader.
		final ExtensionManager.ExtensionManagerBuilder builder = ExtensionManager.builder();
		final List<IProtocolExtension> protocolExtensions = new ArrayList<>();
		final List<IStrategyProviderExtension> strategyProviderExtensions = new ArrayList<>();
		final List<IConnectorStoreProviderExtension> connectorStoreProviderExtensions = new ArrayList<>();
		final List<ISourceComputationExtension> sourceComputationExtensions = new ArrayList<>();
		final List<ICompositeSourceScriptExtension> compositeSourceScriptExtensions = new ArrayList<>();
		final List<IConfigurationProvider> configurationProviderExtensions = new ArrayList<>();
		final List<IMetricEnrichmentExtension> metricEnrichmentExtensions = new ArrayList<>();

		for (final ExtensionClassLoader loader : classLoaders) {
			loadSpi(IProtocolExtension.class, loader, protocolExtensions);
			loadSpi(IStrategyProviderExtension.class, loader, strategyProviderExtensions);
			loadSpi(IConnectorStoreProviderExtension.class, loader, connectorStoreProviderExtensions);
			loadSpi(ISourceComputationExtension.class, loader, sourceComputationExtensions);
			loadSpi(ICompositeSourceScriptExtension.class, loader, compositeSourceScriptExtensions);
			loadSpi(IConfigurationProvider.class, loader, configurationProviderExtensions);
			loadSpi(IMetricEnrichmentExtension.class, loader, metricEnrichmentExtensions);
		}

		final ExtensionManager extensionManager = builder
			.withProtocolExtensions(protocolExtensions)
			.withStrategyProviderExtensions(strategyProviderExtensions)
			.withConnectorStoreProviderExtensions(connectorStoreProviderExtensions)
			.withSourceComputationExtensions(sourceComputationExtensions)
			.withCompositeSourceScriptExtensions(compositeSourceScriptExtensions)
			.withConfigurationProviderExtensions(configurationProviderExtensions)
			.withMetricEnrichmentExtensions(metricEnrichmentExtensions)
			.withClassLoaders(new ArrayList<>(classLoaders))
			.build();

		return new ExtensionRuntime(extensionManager);
	}

	/**
	 * Runs the {@link ServiceLoader} for a single SPI against a single extension loader, keeps only
	 * the providers that loader owns, wraps each in a TCCL proxy, and appends them to {@code target}.
	 *
	 * @param <T>    the SPI interface type.
	 * @param spi    the SPI interface class.
	 * @param loader the extension loader to query.
	 * @param target the list to append the wrapped instances to.
	 */
	private static <T> void loadSpi(final Class<T> spi, final ExtensionClassLoader loader, final List<T> target) {
		for (final Provider<T> provider : ServiceLoader.load(spi, loader).stream().toList()) {
			// A provider visible through the parent or a delegate is registered during that owner's
			// own pass; skip it here to avoid loading the same extension twice.
			if (provider.type().getClassLoader() != loader) {
				continue;
			}
			final String providerClassName = provider.type().getName();
			try {
				final T instance = TcclClassLoaderDecorator.call(loader, provider::get);
				target.add(TcclClassLoaderDecorator.wrap(spi, instance, loader));
				log.info("Loaded {} '{}' from extension '{}'.", spi.getSimpleName(), providerClassName, loader.getName());
			} catch (Exception e) {
				log.error(
					"Failed to instantiate {} '{}' from extension '{}': {}",
					spi.getSimpleName(),
					providerClassName,
					loader.getName(),
					e.getMessage()
				);
				log.debug("Extension provider instantiation exception:", e);
			}
		}
	}

	/**
	 * Orders extensions so that every declared dependency precedes its dependents. Extensions whose
	 * declared dependency is absent, or that participate in a dependency cycle, are dropped with an
	 * error rather than crashing the agent.
	 *
	 * @param byId all discovered descriptors keyed by id.
	 * @return the enabled descriptors in dependency-first order.
	 */
	private static List<ExtensionDescriptor> orderByDependencies(final Map<String, ExtensionDescriptor> byId) {
		final List<ExtensionDescriptor> ordered = new ArrayList<>();
		final java.util.Set<String> resolved = new java.util.HashSet<>();
		final Map<String, ExtensionDescriptor> remaining = new LinkedHashMap<>(byId);

		// Disable extensions that require an id we never discovered.
		remaining
			.values()
			.removeIf(descriptor -> {
				final List<String> missing = descriptor
					.requires()
					.stream()
					.filter(requiredId -> !byId.containsKey(requiredId))
					.toList();
				if (!missing.isEmpty()) {
					log.error("Disabling extension '{}': required extension(s) {} not found.", descriptor.id(), missing);
					return true;
				}
				return false;
			});

		// Repeatedly emit descriptors whose dependencies are already resolved.
		boolean progress = true;
		while (progress) {
			progress = false;
			final var iterator = remaining.values().iterator();
			while (iterator.hasNext()) {
				final ExtensionDescriptor descriptor = iterator.next();
				if (resolved.containsAll(descriptor.requires())) {
					ordered.add(descriptor);
					resolved.add(descriptor.id());
					iterator.remove();
					progress = true;
				}
			}
		}

		// Whatever is left cannot be resolved (cyclic, or depends on a disabled extension).
		remaining
			.values()
			.forEach(descriptor ->
				log.error(
					"Disabling extension '{}': unresolved or cyclic dependency among {}.",
					descriptor.id(),
					descriptor.requires()
				)
			);

		return ordered;
	}

	/**
	 * Removes child-first prefixes that overlap the forced-parent list.
	 *
	 * @param descriptor the extension descriptor.
	 * @return the sanitized child-first prefix list.
	 */
	static List<String> sanitizeChildFirst(final ExtensionDescriptor descriptor) {
		return descriptor
			.childFirstPackages()
			.stream()
			.filter(prefix -> {
				// Reject a declared prefix that overlaps a forced-parent prefix in either direction:
				// a more specific prefix (e.g. "org.metricshub.engine.telemetry.") that falls under a
				// forced one, or a broader ancestor (e.g. "org.metricshub.") that would itself capture
				// a forced namespace and load engine classes child-first.
				final boolean forced = FORCED_PARENT_PREFIXES.stream().anyMatch(
					forcedPrefix -> prefix.startsWith(forcedPrefix) || forcedPrefix.startsWith(prefix)
				);
				if (forced) {
					log.warn(
						"Ignoring child-first prefix '{}' of extension '{}': it must resolve from the engine.",
						prefix,
						descriptor.id()
					);
				}
				return !forced;
			})
			.toList();
	}

	/**
	 * Converts a jar file to a URL, logging and returning {@code null} on failure.
	 *
	 * @param jarFile the jar file.
	 * @return the URL, or {@code null} if it cannot be built.
	 */
	private static URL toUrl(final File jarFile) {
		try {
			return jarFile.toURI().toURL();
		} catch (Exception e) {
			log.error("Cannot build a URL for extension jar '{}': {}", jarFile, e.getMessage());
			return null;
		}
	}

	@Override
	public void close() {
		extensionManager.close();
	}
}
