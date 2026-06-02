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

/**
 * Identifies where a {@link JdbcDriverDescriptor} or a resolved driver JAR was discovered.
 *
 * <p>The origin is used both for logging (so operators can see which path a driver came from) and
 * for precedence resolution when multiple sources advertise the same driver class.
 */
public enum DriverOrigin {
	/**
	 * Driver is shipped with the agent, shaded inside {@code metricshub-jdbc-extension.jar}.
	 */
	BUILT_IN,

	/**
	 * Driver JAR was discovered by scanning the operator-default drivers directory (typically
	 * {@code <INSTALL_DIR>/lib/extensions/jdbc/}) for any JAR exposing the requested
	 * {@code driverClass}. Used when {@code JdbcInfo.driverPath} is null.
	 */
	USER_DEFAULT,

	/**
	 * Driver JAR was located via an explicit {@code JdbcInfo.driverPath} expression (resolved to
	 * an absolute file path or a single glob match).
	 */
	USER_EXPLICIT,

	/**
	 * Driver JAR was discovered alongside a community connector YAML (sibling {@code drivers/} folder).
	 * Never applies to enterprise (serialized) connectors.
	 */
	CONNECTOR_SIBLING,

	/**
	 * Driver JAR was discovered through the {@code additionalDriverJars} escape hatch in {@code metricshub.yaml}.
	 */
	ADDITIONAL
}
