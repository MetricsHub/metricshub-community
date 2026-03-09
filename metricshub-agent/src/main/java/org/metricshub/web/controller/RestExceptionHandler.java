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

import java.util.HashMap;
import java.util.Map;
import org.metricshub.web.dto.ErrorResponse;
import org.metricshub.web.exception.ConfigFilesException;
import org.metricshub.web.exception.LogFilesException;
import org.metricshub.web.exception.TextPlainException;
import org.metricshub.web.exception.UnauthorizedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for REST controllers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler {

	/**
	 * Handle UnauthorizedException exceptions.
	 *
	 * @param <T>       the type of the exception
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
	 *
	 * @param <T>       the type of the exception
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

	/**
	 * Handle ConfigFilesException exceptions.
	 *
	 * @param ex the exception to handle
	 * @return a ResponseEntity containing the error response
	 */
	@ExceptionHandler(ConfigFilesException.class)
	protected ResponseEntity<Object> handleConfigFilesException(final ConfigFilesException ex) {
		HttpStatus status;
		switch (ex.getCode()) {
			case CONFIG_DIR_UNAVAILABLE:
				status = HttpStatus.SERVICE_UNAVAILABLE;
				break;
			case FILE_NOT_FOUND:
				status = HttpStatus.NOT_FOUND;
				break;
			case INVALID_FILE_NAME, INVALID_EXTENSION, INVALID_PATH, VALIDATION_FAILED:
				status = HttpStatus.BAD_REQUEST;
				break;
			case TARGET_EXISTS:
				status = HttpStatus.CONFLICT;
				break;
			case IO_FAILURE:
			default:
				status = HttpStatus.INTERNAL_SERVER_ERROR;
				break;
		}

		final String message = (ex.getMessage() != null && !ex.getMessage().isEmpty())
			? ex.getMessage()
			: ex.getCode().name();

		return new ResponseEntity<>(ErrorResponse.builder().httpStatus(status).message(message).build(), status);
	}

	/**
	 * Handle LogFilesException exceptions.
	 *
	 * @param ex the exception to handle
	 * @return a ResponseEntity containing the error response
	 */
	@ExceptionHandler(LogFilesException.class)
	protected ResponseEntity<Object> handleLogFilesException(final LogFilesException ex) {
		HttpStatus status;
		switch (ex.getCode()) {
			case LOGS_DIR_UNAVAILABLE:
				status = HttpStatus.SERVICE_UNAVAILABLE;
				break;
			case FILE_NOT_FOUND:
				status = HttpStatus.NOT_FOUND;
				break;
			case INVALID_FILE_NAME, INVALID_PATH:
				status = HttpStatus.BAD_REQUEST;
				break;
			case IO_FAILURE:
			default:
				status = HttpStatus.INTERNAL_SERVER_ERROR;
				break;
		}

		final String message = (ex.getMessage() != null && !ex.getMessage().isEmpty())
			? ex.getMessage()
			: ex.getCode().name();

		return new ResponseEntity<>(ErrorResponse.builder().httpStatus(status).message(message).build(), status);
	}

	/**
	 * Handle BindException exceptions.
	 *
	 * @param ex the exception to handle
	 * @return a ResponseEntity containing the validation errors
	 */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<Map<String, String>> handleValidationExceptions(final BindException ex) {
		final Map<String, String> errors = ex
			.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error ->
				Map.entry(error.getField(), error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value")
			)
			.collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
		return ResponseEntity.badRequest().body(errors);
	}

	/**
	 * Handle {@link TextPlainException} exceptions.
	 * <p>
	 * Returns the error message as {@code text/plain} so that endpoints
	 * producing plain text get a consistent content type even on errors.
	 *
	 * @param ex the exception to handle
	 * @return a ResponseEntity containing the plain-text error message
	 */
	@ExceptionHandler(TextPlainException.class)
	public ResponseEntity<String> handleTextPlainException(final TextPlainException ex) {
		return ResponseEntity.status(ex.getStatus()).contentType(MediaType.TEXT_PLAIN).body(ex.getMessage());
	}
}
