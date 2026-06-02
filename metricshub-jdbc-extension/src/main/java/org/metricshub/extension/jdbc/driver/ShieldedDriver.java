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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A thin {@link Driver} wrapper that lets a vendor driver loaded by an
 * {@link IsolatedDriverClassLoader} participate in {@link java.sql.DriverManager}.
 *
 * <p>The JDK's {@code DriverManager.getConnection(url)} loop walks every registered driver but
 * only accepts a driver whose class was loaded by the caller's classloader (the well-known
 * {@code isDriverAllowed} guard). A driver loaded from an isolated child loader would normally be
 * rejected — even though it can perfectly well handle the URL.
 *
 * <p>{@code ShieldedDriver} sidesteps this by being a tiny class loaded by the engine classloader.
 * {@code DriverManager}'s check passes, and every method delegates to the real driver instance,
 * which in turn talks to its own classloader internally. Net effect: connections route correctly
 * by URL while the vendor classes stay isolated.
 */
public final class ShieldedDriver implements Driver {

	private final Driver delegate;
	private final JdbcDriverDescriptor descriptor;

	/**
	 * @param delegate   the real {@link Driver} instance, instantiated by an
	 *                   {@link IsolatedDriverClassLoader}.
	 * @param descriptor the descriptor that produced {@code delegate}; kept for diagnostics.
	 */
	public ShieldedDriver(final Driver delegate, final JdbcDriverDescriptor descriptor) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
	}

	/**
	 * @return the descriptor that produced this driver.
	 */
	public JdbcDriverDescriptor descriptor() {
		return descriptor;
	}

	/**
	 * @return the underlying vendor {@link Driver} instance.
	 */
	public Driver delegate() {
		return delegate;
	}

	/**
	 * Delegates to the wrapped vendor driver to open a connection to the given URL.
	 *
	 * @param url  the JDBC URL.
	 * @param info connection properties.
	 * @return the new {@link Connection}, or {@code null} if the vendor driver does not accept the URL.
	 * @throws SQLException propagated from the vendor driver.
	 */
	@Override
	public Connection connect(final String url, final Properties info) throws SQLException {
		return delegate.connect(url, info);
	}

	/**
	 * Delegates to the wrapped vendor driver.
	 *
	 * @param url the JDBC URL to test.
	 * @return {@code true} if the vendor driver claims it can handle {@code url}.
	 * @throws SQLException propagated from the vendor driver.
	 */
	@Override
	public boolean acceptsURL(final String url) throws SQLException {
		return delegate.acceptsURL(url);
	}

	/**
	 * Delegates to the wrapped vendor driver.
	 *
	 * @param url  the JDBC URL.
	 * @param info connection properties (may be {@code null}).
	 * @return the array of property descriptors required by the vendor driver.
	 * @throws SQLException propagated from the vendor driver.
	 */
	@Override
	public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
		return delegate.getPropertyInfo(url, info);
	}

	/**
	 * @return the wrapped vendor driver's major version.
	 */
	@Override
	public int getMajorVersion() {
		return delegate.getMajorVersion();
	}

	/**
	 * @return the wrapped vendor driver's minor version.
	 */
	@Override
	public int getMinorVersion() {
		return delegate.getMinorVersion();
	}

	/**
	 * @return {@code true} when the wrapped vendor driver reports JDBC compliance.
	 */
	@Override
	public boolean jdbcCompliant() {
		return delegate.jdbcCompliant();
	}

	/**
	 * @return the wrapped vendor driver's parent logger.
	 * @throws SQLFeatureNotSupportedException propagated from the vendor driver.
	 */
	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return delegate.getParentLogger();
	}
}
