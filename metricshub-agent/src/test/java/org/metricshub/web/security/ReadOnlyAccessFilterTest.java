package org.metricshub.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ReadOnlyAccessFilterTest {

	private ReadOnlyAccessFilter filter;

	@BeforeEach
	void setUp() {
		filter = new ReadOnlyAccessFilter();
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void testAllowsWhenUnauthenticated() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/config");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		filter.doFilterInternal(request, response, chain);

		verify(chain, times(1)).doFilter(request, response);
	}

	@Test
	void testAllowsGetForReadOnlyUser() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/config");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		setAuthenticatedUser("ro");

		filter.doFilterInternal(request, response, chain);

		verify(chain, times(1)).doFilter(request, response);
	}

	@Test
	void testDeniesPostForReadOnlyUserOnNonAuthEndpoint() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/config");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		setAuthenticatedUser("ro");

		filter.doFilterInternal(request, response, chain);

		verify(chain, never()).doFilter(request, response);
		assertEquals(403, response.getStatus(), "Read-only user should receive 403 for non-GET requests");
	}

	@Test
	void testAllowsAuthEndpointsForReadOnlyUser() throws ServletException, IOException {
		final FilterChain chain = mock(FilterChain.class);

		setAuthenticatedUser("ro");

		final MockHttpServletRequest authRequest = new MockHttpServletRequest("POST", "/auth");
		final MockHttpServletResponse authResponse = new MockHttpServletResponse();
		filter.doFilterInternal(authRequest, authResponse, chain);

		final MockHttpServletRequest refreshRequest = new MockHttpServletRequest("POST", "/auth/refresh");
		final MockHttpServletResponse refreshResponse = new MockHttpServletResponse();
		filter.doFilterInternal(refreshRequest, refreshResponse, chain);

		final MockHttpServletRequest logoutRequest = new MockHttpServletRequest("DELETE", "/auth");
		final MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
		filter.doFilterInternal(logoutRequest, logoutResponse, chain);

		verify(chain, times(3)).doFilter(any(), any());
	}

	@Test
	void testAllowsNonReadOnlyUser() throws ServletException, IOException {
		final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/config");
		final MockHttpServletResponse response = new MockHttpServletResponse();
		final FilterChain chain = mock(FilterChain.class);

		setAuthenticatedUser("rw");

		filter.doFilterInternal(request, response, chain);

		verify(chain, times(1)).doFilter(request, response);
	}

	/**
	 * Set the authenticated user with the given role
	 *
	 * @param role the role of the user
	 */
	private void setAuthenticatedUser(final String role) {
		final User user = new User();
		user.setUsername("john");
		user.setRole(role);
		final var authentication = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
