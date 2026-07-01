package org.metricshub.engine.connector.model.identity;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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
 * Enum representing the types of connections for a connector.
 */
public enum ConnectionType {
	/**
	 * Remote connection
	 */
	REMOTE,
	/**
	 * Local connection
	 */
	LOCAL;

	/**
	 * Detects {@link ConnectionType} using the value defined in the connector code.
	 *
	 * @param value The value to detect.
	 * @return {@link ConnectionType} instance.
	 * @throws IllegalArgumentException If the provided value is not a supported connection type.
	 */
	public static ConnectionType detect(final String value) {
		// Null returns null
		if (value == null) {
			return null;
		}

		try {
			return ConnectionType.valueOf(value.toUpperCase());
		} catch (Exception e) {
			// No match => Exception
			throw new IllegalArgumentException(
				"'" +
					value +
					"' is not a supported connection type." +
					"ConnectionType must be a known connection type (local, remote)."
			);
		}
	}
}
