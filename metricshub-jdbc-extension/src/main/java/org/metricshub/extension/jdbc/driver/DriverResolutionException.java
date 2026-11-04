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

/**
 * Thrown when {@link JdbcDriverRegistry} cannot locate, load, or instantiate a JDBC driver.
 *
 * <p>The message is intended to be operator-facing and should clearly name the driver class and, when applicable,

 * the expected JAR path or directory involved in resolution.

 */
public class DriverResolutionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param message operator-facing diagnostic message.
	 */
	public DriverResolutionException(final String message) {
		super(message);
	}

	/**
	 * @param message operator-facing diagnostic message.
	 * @param cause   the underlying cause (e.g. {@link ClassNotFoundException}).
	 */
	public DriverResolutionException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
