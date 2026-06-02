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

import java.util.Collection;

/**
 * Service provider interface for contributing {@link JdbcDriverDescriptor}s to the
 * {@link JdbcDriverRegistry}.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} from the extension
 * classloader. Each provider returns a collection of descriptors; the registry aggregates them and
 * resolves driver lookups by {@code (driverClass, explicitJarPath)} or by URL prefix.
 *
 * <p>Two kinds of providers are expected in practice:
 * <ul>
 *   <li>A <em>built-in provider</em> shipped inside {@code metricshub-jdbc-extension.jar} that
 *       advertises the shaded drivers (MariaDB, PostgreSQL, MySQL, H2) with
 *       {@link DriverOrigin#BUILT_IN}.</li>
 *   <li><em>Descriptor-only providers</em> (also shipped in the JDBC extension) that advertise
 *       well-known external drivers (Oracle, SQL Server, JTOpen, ...) without bundling the JAR.
 *       The registry then resolves the JAR from {@code <INSTALL_DIR>/extensions/jdbc/} at first
 *       use.</li>
 * </ul>
 */
public interface JdbcDriverProvider {
	/**
	 * Returns the descriptors contributed by this provider.
	 *
	 * @return a non-{@code null} collection of {@link JdbcDriverDescriptor}s; may be empty.
	 */
	Collection<JdbcDriverDescriptor> provide();
}
