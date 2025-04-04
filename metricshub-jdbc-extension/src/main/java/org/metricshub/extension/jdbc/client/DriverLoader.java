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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Singleton class to load JDBC drivers only once and in a thread-safe manner.
 */
public class DriverLoader {

	/**
	 * List of loaded JDBC drivers.
	 */
	private static final List<String> LOADED_DRIVERS = Collections.synchronizedList(new ArrayList<>());

	/**
	 * SingletonHelper class to implement the Singleton pattern.
	 */
	private static class SingletonHelper {

		private static final DriverLoader INSTANCE = new DriverLoader();
	}

	/**
	 * Private constructor to implement the Singleton pattern.
	 */
	private DriverLoader() {}

	/**
	 * Returns the Singleton instance of DriverLoader.
	 *
	 * @return DriverLoader instance
	 */
	public static DriverLoader getInstance() {
		return SingletonHelper.INSTANCE;
	}

	/**
	 * Retrieves a list of loaded JDBC drivers.
	 * @return a list of fully qualified names of the loaded JDBC drivers.
	 */
	public static List<String> getLoadedDrivers() {
		return LOADED_DRIVERS;
	}

	/**
	 * Loads a JDBC driver if it hasn't been loaded yet.
	 *
	 * @param driverClassName The fully qualified name of the driver class
	 * @param disableLogs     Specifies whether to disable logging for the driver (true to disable, false to enable).
	 * @throws ClassNotFoundException If the driver class cannot be found
	 */
	public synchronized void loadDriver(final String driverClassName, final boolean disableLogs)
		throws ClassNotFoundException {
		if (!LOADED_DRIVERS.contains(driverClassName)) {
			if (disableLogs) {
				DatabaseLogUtils.disableLogging(driverClassName);
			}

			Class.forName(driverClassName);
			LOADED_DRIVERS.add(driverClassName);
		}
	}

	/**
	 * Loads the appropriate driver for a given JDBC URL using
	 * DriverLoader.
	 *
	 * @param url The JDBC URL for which to load the driver
	 * @throws SQLException If the driver cannot be found or loaded
	 */
	public static void loadDriverForUrl(final String url) throws SQLException {
		String driverClass = null;

		// Determine which driver to load based on the URL
		if (url.startsWith("jdbc:jtds:")) {
			driverClass = "net.sourceforge.jtds.jdbc.Driver";
		} else if (url.startsWith("jdbc:sqlserver:")) {
			driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		} else if (url.startsWith("jdbc:mysql:")) {
			driverClass = "com.mysql.cj.jdbc.Driver";
		} else if (url.startsWith("jdbc:oracle:thin:")) {
			driverClass = "oracle.jdbc.driver.OracleDriver";
		} else if (url.startsWith("jdbc:postgresql:")) {
			driverClass = "org.postgresql.Driver";
		} else if (url.startsWith("jdbc:informix-sqli:") || url.startsWith("jdbc:informix-direct:")) {
			driverClass = "com.informix.jdbc.IfxDriver";
		} else if (url.startsWith("jdbc:derby:")) {
			driverClass = "org.apache.derby.jdbc.EmbeddedDriver";
		} else if (url.startsWith("jdbc:h2:")) {
			driverClass = "org.h2.Driver";
		}

		if (driverClass == null) {
			throw new SQLException("No suitable driver found for the provided JDBC URL: " + url);
		}

		try {
			DriverLoader.getInstance().loadDriver(driverClass, true);
		} catch (Exception e) {
			throw new SQLException("Unable to load JDBC driver for URL: " + url, e);
		}
	}
}
