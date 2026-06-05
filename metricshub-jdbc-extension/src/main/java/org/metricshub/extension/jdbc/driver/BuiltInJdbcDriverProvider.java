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

import java.util.Collection;
import java.util.List;
import lombok.NoArgsConstructor;

/**
 * {@link IJdbcDriverProvider} that advertises the JDBC drivers shaded into
 * {@code metricshub-jdbc-extension}: MariaDB, PostgreSQL, MySQL and H2.
 *
 * <p>All descriptors use {@link DriverOrigin#BUILT_IN}, which instructs
 * {@link JdbcDriverRegistry} to load them from the extension's own classloader (no isolated
 * classloader and no filesystem lookup) when no variant is requested.
 */
@NoArgsConstructor
public final class BuiltInJdbcDriverProvider implements IJdbcDriverProvider {

	@Override
	public Collection<JdbcDriverDescriptor> provide() {
		return List.of(
			new JdbcDriverDescriptor(
				"org.mariadb.jdbc.Driver",
				"MariaDB Connector/J",
				DriverOrigin.BUILT_IN,
				List.of("org.mariadb.")
			),
			new JdbcDriverDescriptor(
				"org.postgresql.Driver",
				"PostgreSQL JDBC Driver",
				DriverOrigin.BUILT_IN,
				List.of("org.postgresql.")
			),
			new JdbcDriverDescriptor(
				"com.mysql.cj.jdbc.Driver",
				"MySQL Connector/J",
				DriverOrigin.BUILT_IN,
				List.of("com.mysql.")
			),
			new JdbcDriverDescriptor("org.h2.Driver", "H2 Database Engine", DriverOrigin.BUILT_IN, List.of("org.h2."))
		);
	}
}
