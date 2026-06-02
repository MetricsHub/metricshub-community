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

import java.net.URL;
import java.util.Optional;

/**
 * Strategy for locating the JAR(s) that provide a given JDBC driver class on disk.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li>{@code explicitJarPath != null} → resolve a specific JAR file (or a single glob match)
 *       previously expanded by {@link JdbcPathExpression} from a
 *       {@link org.metricshub.engine.connector.model.identity.JdbcInfo#getDriverPath()}.
 *       Origin is {@link DriverOrigin#USER_EXPLICIT}.</li>
 *   <li>{@code explicitJarPath == null} → scan the operator-default drivers directory for any
 *       JAR exposing {@code driverClass}. Origin is {@link DriverOrigin#USER_DEFAULT}.</li>
 * </ul>
 *
 * <p>The path is passed as a {@link String} (not a {@link java.nio.file.Path}) because it may
 * contain glob meta-characters like {@code *} that {@link java.nio.file.Path} cannot represent on
 * Windows.
 *
 * <p>This interface is intentionally small so Phase 0 can ship without a real filesystem scanner.
 * Tests inject a stub; production wiring uses {@code FilesystemDriverScanner}.
 */
public interface JdbcDriverJarLocator {
	/**
	 * Locates the JAR(s) that provide the given driver class.
	 *
	 * @param driverClass     fully-qualified driver class name; never {@code null}.
	 * @param explicitJarPath optional absolute, expanded JAR path expression (may contain glob
	 *                        meta-characters) previously resolved from {@code JdbcInfo.driverPath}.
	 *                        When {@code null}, the implementation scans its configured default
	 *                        directory.
	 * @return the located classpath, or {@link Optional#empty()} when no matching JAR is found.
	 */
	Optional<LocatedDriverJars> locate(String driverClass, String explicitJarPath);

	/**
	 * The result of a successful location lookup.
	 *
	 * @param urls   classpath entries to feed to a {@link IsolatedDriverClassLoader}; must contain
	 *               at least one URL.
	 * @param origin where the JAR(s) were found, for diagnostics.
	 */
	record LocatedDriverJars(URL[] urls, DriverOrigin origin) {}

	/**
	 * A no-op locator that always returns {@link Optional#empty()}. Useful in tests and as a
	 * default placeholder before Phase 1 wires up the filesystem scanner.
	 *
	 * @return a singleton no-op locator.
	 */
	static JdbcDriverJarLocator noOp() {
		return NoOpHolder.INSTANCE;
	}

	/**
	 * Singleton holder for {@link #noOp()}.
	 */
	final class NoOpHolder {

		/** The shared no-op locator instance. */
		static final JdbcDriverJarLocator INSTANCE = (_, _) -> Optional.empty();

		/** Prevents instantiation of this holder. */
		private NoOpHolder() {}
	}
}
