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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.DriverInfo;
import org.metricshub.extension.jdbc.client.DatabaseLogUtils;

/**
 * Process-wide holder for the singleton {@link JdbcDriverRegistry}.
 *
 * <p>The registry is created lazily on first call to {@link #get()}. Subsequent calls return the
 * same instance.
 *
 * <h2>Driver directory resolution</h2>
 *
 * On lazy initialization, the operator-default directory used by {@link FilesystemDriverScanner}
 * (scanned only when {@code DriverInfo.jarPath} is null) is resolved with the following
 * precedence (first non-blank wins):
 * <ol>
 *   <li>System property {@code metricshub.jdbc.driversDir}.</li>
 *   <li>Environment variable {@code METRICSHUB_JDBC_DRIVERS_DIR}.</li>
 *   <li>App-relative {@code $APP_DIR/extensions/jdbc/} computed via
 *       {@link JdbcAppDir#resolveSubPath(String)} (mirrors {@code ConfigHelper.getSubPath}):
 *       {@code /opt/metricshub/lib/extensions/jdbc/} on Linux,
 *       {@code C:\Program Files\MetricsHub\extensions\jdbc\} on Windows.</li>
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
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class JdbcDriverRegistryHolder {

	/** System property used to override the driver directory. */
	public static final String DRIVERS_DIR_PROPERTY = "metricshub.jdbc.driversDir";

	/** Environment variable used to override the driver directory. */
	public static final String DRIVERS_DIR_ENV = "METRICSHUB_JDBC_DRIVERS_DIR";

	/** App-relative sub-path resolved when no explicit override is provided. */
	public static final String DEFAULT_APP_SUBPATH = "extensions/jdbc";

	private static final AtomicReference<JdbcDriverRegistry> INSTANCE = new AtomicReference<>();

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
	 * Translates a {@link DriverInfo} block into an actionable {@link JdbcDriverSelection}.
	 *
	 * <p>This is a pure translation: it does <em>not</em> attempt to load the driver. Actual
	 * resolution happens at the connection site (e.g. inside {@code isDatabaseAlive} or the SQL
	 * processors), so a declared-but-unresolvable driver surfaces a precise
	 * {@link DriverResolutionException} message — instead of being silently demoted to
	 * {@code null}, which would let callers fall back to built-in inference and hide the real
	 * problem.
	 *
	 * <p>When the {@code jarPath} expression itself is malformed (unknown placeholder, traversal
	 * segment, ...) the bad path is rejected and logged at WARN, but the declared
	 * {@code className} is <em>preserved</em>. The returned selection carries a {@code null}
	 * {@code explicitJarPath}, so the registry still attempts to resolve the operator's declared
	 * class via the parent classloader and the scanned drivers directory rather than silently
	 * falling back to URL-based driver inference (which could pick an unintended driver).
	 *
	 * <p>Returns {@code null} only when nothing was declared ({@code info == null} or className is
	 * blank).
	 *
	 * @param info the JDBC requirement; may be {@code null}, in which case this method returns
	 *             {@code null}.
	 * @return the declared selection, or {@code null} when nothing usable was declared.
	 */
	public static JdbcDriverSelection resolveSelection(final DriverInfo info) {
		if (info == null) {
			return null;
		}
		final String driverClass = info.getClassName();
		if (driverClass == null || driverClass.isBlank()) {
			return null;
		}
		DatabaseLogUtils.disableLogging(driverClass);
		String explicitJarPath;
		try {
			explicitJarPath = resolveDriverPath(info.getJarPath());
		} catch (IllegalArgumentException e) {
			// Reject the malformed path but keep the declared className so the registry can still
			// honor the operator's intent (parent loader / scanned drivers directory) instead of
			// silently demoting the whole selection and letting URL-based inference pick a
			// different driver.
			log.warn(
				"Invalid jarPath {} for className={}: {}. Path is ignored; resolution will use the operator-default drivers directory or the parent classloader.",
				info.getJarPath(),
				driverClass,
				e.getMessage()
			);
			explicitJarPath = null;
		}
		return new JdbcDriverSelection(driverClass, explicitJarPath);
	}

	/**
	 * Picks the first registered descriptor whose driver accepts {@code jdbcUrl}.
	 *
	 * <p>This drives the zero-config fallback for the health check and for source/criterion
	 * processing when neither the resource nor any connector declares a driver. Each descriptor is
	 * tried with {@code null} jarPath so the registry uses its standard resolution path: isolated
	 * loader if a JAR is located in the operator-default drivers directory, otherwise the parent
	 * classloader (which catches drivers shipped on the agent classpath, e.g. an enterprise
	 * distribution that bundles Oracle/JTOpen/DB2). Resolution failures are logged at DEBUG and
	 * skipped so a single missing JAR does not abort the scan.
	 *
	 * @param jdbcUrl the JDBC URL to match; may be {@code null} or blank, in which case this method
	 *                returns {@code null}.
	 * @return a {@link JdbcDriverSelection} for the first matching descriptor, or {@code null}
	 *         when no resolvable descriptor accepts the URL.
	 */
	public static JdbcDriverSelection findSelectionForUrl(final String jdbcUrl) {
		if (jdbcUrl == null || jdbcUrl.isBlank()) {
			return null;
		}
		final JdbcDriverRegistry registry = get();
		for (final JdbcDriverDescriptor descriptor : registry.descriptors()) {
			final String driverClass = descriptor.driverClass();
			final LoadedDriver loaded;
			try {
				loaded = registry.resolve(driverClass, null);
			} catch (DriverResolutionException e) {
				log.debug("findSelectionForUrl: skipping {} for URL {} ({})", driverClass, jdbcUrl, e.getMessage());
				continue;
			}
			final boolean accepts;
			try {
				accepts = loaded.driver().acceptsURL(jdbcUrl);
			} catch (SQLException e) {
				log.debug("findSelectionForUrl: {}.acceptsURL threw for URL {} ({})", driverClass, jdbcUrl, e.getMessage());
				continue;
			}
			if (accepts) {
				DatabaseLogUtils.disableLogging(driverClass);
				return new JdbcDriverSelection(driverClass, null);
			}
		}
		return null;
	}

	/**
	 * Lazily-initialized path-expression resolver shared by every call into the holder.
	 */
	private static final class PathExpressionHolder {

		/** The shared resolver instance. */
		static final JdbcPathExpression INSTANCE = new JdbcPathExpression();

		private PathExpressionHolder() {}
	}

	/**
	 * Resolves {@code driverPath}, returning {@code null} when no path is declared.
	 *
	 * @param driverPath raw expression coming from {@link DriverInfo#getJarPath()}; may be
	 *                   {@code null}.
	 * @return the expanded path expression, or {@code null} when {@code driverPath} is
	 *         {@code null}.
	 * @throws IllegalArgumentException when the expression is invalid.
	 */
	private static String resolveDriverPath(final String driverPath) {
		if (driverPath == null) {
			return null;
		}
		return PathExpressionHolder.INSTANCE.resolve(driverPath);
	}

	/**
	 * Disposes the current registry instance, if any, and clears the holder.
	 *
	 * <p>Intended for unit-test isolation. Calling this from production code defeats the
	 * registration cache and leaks isolated classloaders until the next {@link #get()}.
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
	 * Builds a fresh registry using {@link JdbcDriverRegistry#buildDefault(IJdbcDriverJarLocator, ClassLoader)}
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
	 * Emits one INFO log line per discovered JDBC driver descriptor (driver class, origin). Called
	 * once on lazy initialisation to give operators a clear inventory of which drivers MetricsHub
	 * knows about and where they come from.
	 *
	 * @param registry the freshly-built registry whose descriptors should be enumerated.
	 */
	private static void logDiscoveredDescriptors(final JdbcDriverRegistry registry) {
		for (final JdbcDriverDescriptor d : registry.descriptors()) {
			log.info("JDBC driver discovered: {} [origin={}].", d.driverClass(), d.origin());
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
		return JdbcAppDir.resolveSubPath(DEFAULT_APP_SUBPATH);
	}
}
