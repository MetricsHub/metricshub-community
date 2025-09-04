package org.metricshub.web.exception;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import org.springframework.security.core.AuthenticationException;

/**
 * Exception used to for unauthorized access (Security)
 */
public class UnauthorizedException extends AuthenticationException {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new UnauthorizedException with the specified detail message.
	 *
	 * @param message the detail message
	 */
	public UnauthorizedException(final String message) {
		super(message);
	}

	/**
	 * Constructs a new UnauthorizedException with the specified detail message and
	 * cause.
	 *
	 * @param message the detail message
	 * @param cause   the cause (which is saved for later retrieval by the
	 *                {@link #getCause()} method). (A <code>null</code> value is
	 *                permitted, and indicates that the cause is nonexistent or
	 *                unknown.)
	 */
	public UnauthorizedException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
