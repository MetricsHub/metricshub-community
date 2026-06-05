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

import java.util.List;
import java.util.Objects;

/**
 * Static, pure metadata about a JDBC driver that the registry knows how to resolve.
 *
 * <p>A descriptor does not carry the {@link java.sql.Driver} instance, nor any {@link ClassLoader}.
 * It is the recipe used by {@link JdbcDriverRegistry} to locate and load a driver on first use.
 *
 * <p>Descriptors are returned by {@link JdbcDriverProvider} implementations discovered through
 * {@link java.util.ServiceLoader}. Built-in drivers (MariaDB, PostgreSQL, MySQL, H2) are advertised
 * by the shipped {@code BuiltInJdbcDriverProvider}; external drivers (JTOpen, Oracle, etc.) are
 * advertised by descriptor-only providers and resolved against the operator-default drivers
 * directory at runtime — {@code /opt/metricshub/lib/extensions/jdbc/} on Linux,
 * {@code C:\Program Files\MetricsHub\extensions\jdbc\} on Windows.
 *
 * @param driverClass    Fully-qualified {@link java.sql.Driver} implementation class. Required.
 * @param displayName    Human-readable name used in logs and diagnostics.
 * @param origin         Where this descriptor was discovered. {@link DriverOrigin#BUILT_IN} for
 *                       shaded drivers; otherwise filled in by the registry at resolution time.
 * @param driverPackages Java package prefixes that must be loaded child-first by
 *                       {@link IsolatedDriverClassLoader} (vendor code). All other classes are
 *                       loaded parent-first. May be empty for built-ins.
 */
public record JdbcDriverDescriptor(
	String driverClass,
	String displayName,
	DriverOrigin origin,
	List<String> driverPackages
) {
	/**
	 * Canonical constructor. Defensive: makes the package list immutable and null-safe, and
	 * rejects a blank driver class.
	 */
	public JdbcDriverDescriptor {
		Objects.requireNonNull(driverClass, "driverClass");
		if (driverClass.isBlank()) {
			throw new IllegalArgumentException("driverClass must not be blank");
		}
		Objects.requireNonNull(origin, "origin");
		driverPackages = driverPackages == null ? List.of() : List.copyOf(driverPackages);
	}
}
