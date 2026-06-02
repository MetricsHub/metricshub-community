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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.JdbcInfo;
import org.metricshub.extension.jdbc.client.DatabaseLogUtils;
import org.metricshub.extension.jdbc.driver.JdbcPathExpression.Scope;

/**
 * Process-wide holder for the singleton {@link JdbcDriverRegistry}.
 *
 * <p>The registry is created lazily on first call to {@link #get()} or
 * {@link #ensureDriverForUrl(String)}. Subsequent calls return the same instance.
 *
 * <h2>Driver directory resolution</h2>
 *
 * On lazy initialisation, the operator-default directory used by {@link FilesystemDriverScanner}
 * (scanned only when {@code JdbcInfo.driverPath} is null) is resolved with the following
 * precedence (first non-blank wins):
 * <ol>
 *   <li>System property {@code metricshub.jdbc.driversDir}.</li>
 *   <li>Environment variable {@code METRICSHUB_JDBC_DRIVERS_DIR}.</li>
 *   <li>Install-relative {@code <INSTALL_DIR>/lib/extensions/jdbc/} computed via
 *       {@link JdbcInstallDir#resolveSubPath(String)} (mirrors {@code ConfigHelper.getSubPath}).</li>
 * </ol>
 *
 * <p>The directory does not need to exist; absence simply means the scanner returns no JARs and
 * the registry resolves only built-in (parent-loaded) drivers.
 *
 * <h2>Test reset</h2>
 *
 * {@link #resetForTests()} disposes and clears the singleton. Production code must never call it.
 */
@Slf4j
public final class JdbcDriverRegistryHolder {

	/** System property used to override the driver directory. */
	public static final String DRIVERS_DIR_PROPERTY = "metricshub.jdbc.driversDir";

	/** Environment variable used to override the driver directory. */
	public static final String DRIVERS_DIR_ENV = "METRICSHUB_JDBC_DRIVERS_DIR";

	/** Install-relative sub-path resolved when no explicit override is provided. */
	public static final String DEFAULT_INSTALL_SUBPATH = "extensions/jdbc";

	private static final AtomicReference<JdbcDriverRegistry> INSTANCE = new AtomicReference<>();

	private JdbcDriverRegistryHolder() {
		// utility holder
	}

	/**
	 * Returns the process-wide registry, creating it on first call.
	 *
	 * @return the singleton {@link JdbcDriverRegistry}; never {@code null}.
	 */
	public static JdbcDriverRegistry get() {
		final JdbcDriverRegistry current = INSTANCE.get();
		if (current != null) {
			return current;
		}
		synchronized (JdbcDriverRegistryHolder.class) {
			final JdbcDriverRegistry again = INSTANCE.get();
			if (again != null) {
				return again;
			}
			final JdbcDriverRegistry built = build();
			INSTANCE.set(built);
			return built;
		}
	}

	/**
	 * Ensures the driver claimed by {@code url}'s prefix is registered with {@link java.sql.DriverManager}.
	 *
	 * <p>This is a best-effort hook: when no descriptor matches the URL (the typical case for
	 * built-in drivers that self-register via JDBC 4.0 auto-discovery), the call is a no-op.
	 * Resolution failures are logged at debug and swallowed so that legacy URL-based lookups keep
	 * working through the existing {@link java.sql.DriverManager} chain.
	 *
	 * @param url a JDBC URL; may be {@code null} or blank, in which case this method does nothing.
	 */
	public static void ensureDriverForUrl(final String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		final JdbcDriverRegistry registry = get();
		final Optional<JdbcDriverDescriptor> descriptor = registry.descriptorForUrl(url);
		if (descriptor.isEmpty()) {
			return;
		}
		final String driverClass = descriptor.get().driverClass();
		DatabaseLogUtils.disableLogging(driverClass);
		try {
			registry.resolve(driverClass, (String) null);
		} catch (DriverResolutionException e) {
			log.debug("JDBC driver resolution failed for URL {}: {}", url, e.getMessage());
		}
	}

