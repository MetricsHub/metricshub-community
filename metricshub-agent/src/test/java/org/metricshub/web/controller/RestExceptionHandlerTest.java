package org.metricshub.web.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.ErrorResponse;
import org.metricshub.web.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class RestExceptionHandlerTest {

	@Test
	void testShouldHandleUnauthorizedException() {
		final RestExceptionHandler handler = new RestExceptionHandler();
		final UnauthorizedException ex = new UnauthorizedException("Unauthorized!");

		final ResponseEntity<Object> response = handler.handleUnauthorizedException(ex);

		assertAll(
			() -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "Status code should be 401 Unauthorized"),
			() -> assertNotNull(response.getBody(), "Response body should not be null"),
			() -> assertTrue(response.getBody() instanceof ErrorResponse, "Response body should be of type ErrorResponse"),
			() ->
				assertEquals(
					HttpStatus.UNAUTHORIZED,
					((ErrorResponse) response.getBody()).getHttpStatus(),
					"HTTP status in body should be 401 Unauthorized"
				),
			() ->
				assertEquals(
					"Unauthorized!",
					((ErrorResponse) response.getBody()).getMessage(),
					"Message should match the exception message"
				)
		);
	}

	@Test
	void testShouldHandleAccessDeniedException() {
		final RestExceptionHandler handler = new RestExceptionHandler();
		final AccessDeniedException ex = new AccessDeniedException("Forbidden!");

		final ResponseEntity<Object> response = handler.handleAccessDeniedException(ex);

		assertAll(
			() -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Status code should be 403 Forbidden"),
			() -> assertNotNull(response.getBody(), "Response body should not be null"),
			() -> assertTrue(response.getBody() instanceof ErrorResponse, "Response body should be of type ErrorResponse"),
			() ->
				assertEquals(
					HttpStatus.FORBIDDEN,
					((ErrorResponse) response.getBody()).getHttpStatus(),
					"HTTP status in body should be 403 Forbidden"
				),
			() ->
				assertEquals(
					"Forbidden!",
					((ErrorResponse) response.getBody()).getMessage(),
					"Message should match the exception message"
				)
		);
	}
}
