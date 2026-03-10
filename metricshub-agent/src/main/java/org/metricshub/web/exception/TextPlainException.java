package org.metricshub.web.exception;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import org.springframework.http.HttpStatus;

/**
 * Runtime exception for endpoints that produce {@code text/plain} responses.
 * <p>
 * When thrown, the {@link org.metricshub.web.controller.RestExceptionHandler}
 * renders the error as a plain-text body instead of the default JSON
 * {@link org.metricshub.web.dto.ErrorResponse}.
 */
public class TextPlainException extends RuntimeException {

	/**
	 * Serial version UID for serialization.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * HTTP status to return in the response.
	 */
	private final HttpStatus status;

	/**
	 * Constructor for TextPlainException.
	 *
	 * @param status  the HTTP status code for the error response
	 * @param message the plain-text error message
	 */
	public TextPlainException(final HttpStatus status, final String message) {
		super(message);
		this.status = status;
	}

	/**
	 * Constructor for TextPlainException with a cause.
	 *
	 * @param status  the HTTP status code for the error response
	 * @param message the plain-text error message
	 * @param cause   the underlying cause of the exception
	 */
	public TextPlainException(final HttpStatus status, final String message, final Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	/**
	 * Gets the HTTP status associated with this exception.
	 *
	 * @return the HTTP status
	 */
	public HttpStatus getStatus() {
		return status;
	}
}