	/**
	 * Resolves a {@link JdbcInfo} block into an actionable {@link JdbcDriverSelection}, primes the
	 * registry cache as a side effect, and returns the selection so that callers can carry it into
	 * the per-call code path (where it bypasses {@link java.sql.DriverManager} routing).
	 *
	 * <p>Resolution failures (missing JAR, bad class, scope violation) are logged at DEBUG and
	 * cause this method to return {@code null}. The eventual {@code Driver.connect} call will
	 * surface the failure as a {@link java.sql.SQLException} with the proper message.
	 *
	 * @param info  the JDBC requirement; may be {@code null}, in which case this method returns
	 *              {@code null}.
	 * @param scope security scope governing the {@code driverPath} expression. Use
	 *              {@link Scope#RESOURCE} for blocks coming from {@code metricshub.yaml} and
	 *              {@link Scope#CONNECTOR} for blocks coming from a connector header.
	 * @return the resolved selection, or {@code null} when {@code info} is missing/blank or the
	 *         expression cannot be resolved.
	 */
	public static JdbcDriverSelection resolveSelection(final JdbcInfo info, final Scope scope) {
		if (info == null) {
			return null;
		}
		final String driverClass = info.getDriverClass();
		if (driverClass == null || driverClass.isBlank()) {
			return null;
		}
		DatabaseLogUtils.disableLogging(driverClass);
		final String explicitJarPath;
		try {
			explicitJarPath = resolveDriverPath(info.getDriverPath(), scope);
		} catch (IllegalArgumentException e) {
			log.debug("Invalid driverPath for driverClass={} (scope={}): {}", driverClass, scope, e.getMessage());
			return null;
		}
		final JdbcDriverSelection selection = new JdbcDriverSelection(driverClass, explicitJarPath);
		try {
			get().resolve(driverClass, explicitJarPath);
		} catch (DriverResolutionException e) {
			log.debug(
				"JDBC driver resolution failed for driverClass={} driverPath={}: {}",
				driverClass,
				explicitJarPath,
				e.getMessage()
			);
			return null;
		}
		return selection;
	}

	/**
	 * Lazily-initialised path-expression resolver shared by every call into the holder.
	 */
	private static final class PathExpressionHolder {

		/** The shared resolver instance. */
		static final JdbcPathExpression INSTANCE = new JdbcPathExpression();

		private PathExpressionHolder() {}
	}

	/**
	 * Resolves {@code driverPath} against the supplied scope, returning {@code null} when no path
	 * is declared.
	 *
	 * @param driverPath raw expression coming from {@link JdbcInfo#getDriverPath()}; may be
	 *                   {@code null}.
	 * @param scope      security scope.
	 * @return the expanded path expression, or {@code null} when {@code driverPath} is
	 *         {@code null}.
	 * @throws IllegalArgumentException when the expression violates the scope rules.
	 */
	private static String resolveDriverPath(final String driverPath, final Scope scope) {
		if (driverPath == null) {
			return null;
		}
		return PathExpressionHolder.INSTANCE.resolve(driverPath, scope);
	}

	/**
	 * Disposes the current registry instance, if any, and clears the holder.
	 *
	 * <p>Intended for unit-test isolation. Calling this from production code defeats the
	 * registration cache and leaks shielded drivers until the next {@link #get()}.
	 */
	public static void resetForTests() {
		synchronized (JdbcDriverRegistryHolder.class) {
			final JdbcDriverRegistry previous = INSTANCE.getAndSet(null);
			if (previous != null) {
				previous.close();
			}
		}
	}

	/**
	 * Builds a fresh registry using {@link JdbcDriverRegistry#buildDefault(JdbcDriverJarLocator, ClassLoader)}
	 * with a {@link FilesystemDriverScanner} rooted at {@link #resolveDriversDir()}.
	 *
	 * @return a new {@link JdbcDriverRegistry}; never {@code null}.
	 */
	private static JdbcDriverRegistry build() {
		final Path baseDir = resolveDriversDir();
		log.info("JDBC driver registry initialising. Base directory: {} (exists={}).", baseDir, Files.isDirectory(baseDir));
		final FilesystemDriverScanner scanner = new FilesystemDriverScanner(baseDir);
		final JdbcDriverRegistry registry = JdbcDriverRegistry.buildDefault(
			scanner,
			JdbcDriverRegistryHolder.class.getClassLoader()
		);
		logDiscoveredDescriptors(registry);
		return registry;
	}

	/**
	 * Emits one INFO log line per discovered JDBC driver descriptor (driver class, origin, URL
	 * prefixes). Called once on lazy initialisation to give operators a clear inventory of which
	 * drivers MetricsHub knows about and where they come from.
	 *
	 * @param registry the freshly-built registry whose descriptors should be enumerated.
	 */
	private static void logDiscoveredDescriptors(final JdbcDriverRegistry registry) {
		for (final JdbcDriverDescriptor d : registry.descriptors()) {
			log.info("JDBC driver discovered: {} [origin={}, urlPrefixes={}].", d.driverClass(), d.origin(), d.urlPrefixes());
		}
	}

	/**
	 * Resolves the JDBC drivers directory using the documented precedence chain. See the
	 * class-level Javadoc for details.
	 *
	 * @return the resolved {@link Path}; never {@code null}, may not exist on disk.
	 */
	static Path resolveDriversDir() {
		final String fromProperty = System.getProperty(DRIVERS_DIR_PROPERTY);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return Paths.get(fromProperty);
		}
		final String fromEnv = System.getenv(DRIVERS_DIR_ENV);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return Paths.get(fromEnv);
		}
		return JdbcInstallDir.resolveSubPath(DEFAULT_INSTALL_SUBPATH);
	}
}
