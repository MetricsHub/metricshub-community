package org.sentrysoftware.metricshub.engine.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
 * Enum representing transport protocols for communication.
 * Supports HTTP and HTTPS protocols.
 */
public enum TransportProtocols {
	/**
	 * HTTP transport protocol.
	 */
	HTTP,
	/**
	 * HTTPS transport protocol.
	 */
	HTTPS;

	/**
	 * Interpret the specified name and returns corresponding value.
	 *
	 * @param label String to be interpreted
	 * @return Corresponding {@link TransportProtocols} value
	 */
	public static TransportProtocols interpretValueOf(final String label) {
		if ("http".equalsIgnoreCase(label)) {
			return HTTP;
		}

		if ("https".equalsIgnoreCase(label)) {
			return HTTPS;
		}

		throw new IllegalArgumentException("Invalid protocol value: " + label);
	}

	@Override
	public String toString() {
		if (this == HTTP) {
			return "http";
		}
		return "https";
	}
}
