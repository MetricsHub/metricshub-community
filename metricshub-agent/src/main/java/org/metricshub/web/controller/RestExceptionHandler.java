package org.metricshub.web.controller;

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

import org.metricshub.web.dto.ErrorResponse;
import org.metricshub.web.exception.UnauthorizedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Middleware handling all the exceptions that can be thrown by the REST Controllers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler {

	/**
	 * Handle UnauthorizedException exceptions.
	 *
	 * @param <T>  the type of the exception
	 * @param exception the exception to handle
	 * @return a ResponseEntity containing the error response
	 */
	@ExceptionHandler({ UnauthorizedException.class })
	protected <T extends UnauthorizedException> ResponseEntity<Object> handleUnauthorizedException(final T exception) {
		return new ResponseEntity<>(
			ErrorResponse.builder().httpStatus(HttpStatus.UNAUTHORIZED).message(exception.getMessage()).build(),
			HttpStatus.UNAUTHORIZED
		);
	}

	/**
	 * Handle AccessDeniedException exceptions.
	 * @param <T> the type of the exception
	 * @param exception the exception to handle
	 * @return a ResponseEntity containing the error response
	 */
	@ExceptionHandler({ AccessDeniedException.class })
	protected <T extends RuntimeException> ResponseEntity<Object> handleAccessDeniedException(final T exception) {
		return new ResponseEntity<>(
			ErrorResponse.builder().httpStatus(HttpStatus.FORBIDDEN).message(exception.getMessage()).build(),
			HttpStatus.FORBIDDEN
		);
	}
}
