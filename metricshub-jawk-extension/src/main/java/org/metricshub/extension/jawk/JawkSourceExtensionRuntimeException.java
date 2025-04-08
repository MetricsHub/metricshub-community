package org.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
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
 * An exception class for handling runtime exceptions in the JawkSoruce extension.
 */
public class JawkSourceExtensionRuntimeException extends RuntimeException {

	/**
	 * Default serial version UID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new {@link JawkSourceExtensionRuntimeException} with the specified message.
	 *
	 * @param message the message
	 */
	public JawkSourceExtensionRuntimeException(final String message) {
		super(message);
	}

	/**
	 * Constructs a new {@link JawkSourceExtensionRuntimeException} with the specified message and
	 * cause.
	 *
	 * @param message the message
	 * @param cause   the cause
	 */
	public JawkSourceExtensionRuntimeException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
