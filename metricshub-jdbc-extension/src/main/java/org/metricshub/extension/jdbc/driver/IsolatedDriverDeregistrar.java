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

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * Deregisters from the JVM-global {@link DriverManager} every driver defined by the class loader
 * that loaded <em>this</em> class.
 *
 * <p>{@link DriverManager#getDrivers()} and {@link DriverManager#deregisterDriver(Driver)} only
 * expose drivers whose class is visible from the <em>caller</em> class's loader. A driver that
 * self-registered from an {@link IsolatedDriverClassLoader} is therefore invisible to code loaded
 * by the JDBC extension itself. {@link IsolatedDriverClassLoader#defineDeregistrar()} defines a
 * copy of this class <em>inside</em> the isolated loader, so invoking
 * {@link #deregisterOwnDrivers()} on that copy passes the caller-visibility check for exactly the
 * drivers that loader defined — letting the loader be closed and reclaimed after an extension
 * reload.
 *
 * <p>This class must stay self-contained (JDK types only): the copy defined inside the isolated
 * loader must not drag other extension classes across the loader boundary.
 */
public final class IsolatedDriverDeregistrar {

	private IsolatedDriverDeregistrar() {}

	/**
	 * Deregisters every {@link DriverManager} driver whose class was defined by this class's own
	 * class loader.
	 *
	 * @return the number of drivers deregistered.
	 */
	public static int deregisterOwnDrivers() {
		final ClassLoader self = IsolatedDriverDeregistrar.class.getClassLoader();
		int deregistered = 0;
		final Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			final Driver driver = drivers.nextElement();
			if (driver.getClass().getClassLoader() == self) {
				try {
					DriverManager.deregisterDriver(driver);
					deregistered++;
				} catch (SQLException e) {
					// Best-effort: keep deregistering the remaining drivers.
				}
			}
		}
		return deregistered;
	}
}
