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
import java.util.List;
import lombok.NoArgsConstructor;

/**
 * Descriptor-only {@link JdbcDriverProvider} for JDBC drivers MetricsHub supports but does
 * <em>not</em> ship.
 *
 * <p>Each descriptor declares its driver class, URL prefixes, default port and the vendor packages
 * that must be isolated. The actual driver JAR is expected at
 * {@code <INSTALL_DIR>/extensions/jdbc/} (or {@code .../jdbc/<variant>/}) and will be loaded
 * lazily by {@link JdbcDriverRegistry} through an {@link IsolatedDriverClassLoader}.
 *
 * <p>Origin is {@link DriverOrigin#USER_DEFAULT} because, from the registry's point of view, every
 * descriptor advertised here ultimately requires a user-supplied JAR.
 */
@NoArgsConstructor
public final class ExternalJdbcDriverDescriptorsProvider implements JdbcDriverProvider {

	@Override
	public Collection<JdbcDriverDescriptor> provide() {
		return List.of(
			new JdbcDriverDescriptor(
				"oracle.jdbc.OracleDriver",
				"Oracle JDBC Driver",
				List.of("jdbc:oracle:thin:", "jdbc:oracle:oci:"),
				1521,
				DriverOrigin.USER_DEFAULT,
				List.of("oracle."),
				"SELECT 1 FROM DUAL"
			),
			new JdbcDriverDescriptor(
				"com.microsoft.sqlserver.jdbc.SQLServerDriver",
				"Microsoft SQL Server JDBC Driver",
				List.of("jdbc:sqlserver:"),
				1433,
				DriverOrigin.USER_DEFAULT,
				List.of("com.microsoft.sqlserver."),
				"SELECT 1"
			),
			new JdbcDriverDescriptor(
				"net.sourceforge.jtds.jdbc.Driver",
				"jTDS JDBC Driver",
				List.of("jdbc:jtds:"),
				1433,
				DriverOrigin.USER_DEFAULT,
				List.of("net.sourceforge.jtds."),
				"SELECT 1"
			),
			new JdbcDriverDescriptor(
				"com.informix.jdbc.IfxDriver",
				"IBM Informix JDBC Driver",
				List.of("jdbc:informix-sqli:", "jdbc:informix-direct:"),
				1526,
				DriverOrigin.USER_DEFAULT,
				List.of("com.informix."),
				"SELECT 1 FROM systables WHERE tabid = 1"
			),
			new JdbcDriverDescriptor(
				"org.apache.derby.jdbc.EmbeddedDriver",
				"Apache Derby Embedded Driver",
				List.of("jdbc:derby:"),
				1527,
				DriverOrigin.USER_DEFAULT,
				List.of("org.apache.derby."),
				"VALUES 1"
			),
			new JdbcDriverDescriptor(
				"com.ibm.as400.access.AS400JDBCDriver",
				"IBM Toolbox for Java (JTOpen) JDBC Driver",
				List.of("jdbc:as400:"),
				8471,
				DriverOrigin.USER_DEFAULT,
				List.of("com.ibm.as400."),
				"VALUES 1"
			),
			new JdbcDriverDescriptor(
				"com.ibm.db2.jcc.DB2Driver",
				"IBM Db2 JDBC Driver",
				List.of("jdbc:db2:"),
				50000,
				DriverOrigin.USER_DEFAULT,
				List.of("com.ibm.db2."),
				"VALUES 1"
			)
		);
	}
}
