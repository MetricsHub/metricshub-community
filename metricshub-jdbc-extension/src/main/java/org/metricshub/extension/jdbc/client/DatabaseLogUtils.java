package org.metricshub.extension.jdbc.client;

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

import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides utility methods to disable or redirect logging for specific JDBC drivers.
 */
public class DatabaseLogUtils {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private DatabaseLogUtils() {}

	/**
	 * An OutputStream that does nothing to redirect logging to void.
	 */
	public static final OutputStream NOOP_STREAM = new OutputStream() {
		@Override
		public void write(int b) {
			// Do nothing to redirect logging to void
		}
	};

	/**
	 * Disables or redirects logging for specific JDBC drivers.
	 *
	 * @param driverClassName The fully qualified name of the driver class
	 */
	public static void disableLogging(final String driverClassName) {
		if (driverClassName.startsWith("com.microsoft.sqlserver.jdbc.SQLServerDriver")) {
			// MS SQL Server: Disable logging
			Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(Level.OFF);
		} else if (driverClassName.startsWith("org.apache.derby.jdbc.EmbeddedDriver")) {
			// Derby: Redirect logging (derby.log) to void
			System.setProperty("derby.stream.error.field", DatabaseLogUtils.class.getCanonicalName() + ".NOOP_STREAM");
		}
	}
}
