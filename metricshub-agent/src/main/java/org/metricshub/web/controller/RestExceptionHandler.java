package org.metricshub.web.controller;

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
			ErrorResponse
				.builder()
				.httpStatus(HttpStatus.UNAUTHORIZED)
				.message(exception.getMessage())
				.build(),
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
			ErrorResponse
				.builder()
				.httpStatus(HttpStatus.FORBIDDEN)
				.message(exception.getMessage())
				.build(),
			HttpStatus.FORBIDDEN
		);
	}
}