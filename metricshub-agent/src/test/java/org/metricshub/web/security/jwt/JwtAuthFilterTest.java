package org.metricshub.web.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.security.User;
import org.metricshub.web.service.UserService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthFilterTest {

	private JwtComponent jwtComponent;
	private UserService userService;
	private JwtAuthFilter filter;

	@BeforeEach
	void setup() {
		jwtComponent = mock(JwtComponent.class);
		userService = mock(UserService.class);
		filter = new JwtAuthFilter(jwtComponent, userService);
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void testShouldBypassWhenAlreadyAuthenticated() throws ServletException, IOException {
		final Authentication existingAuth = mock(Authentication.class);
		SecurityContextHolder.getContext().setAuthentication(existingAuth);

		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain, times(1)).doFilter(request, response);
		assertSame(
			existingAuth,
			SecurityContextHolder.getContext().getAuthentication(),
			"Existing authentication must be preserved"
		);
	}

	@Test
	void testShouldSkipForLoginPostAuthPath() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		// No token interactions, just passes through
		verify(chain, times(1)).doFilter(request, response);
		assertNull(SecurityContextHolder.getContext().getAuthentication(), "Auth should remain null for /auth POST");
		verifyNoInteractions(jwtComponent);
	}

	@Test
	void testShouldSetAuthenticationFromValidToken() throws ServletException, IOException {
		final String token = "jwt-token";
		final String username = "john";
		final long shortExpire = 1800L;

		// Request/response/chain
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		// Mocks
		final var claims = mock(io.jsonwebtoken.Claims.class);
		when(jwtComponent.getTokenFromRequestCookie(request)).thenReturn(token);
		when(jwtComponent.getAllClaimsFromToken(token)).thenReturn(claims);
		when(claims.getSubject()).thenReturn(username);
		when(jwtComponent.getShortExpire()).thenReturn(shortExpire);

		final User user = new User();
		user.setUsername(username);
		when(userService.find(username)).thenReturn(Optional.of(user));

		// Exercise
		filter.doFilterInternal(request, response, chain);

		// Verify chain continues
		verify(chain, times(1)).doFilter(request, response);

		// Verify auth populated
		final var auth = SecurityContextHolder.getContext().getAuthentication();
		assertNotNull(auth, "Authentication should be set");
		assertTrue(auth instanceof JwtAuthToken, "Authentication should be a JwtAuthToken");
		final JwtAuthToken jwtAuth = (JwtAuthToken) auth;
		assertEquals(token, jwtAuth.getToken(), "Token should match");
		assertEquals(user, jwtAuth.getPrincipal(), "Principal should be the resolved User");
		assertEquals(shortExpire, jwtAuth.getExpiresIn(), "Expiry should match jwtComponent.getShortExpire()");
	}

	@Test
	void testShouldNotAuthenticateWhenNoToken() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		when(jwtComponent.getTokenFromRequestCookie(request)).thenReturn(null);

		filter.doFilterInternal(request, response, chain);

		verify(chain, times(1)).doFilter(request, response);
		assertNull(SecurityContextHolder.getContext().getAuthentication(), "Auth should remain null when no token");
	}

	@Test
	void testShouldWriteUnauthorizedAndStopChainOnUserNotFound() throws ServletException, IOException {
		final String token = "bad-token";
		final String username = "ghost";

		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		final var claims = mock(io.jsonwebtoken.Claims.class);
		when(jwtComponent.getTokenFromRequestCookie(request)).thenReturn(token);
		when(jwtComponent.getAllClaimsFromToken(token)).thenReturn(claims);
		when(claims.getSubject()).thenReturn(username);
		when(userService.find(username)).thenReturn(Optional.empty()); // will throw AccessDeniedException inside

		filter.doFilterInternal(request, response, chain);

		// When an exception occurs, filter writes unauthorized response and RETURNS (does not call chain)
		verify(chain, never()).doFilter(request, response);
		assertNull(SecurityContextHolder.getContext().getAuthentication(), "Auth should remain null on failure");

		assertEquals(
			401,
			response.getStatus(),
			"Response should be unauthorized or untouched depending on SecurityHelper implementation"
		);
	}
}
