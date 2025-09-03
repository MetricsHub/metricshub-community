package org.metricshub.web.security.jwt;

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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.metricshub.web.dto.ErrorResponse;
import org.metricshub.web.security.SecurityConstants;
import org.metricshub.web.security.User;
import org.metricshub.web.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that processes incoming HTTP requests to authenticate users based on JWT tokens.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Pattern AUTH_PATH_PATTERN = Pattern.compile("^/auth$");
	private static final String APPLICATION_JSON = "application/json";

	private JwtComponent jwtComponent;
	private UserService userService;
	private ObjectMapper objectMapper;

	/**
	 * Constructor to initialize the filter with necessary components.
	 *
	 * @param jwtComponent JWT handling component
	 * @param userService  User service to retrieve user details
	 * @param objectMapper Object mapper for JSON serialization
	 */
	public JwtAuthenticationFilter(JwtComponent jwtComponent, UserService userService, ObjectMapper objectMapper) {
		this.jwtComponent = jwtComponent;
		this.userService = userService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

	    // If already authenticated (e.g., by API key), don't override
	    if (SecurityContextHolder.getContext().getAuthentication() != null) {
	        filterChain.doFilter(request, response);
	        return;
	    }

		try {
			doApiFilter(request);
		} catch (final Exception ex) {
			writeErrorResponse(response, HttpStatus.UNAUTHORIZED, ex);
			return;
		}

		filterChain.doFilter(request, response);

	}

	/**
	 * Write error response in the given {@link HttpServletResponse}
	 * 
	 * @param response   HTTP response
	 * @param httpStatus {@link HttpStatus} to set in the response
	 * @param ex         Exception to get the message from
	 * @throws IOException can be thrown by {@link HttpServletResponse} or the {@link ObjectMapper}
	 */
	private void writeErrorResponse(final HttpServletResponse response, final HttpStatus httpStatus, final Exception ex)
			throws IOException {
		final var errorResponse = ErrorResponse
			.builder()
			.message(ex.getMessage())
			.httpStatus(httpStatus)
			.build();
		response.getWriter().append(objectMapper.writeValueAsString(errorResponse));
		response.setStatus(httpStatus.value());
		response.setContentType(APPLICATION_JSON);
	}

	/**
	 * Get the authentication token, retrieve all the claims, check that user exists
	 * and finally set the {@link JwtAuthenticationToken} info in the security context holder
	 * 
	 * @param request HTTP request
	 */
	private void doApiFilter(final HttpServletRequest request) {

		// Skip filtering for POST /auth (login)
		if (AUTH_PATH_PATTERN.matcher(request.getRequestURI()).matches() && "post".equalsIgnoreCase(request.getMethod())) {
			return;
		}

		// Get the authentication token
		final String authToken = jwtComponent.getTokenFromRequestCookie(request);

		// No token no filtering
		if (authToken != null) {

			// Get all the claims
			final var claims = jwtComponent.getAllClaimsFromToken(authToken);

			// Create the granted authority
			final GrantedAuthority grantedAuthority = new SimpleGrantedAuthority(SecurityConstants.ROLE_APP_USER);

			// Build the granted authorities singleton as for now we manage the application user
			final List<GrantedAuthority> authorities = Collections.singletonList(grantedAuthority);

			// Then instantiate the authentication
			final var authentication = new JwtAuthenticationToken(
				getUser(claims.getSubject()),
				null,
				authToken,
				authorities,
				jwtComponent.getShortExpire()
			);

			// Set the authentication in the security context, the user can be retrieved easily later in the service or the controller.
			// This SecurityContext is built using a thread local holder.
			// So, this JwtAuthenticationToken will only available during the time of serving an HTTP Servlet request (The lifetime of the HttpServletRequest).
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

	}

	/**
	 * Get user with username
	 * 
	 * @param username user name
	 * @return {@link User} instance
	 */
	private User getUser(final String username) {
		final Optional<User> userOptional = userService.find(username);
		if (userOptional.isEmpty()) {
			throw new AccessDeniedException("User not found");
		}
		return userOptional.get();
	}

}

