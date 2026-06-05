package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import java.io.IOException;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * Engine-scoped registry of JDBC drivers.
 *
 * <p>Aggregates {@link JdbcDriverDescriptor}s from {@link IJdbcDriverProvider}s and resolves driver
 * requests by {@code (driverClass, explicitJarPath)}. Caches one {@link LoadedDriver} per resolved
 * key for the lifetime of the registry.
 *
 * <p>Resolution flow:
 * <ol>
 *   <li>Look up the descriptor for {@code driverClass}. If absent, throw
 *       {@link DriverResolutionException}: every supported driver must be advertised by a
 *       {@link IJdbcDriverProvider}.</li>
 *   <li>If {@code origin == BUILT_IN} <em>and</em> no explicit JAR path is supplied, load the
 *       driver from {@link #parentLoader} (the JDBC extension's own classloader) — no isolated
 *       loader.</li>
 *   <li>Otherwise, ask {@link #jarLocator} for the JAR URLs, build an
 *       {@link IsolatedDriverClassLoader} and instantiate the driver.</li>
 * </ol>
 *
 * <p>Drivers obtained from this registry are used directly through
 * {@link LoadedDriver#driver()}; the registry never registers them with
 * {@link java.sql.DriverManager}.
 *
 * <p>The registry implements {@link AutoCloseable}: on close it closes every isolated loader.
 * Callers should close the registry on agent shutdown.
 */
@Slf4j
public final class JdbcDriverRegistry implements AutoCloseable {

	private final ClassLoader parentLoader;
	private final IJdbcDriverJarLocator jarLocator;

	/** Descriptors keyed by fully-qualified driver class. Insertion order preserved for logs. */
	private final Map<String, JdbcDriverDescriptor> descriptorsByClass;

	/** Cache of resolved drivers, keyed by {@code (driverClass, explicitJarPath)}. */
	private final Map<Key, LoadedDriver> loadedByKey = new ConcurrentHashMap<>();

	/** Every isolated loader this registry owns, recorded at creation so close() never leaks. */
	private final List<IsolatedDriverClassLoader> isolatedLoaders = new CopyOnWriteArrayList<>();

	/**
	 * Primary constructor.
	 *
	 * @param descriptors  initial set of descriptors (typically aggregated from
	 *                     {@link IJdbcDriverProvider}s).
	 * @param jarLocator   strategy for locating JAR(s) on disk.
	 * @param parentLoader parent classloader used for built-ins and as the parent of every
	 *                     {@link IsolatedDriverClassLoader}.
	 */
	public JdbcDriverRegistry(
		final Collection<JdbcDriverDescriptor> descriptors,
		final IJdbcDriverJarLocator jarLocator,
		final ClassLoader parentLoader
	) {
		this.parentLoader = Objects.requireNonNull(parentLoader, "parentLoader");
		this.jarLocator = Objects.requireNonNull(jarLocator, "jarLocator");
		Objects.requireNonNull(descriptors, "descriptors");

		final Map<String, JdbcDriverDescriptor> byClass = new LinkedHashMap<>();
		for (final JdbcDriverDescriptor d : descriptors) {
			final JdbcDriverDescriptor previous = byClass.putIfAbsent(d.driverClass(), d);
			if (previous != null) {
				log.warn(
					"Duplicate JDBC driver descriptor for {} (kept first from {}, ignored {}).",
					d.driverClass(),
					previous.origin(),
					d.origin()
				);
			}
		}
		this.descriptorsByClass = Collections.unmodifiableMap(byClass);
	}

	/**
	 * Builds a registry by aggregating every {@link IJdbcDriverProvider} discoverable via
	 * {@link ServiceLoader} on the given classloader.
	 *
	 * @param jarLocator   strategy for locating JAR(s) on disk.
	 * @param parentLoader parent classloader; also the loader used for service discovery.
	 * @return a new registry; never {@code null}.
	 */
	public static JdbcDriverRegistry buildDefault(
		final IJdbcDriverJarLocator jarLocator,
		final ClassLoader parentLoader
	) {
		final List<JdbcDriverDescriptor> aggregated = new ArrayList<>();
		for (final IJdbcDriverProvider provider : ServiceLoader.load(IJdbcDriverProvider.class, parentLoader)) {
			aggregated.addAll(provider.provide());
		}
		return new JdbcDriverRegistry(aggregated, jarLocator, parentLoader);
	}

	/**
	 * @return an unmodifiable view of every known descriptor, in registration order.
	 */
	Collection<JdbcDriverDescriptor> descriptors() {
		return descriptorsByClass.values();
	}

	/**
	 * Resolves a {@link LoadedDriver} for the given driver class and optional explicit JAR path.
	 *
	 * <p>The result is cached; subsequent calls with the same key return the same instance.
	 *
	 * @param driverClass     fully-qualified driver class; required.
	 * @param explicitJarPath optional absolute, expanded JAR path expression (may contain glob
	 *                        meta-characters) previously resolved from
	 *                        {@code DriverInfo.jarPath}; {@code null} triggers a scan of the
	 *                        operator-default drivers directory.
	 * @return the loaded driver; never {@code null}.
	 * @throws DriverResolutionException when the driver cannot be located, loaded, or instantiated.
	 */
	public LoadedDriver resolve(final String driverClass, final String explicitJarPath) {
		Objects.requireNonNull(driverClass, "driverClass");
		final Key key = new Key(driverClass, explicitJarPath);
		final LoadedDriver cached = loadedByKey.get(key);
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			final LoadedDriver again = loadedByKey.get(key);
			if (again != null) {
				return again;
			}
			final LoadedDriver loaded = doResolve(key);
			loadedByKey.put(key, loaded);
			return loaded;
		}
	}

	/**
	 * Performs the actual resolution for {@link #resolve(String, String)}. Called under the
	 * registry monitor with the cache miss already confirmed; never invoked twice for the same key.
	 *
	 * @param key the {@code (driverClass, explicitJarPath)} pair being resolved.
	 * @return the freshly-loaded driver to be cached and returned to the caller.
	 * @throws DriverResolutionException when the locator returns nothing or instantiation fails.
	 */
	private LoadedDriver doResolve(final Key key) {
		final JdbcDriverDescriptor descriptor = descriptorsByClass.get(key.driverClass());
		if (descriptor == null) {
			throw new DriverResolutionException(
				"""
				Unknown JDBC driver class: %s. Declare it through a JdbcDriverProvider or check the \
				connector configuration.""".formatted(key.driverClass())
			);
		}

		final boolean useBuiltIn = descriptor.origin() == DriverOrigin.BUILT_IN && key.explicitJarPath() == null;
		if (useBuiltIn) {
			return loadFromParent(descriptor);
		}

		final Optional<IJdbcDriverJarLocator.LocatedDriverJars> located = jarLocator.locate(
			key.driverClass(),
			key.explicitJarPath()
		);
		if (located.isEmpty()) {
			// No JAR was located. When the caller did not supply an explicit jarPath, the driver
			// may still be reachable through the parent classloader (typical of bundled-driver
			// distributions where the JAR is on the agent classpath rather than under the
			// operator-default drivers directory). Fall back to the parent loader in that case;
			// only reject when the class is absent from both isolated jars and the parent.
			if (key.explicitJarPath() == null && isClassOnParent(descriptor.driverClass())) {
				return loadFromParent(descriptor);
			}
			throw new DriverResolutionException(missingJarMessage(descriptor, key.explicitJarPath()));
		}

		final IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
			loaderName(key),
			located.get().urls(),
			parentLoader,
			descriptor.driverPackages()
		);
		try {
			final Driver driver = instantiate(descriptor.driverClass(), loader);

			isolatedLoaders.add(loader);

			final JdbcDriverDescriptor resolvedDescriptor = withOrigin(descriptor, located.get().origin());

			log.info("JDBC driver loaded: {} origin={}", resolvedDescriptor.driverClass(), resolvedDescriptor.origin());

			return new LoadedDriver(driver, loader, resolvedDescriptor);
		} catch (RuntimeException e) {
			try {
				loader.close();
			} catch (IOException ioe) {
				log.debug("Failed to close isolated driver classloader {}", loader.getName(), ioe);
			}

			throw e;
		}
	}

	/**
	 * Builds a human-friendly name for the isolated loader. Includes the JAR filename when an
	 * explicit path was used so distinct loaders show up clearly in logs.
	 *
	 * @param key the resolution key.
	 * @return a non-null name in the form {@code driverClass@default} or
	 *         {@code driverClass@jarFileName}.
	 */
	private static String loaderName(final Key key) {
		if (key.explicitJarPath() == null) {
			return key.driverClass() + "@default";
		}
		final String path = key.explicitJarPath();
		final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		final String tail = slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
		return key.driverClass() + "@" + tail;
	}

	/**
	 * Loads a {@link DriverOrigin#BUILT_IN} driver directly from {@link #parentLoader} (the JDBC
	 * extension's own classloader) — no {@link IsolatedDriverClassLoader} is created.
	 *
	 * @param descriptor the built-in descriptor to load.
	 * @return a {@link LoadedDriver} whose {@code classLoader} is {@link #parentLoader}.
	 */
	private LoadedDriver loadFromParent(final JdbcDriverDescriptor descriptor) {
		final Driver driver = instantiate(descriptor.driverClass(), parentLoader);
		log.info("JDBC driver loaded: {} origin={}", descriptor.driverClass(), descriptor.origin());
		return new LoadedDriver(driver, parentLoader, descriptor);
	}

	/**
	 * Tests whether {@code driverClass} is reachable through {@link #parentLoader} without
	 * triggering its static initialiser. Used to decide whether to fall back to the parent
	 * classloader when no isolated JAR was located for a {@link DriverOrigin#USER_DEFAULT}
	 * descriptor.
	 *
	 * @param driverClass fully-qualified driver class name.
	 * @return {@code true} when the class is visible to the parent loader, {@code false}
	 *         otherwise.
	 */
	private boolean isClassOnParent(final String driverClass) {
		try {
			Class.forName(driverClass, false, parentLoader);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/**
	 * Loads {@code driverClass} from {@code loader} and instantiates it via the public no-arg
	 * constructor required by JDBC.
	 *
	 * @param driverClass fully-qualified driver class name.
	 * @param loader      classloader to load from (either the parent or an
	 *                    {@link IsolatedDriverClassLoader}).
	 * @return the instantiated {@link Driver}.
	 * @throws DriverResolutionException when the class is missing, does not implement
	 *                                   {@link Driver}, or cannot be instantiated.
	 */
	private static Driver instantiate(final String driverClass, final ClassLoader loader) {
		try {
			final Class<?> clazz = Class.forName(driverClass, true, loader);
			if (!Driver.class.isAssignableFrom(clazz)) {
				throw new DriverResolutionException(driverClass + " does not implement java.sql.Driver");
			}
			return (Driver) clazz.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			throw new DriverResolutionException("JDBC driver class not found: " + driverClass, e);
		} catch (ReflectiveOperationException e) {
			throw new DriverResolutionException("Failed to instantiate JDBC driver: " + driverClass, e);
		}
	}

	/**
	 * Returns a copy of {@code base} whose {@link JdbcDriverDescriptor#origin()} is replaced by
	 * {@code origin}. The original is returned unchanged when the origin already matches.
	 *
	 * @param base   the descriptor to copy.
	 * @param origin the origin reported by the locator (e.g. {@link DriverOrigin#USER_EXPLICIT}).
	 * @return a descriptor with the requested origin.
	 */
	private static JdbcDriverDescriptor withOrigin(final JdbcDriverDescriptor base, final DriverOrigin origin) {
		if (base.origin() == origin) {
			return base;
		}
		return new JdbcDriverDescriptor(base.driverClass(), base.displayName(), origin, base.driverPackages());
	}

	/**
	 * Builds the operator-facing diagnostic raised when the locator cannot find a JAR for a
	 * descriptor. The message names the missing driver and, when present, the explicit JAR path
	 * (or glob) that yielded no match.
	 *
	 * @param descriptor      the descriptor that failed to resolve.
	 * @param explicitJarPath the explicit path requested; {@code null} when the default directory
	 *                        was scanned.
	 * @return a single-line, operator-facing diagnostic message.
	 */
	private static String missingJarMessage(final JdbcDriverDescriptor descriptor, final String explicitJarPath) {
		if (explicitJarPath == null) {
			return """
			JDBC driver %s not found. Drop the driver JAR into the operator-default drivers directory \
			(typically $APP_DIR/extensions/jdbc/) or set jdbc.driver.jarPath.\
			""".formatted(descriptor.driverClass());
		}
		return """
		JDBC driver %s not found at %s. Verify the jarPath expression and that the JAR exists.\
		""".formatted(descriptor.driverClass(), explicitJarPath);
	}

	/**
	 * Releases every resource owned by the registry: closes every {@link IsolatedDriverClassLoader}
	 * and clears the resolution cache. Safe to call multiple times. Must be invoked on agent
	 * shutdown to avoid the well-known abandoned-driver leak.
	 */
	@Override
	public synchronized void close() {
		for (final IsolatedDriverClassLoader loader : isolatedLoaders) {
			try {
				loader.close();
			} catch (IOException e) {
				log.debug("Failed to close isolated driver classloader {}", loader.getName(), e);
			}
		}
		isolatedLoaders.clear();
		loadedByKey.clear();
	}

	/**
	 * Internal cache key identifying one resolved driver instance.
	 *
	 * @param driverClass     fully-qualified {@link Driver} implementation class; required.
	 * @param explicitJarPath absolute, expanded JAR path expression previously resolved from
	 *                        {@code DriverInfo.jarPath}; {@code null} selects the operator-default
	 *                        drivers directory scan.
	 */
	record Key(String driverClass, String explicitJarPath) {}
}
