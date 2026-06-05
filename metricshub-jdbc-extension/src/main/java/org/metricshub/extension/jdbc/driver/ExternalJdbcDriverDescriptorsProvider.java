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
 * Descriptor-only {@link IJdbcDriverProvider} for JDBC drivers MetricsHub supports but does
 * <em>not</em> ship.
 *
 * <p>Each descriptor declares its driver class and the vendor packages that must be isolated.
 * The actual driver JAR is expected in the operator-default drivers directory and will be loaded
 * lazily by {@link JdbcDriverRegistry} through an {@link IsolatedDriverClassLoader}:
 * <ul>
 *   <li>Linux:   {@code /opt/metricshub/lib/extensions/jdbc/}</li>
 *   <li>Windows: {@code C:\Program Files\MetricsHub\extensions\jdbc\}</li>
 * </ul>
 *
 * <p>Origin is {@link DriverOrigin#USER_DEFAULT} because, from the registry's point of view, every
 * descriptor advertised here ultimately requires a user-supplied JAR.
 */
@NoArgsConstructor
public final class ExternalJdbcDriverDescriptorsProvider implements IJdbcDriverProvider {

	@Override
	public Collection<JdbcDriverDescriptor> provide() {
		return List.of(
			new JdbcDriverDescriptor(
				"oracle.jdbc.OracleDriver",
				"Oracle JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("oracle.")
			),
			new JdbcDriverDescriptor(
				"com.microsoft.sqlserver.jdbc.SQLServerDriver",
				"Microsoft SQL Server JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("com.microsoft.sqlserver.")
			),
			new JdbcDriverDescriptor(
				"net.sourceforge.jtds.jdbc.Driver",
				"jTDS JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("net.sourceforge.jtds.")
			),
			new JdbcDriverDescriptor(
				"com.informix.jdbc.IfxDriver",
				"IBM Informix JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("com.informix.")
			),
			new JdbcDriverDescriptor(
				"org.apache.derby.jdbc.EmbeddedDriver",
				"Apache Derby Embedded Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("org.apache.derby.")
			),
			new JdbcDriverDescriptor(
				"com.ibm.as400.access.AS400JDBCDriver",
				"IBM Toolbox for Java (JTOpen) JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("com.ibm.as400.")
			),
			new JdbcDriverDescriptor(
				"com.ibm.db2.jcc.DB2Driver",
				"IBM Db2 JDBC Driver",
				DriverOrigin.USER_DEFAULT,
				List.of("com.ibm.db2.")
			)
		);
	}
}
