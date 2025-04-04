package org.metricshub.engine.common.exception;

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
 * Exception thrown to indicate a failure in deserialization.
 */
public class DeserializationException extends RuntimeException {

	private static final long serialVersionUID = -5293423254348627190L;

	/**
	 * Constructs a new {@code DeserializationException} with the specified detail message and cause.
	 *
	 * @param message The detail message.
	 * @param cause   The cause (which is saved for later retrieval by the {@link #getCause()} method).
	 */
	public DeserializationException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new {@code DeserializationException} with the specified detail message.
	 *
	 * @param message The detail message.
	 */
	public DeserializationException(final String message) {
		super(message);
	}
}
