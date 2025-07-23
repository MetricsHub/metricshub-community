package org.metricshub.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthFilterTest {

	private ApiKeyRegistry apiKeyRegistry;
	private ApiKeyAuthFilter filter;
	private HttpServletRequest request;
	private HttpServletResponse response;
	private FilterChain filterChain;

	private final String validToken = "valid-token";
	private final String principalName = "principal";

	@BeforeEach
	void setUp() {
		apiKeyRegistry = mock(ApiKeyRegistry.class);
		filter = new ApiKeyAuthFilter(apiKeyRegistry);
		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
		filterChain = mock(FilterChain.class);
		SecurityContextHolder.clearContext();
	}

	@Test
	void testValidApiKey() throws ServletException, IOException {
		when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
		when(apiKeyRegistry.isValid(validToken)).thenReturn(true);
		when(apiKeyRegistry.getPrincipal(validToken)).thenReturn(principalName);

		filter.doFilterInternal(request, response, filterChain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertNotNull(authentication, "Authentication should not be null");
		assertEquals(principalName, authentication.getPrincipal(), "Principal should match");
		assertEquals(validToken, authentication.getCredentials(), "Credentials should match");

		verify(filterChain).doFilter(request, response);
		verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

	@Test
	void testInvalidApiKey() throws ServletException, IOException {
		when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
		when(apiKeyRegistry.isValid("invalid-token")).thenReturn(false);

		StringWriter responseWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(responseWriter);
		when(response.getWriter()).thenReturn(printWriter);

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		assertTrue(responseWriter.toString().contains("Unauthorized"), "Response should indicate unauthorized access");

		verify(filterChain, never()).doFilter(request, response);
		assertNull(
			SecurityContextHolder.getContext().getAuthentication(),
			"Authentication should be null for invalid API key"
		);
	}

	@Test
	void testMissingAuthorizationHeader() throws ServletException, IOException {
		when(request.getHeader("Authorization")).thenReturn(null);

		StringWriter responseWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(responseWriter);
		when(response.getWriter()).thenReturn(printWriter);

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		assertTrue(responseWriter.toString().contains("Unauthorized"), "Response should indicate unauthorized access");
		verify(filterChain, never()).doFilter(request, response);
		assertNull(
			SecurityContextHolder.getContext().getAuthentication(),
			"Authentication should be null when no Authorization header is present"
		);
	}

	@Test
	void testApiKeyWithoutBearerPrefix() throws ServletException, IOException {
		when(request.getHeader("Authorization")).thenReturn(validToken); // No "Bearer " prefix
		when(apiKeyRegistry.isValid(validToken)).thenReturn(false); // Should still fail

		StringWriter responseWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(responseWriter);
		when(response.getWriter()).thenReturn(printWriter);

		filter.doFilterInternal(request, response, filterChain);

		verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		assertTrue(responseWriter.toString().contains("Unauthorized"), "Response should indicate unauthorized access");
		verify(filterChain, never()).doFilter(request, response);
		assertNull(
			SecurityContextHolder.getContext().getAuthentication(),
			"Authentication should be null when API key is not prefixed with 'Bearer '"
		);
	}
}
