package org.metricshub.extension.jdbc.driver;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JDBC Extension
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

import java.io.IOException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
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
 * <p>Aggregates {@link JdbcDriverDescriptor}s from {@link JdbcDriverProvider}s and resolves driver
 * requests by {@code (driverClass, explicitJarPath)} or by JDBC URL prefix. Caches one
 * {@link LoadedDriver} per resolved key for the lifetime of the registry.
 *
 * <p>Resolution flow:
 * <ol>
 *   <li>Look up the descriptor for {@code driverClass}. If absent, a synthetic descriptor is
 *       fabricated (so unknown drivers can still be loaded if the JAR is present).</li>
 *   <li>If {@code origin == BUILT_IN} <em>and</em> no explicit JAR path is supplied, load the
 *       driver from {@link #parentLoader} (the JDBC extension's own classloader) — no isolated
 *       loader.</li>
 *   <li>Otherwise, ask {@link #jarLocator} for the JAR URLs, build an
 *       {@link IsolatedDriverClassLoader}, instantiate the driver, wrap it in a
 *       {@link ShieldedDriver} and register the shield with {@link DriverManager}.</li>
 * </ol>
 *
 * <p>The registry implements {@link AutoCloseable}: on close it deregisters every
 * {@code ShieldedDriver} and closes every isolated loader. Callers should close the registry on
 * agent shutdown to avoid the well-known abandoned-driver leak.
 */
@Slf4j
public final class JdbcDriverRegistry implements AutoCloseable {

	private final ClassLoader parentLoader;
	private final JdbcDriverJarLocator jarLocator;

	/** Descriptors keyed by fully-qualified driver class. Insertion order preserved for logs. */
	private final Map<String, JdbcDriverDescriptor> descriptorsByClass;

	/** Cache of resolved drivers, keyed by {@code (driverClass, explicitJarPath)}. */
	private final Map<Key, LoadedDriver> loadedByKey = new ConcurrentHashMap<>();

	/** Every {@link ShieldedDriver} we registered, in registration order, for clean shutdown. */
	private final List<ShieldedDriver> registeredShields = new CopyOnWriteArrayList<>();

	/** Isolated loaders we created, for shutdown. */
	private final List<IsolatedDriverClassLoader> isolatedLoaders = new CopyOnWriteArrayList<>();

	/**
	 * Primary constructor.
	 *
	 * @param descriptors  initial set of descriptors (typically aggregated from
	 *                     {@link JdbcDriverProvider}s).
	 * @param jarLocator   strategy for locating JAR(s) on disk; use
	 *                     {@link JdbcDriverJarLocator#noOp()} when no filesystem scanning is wired
	 *                     yet.
	 * @param parentLoader parent classloader used for built-ins and as the parent of every
	 *                     {@link IsolatedDriverClassLoader}.
	 */
	public JdbcDriverRegistry(
		final Collection<JdbcDriverDescriptor> descriptors,
		final JdbcDriverJarLocator jarLocator,
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
	 * Builds a registry by aggregating every {@link JdbcDriverProvider} discoverable via
	 * {@link ServiceLoader} on the given classloader.
	 *
	 * @param jarLocator   strategy for locating JAR(s) on disk.
	 * @param parentLoader parent classloader; also the loader used for service discovery.
	 * @return a new registry; never {@code null}.
	 */
	public static JdbcDriverRegistry buildDefault(final JdbcDriverJarLocator jarLocator, final ClassLoader parentLoader) {
		final List<JdbcDriverDescriptor> aggregated = new java.util.ArrayList<>();
		for (final JdbcDriverProvider provider : ServiceLoader.load(JdbcDriverProvider.class, parentLoader)) {
			aggregated.addAll(provider.provide());
		}
		return new JdbcDriverRegistry(aggregated, jarLocator, parentLoader);
	}

	/**
	 * @return an unmodifiable view of every known descriptor, in registration order.
	 */
	public Collection<JdbcDriverDescriptor> descriptors() {
		return descriptorsByClass.values();
	}

	/**
	 * Resolves the descriptor whose {@code urlPrefixes} contains a prefix of {@code url}.
	 *
	 * @param url a JDBC URL.
	 * @return the matching descriptor, or {@link Optional#empty()} when none matches.
	 */
	public Optional<JdbcDriverDescriptor> descriptorForUrl(final String url) {
		if (url == null) {
			return Optional.empty();
		}
		for (final JdbcDriverDescriptor d : descriptorsByClass.values()) {
			for (final String prefix : d.urlPrefixes()) {
				if (url.startsWith(prefix)) {
					return Optional.of(d);
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Resolves a {@link LoadedDriver} for the given driver class and optional explicit JAR path.
	 *
	 * <p>The result is cached; subsequent calls with the same key return the same instance.
	 *
	 * @param driverClass     fully-qualified driver class; required.
	 * @param explicitJarPath optional absolute, expanded JAR path expression (may contain glob
	 *                        meta-characters) previously resolved from
	 *                        {@code JdbcInfo.driverPath}; {@code null} triggers a scan of the
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
		final JdbcDriverDescriptor descriptor = descriptorsByClass.getOrDefault(
			key.driverClass(),
			syntheticDescriptor(key.driverClass())
		);

		final boolean useBuiltIn = descriptor.origin() == DriverOrigin.BUILT_IN && key.explicitJarPath() == null;
		if (useBuiltIn) {
			return loadFromParent(descriptor);
		}

		final Optional<JdbcDriverJarLocator.LocatedDriverJars> located = jarLocator.locate(
			key.driverClass(),
			key.explicitJarPath()
		);
		if (located.isEmpty()) {
			throw new DriverResolutionException(missingJarMessage(descriptor, key.explicitJarPath()));
		}

		final IsolatedDriverClassLoader loader = new IsolatedDriverClassLoader(
			loaderName(key),
			located.get().urls(),
			parentLoader,
			descriptor.driverPackages()
		);
		isolatedLoaders.add(loader);

		final Driver driver = instantiate(descriptor.driverClass(), loader);
		final JdbcDriverDescriptor resolvedDescriptor = withOrigin(descriptor, located.get().origin());
		// Skip DriverManager registration for explicit-jar resolutions: when the operator pinned a
		// specific JAR path, callers must reach this driver through the per-call selection (see
		// JdbcClient.execute(..., JdbcDriverSelection)). Registering a shield here would expose the
		// instance to legacy DriverManager.getConnection(url) lookups and could collide with another
		// resource that pinned a different JAR for the same driver class.
		if (key.explicitJarPath() == null) {
			registerShield(driver, resolvedDescriptor);
		}
		return new LoadedDriver(driver, loader, resolvedDescriptor);
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
		registerShield(driver, descriptor);
		return new LoadedDriver(driver, parentLoader, descriptor);
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
	 * Wraps {@code driver} in a {@link ShieldedDriver} loaded by the engine classloader and
	 * registers it with {@link DriverManager} so that URL-based connection routing works.
	 * The shield is tracked for deregistration in {@link #close()}.
	 *
	 * @param driver     the vendor driver instance to expose to {@link DriverManager}.
	 * @param descriptor descriptor kept on the shield for diagnostics.
	 * @throws DriverResolutionException when {@link DriverManager#registerDriver(Driver)} fails.
	 */
	private void registerShield(final Driver driver, final JdbcDriverDescriptor descriptor) {
		final ShieldedDriver shield = new ShieldedDriver(driver, descriptor);
		try {
			DriverManager.registerDriver(shield);
			registeredShields.add(shield);
			log.info("JDBC driver registered: {} origin={}", descriptor.driverClass(), descriptor.origin());
		} catch (SQLException e) {
			throw new DriverResolutionException("Failed to register JDBC driver: " + descriptor.driverClass(), e);
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
		return new JdbcDriverDescriptor(
			base.driverClass(),
			base.displayName(),
			base.urlPrefixes(),
			base.defaultPort(),
			origin,
			base.driverPackages(),
			base.validationQuery()
		);
	}

	/**
	 * Fabricates a minimal descriptor for an unknown driver class so the registry can still attempt
	 * to load a JAR contributed only at the filesystem level. Origin is set to
	 * {@link DriverOrigin#USER_DEFAULT}; URL prefixes and child-first packages are empty.
	 *
	 * @param driverClass fully-qualified driver class.
	 * @return a placeholder descriptor used only for resolution.
	 */
	private static JdbcDriverDescriptor syntheticDescriptor(final String driverClass) {
		return new JdbcDriverDescriptor(
			driverClass,
			driverClass,
			List.of(),
			-1,
			DriverOrigin.USER_DEFAULT,
			List.of(),
			null
		);
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
			return String.format(
				"JDBC driver %s not found. Drop the driver JAR into the operator-default drivers directory " +
					"(typically <INSTALL_DIR>/lib/extensions/jdbc/) or set jdbc.driverPath.",
				descriptor.driverClass()
			);
		}
		return String.format(
			"JDBC driver %s not found at %s. Verify the driverPath expression and that the JAR exists.",
			descriptor.driverClass(),
			explicitJarPath
		);
	}

	/**
	 * Releases every resource owned by the registry: deregisters every {@link ShieldedDriver} from
	 * {@link DriverManager}, closes every {@link IsolatedDriverClassLoader} and clears the
	 * resolution cache. Safe to call multiple times. Must be invoked on agent shutdown to avoid
	 * the well-known abandoned-driver leak.
	 */
	@Override
	public synchronized void close() {
		for (final ShieldedDriver shield : registeredShields) {
			try {
				DriverManager.deregisterDriver(shield);
			} catch (SQLException e) {
				log.debug("Failed to deregister shielded driver {}", shield.descriptor().driverClass(), e);
			}
		}
		registeredShields.clear();
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
	 *                        {@code JdbcInfo.driverPath}; {@code null} selects the operator-default
	 *                        drivers directory scan.
	 */
	record Key(String driverClass, String explicitJarPath) {}
}
