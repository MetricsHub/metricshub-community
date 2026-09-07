package org.metricshub.web.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.ErrorResponse;
import org.metricshub.web.exception.TextPlainException;
import org.metricshub.web.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
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

	@Test
	void testShouldSwallowIoExceptionOnceTheResponseIsCommitted() {
		final RestExceptionHandler handler = new RestExceptionHandler();
		final MockHttpServletResponse response = new MockHttpServletResponse();
		response.setCommitted(true);

		// A client that aborted its SSE stream: nothing can be sent back, and nothing must reach Tomcat's SEVERE log
		assertNull(handler.handleIoException(new IOException("Connection reset by peer"), response));
	}

	@Test
	void testShouldAnswerIoExceptionBeforeTheResponseIsCommitted() {
		final RestExceptionHandler handler = new RestExceptionHandler();

		final ResponseEntity<Object> response = handler.handleIoException(
			new IOException("Disk full"),
			new MockHttpServletResponse()
		);

		assertAll(
			() -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()),
			() -> assertNotNull(response.getBody()),
			() -> assertEquals("Disk full", ((ErrorResponse) response.getBody()).getMessage())
		);
	}

	@Test
	void testShouldHandleTextPlainException() {
		final RestExceptionHandler handler = new RestExceptionHandler();
		final TextPlainException ex = new TextPlainException(HttpStatus.BAD_REQUEST, "Only .vm files can be tested.");

		final ResponseEntity<String> response = handler.handleTextPlainException(ex);

		assertAll(
			() -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "Status code should be 400 Bad Request"),
			() -> assertNotNull(response.getBody(), "Response body should not be null"),
			() ->
				assertTrue(
					response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN),
					"Content type should be text/plain"
				),
			() ->
				assertEquals("Only .vm files can be tested.", response.getBody(), "Message should match the exception message")
		);
	}
}
