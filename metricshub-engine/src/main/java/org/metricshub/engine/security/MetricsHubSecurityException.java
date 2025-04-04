package org.metricshub.engine.security;

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

import lombok.NoArgsConstructor;

/**
 * The MetricsHubSecurityException is an exception class specifically designed for security-related issues
 * within the MetricsHub engine.
 */
@NoArgsConstructor
public class MetricsHubSecurityException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new MetricsHubSecurityException with the specified detail message.
	 *
	 * @param message the detail message (which is saved for later retrieval by the getMessage() method)
	 */
	public MetricsHubSecurityException(String message) {
		super(message);
	}

	/**
	 * Constructs a new MetricsHubSecurityException with the specified cause.
	 *
	 * @param cause the cause (which is saved for later retrieval by the getCause() method)
	 */
	public MetricsHubSecurityException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs a new MetricsHubSecurityException with the specified detail message and cause.
	 *
	 * @param message the detail message (which is saved for later retrieval by the getMessage() method)
	 * @param cause   the cause (which is saved for later retrieval by the getCause() method)
	 */
	public MetricsHubSecurityException(String message, Throwable cause) {
		super(message, cause);
	}
}
