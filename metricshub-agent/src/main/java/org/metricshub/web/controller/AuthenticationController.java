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

import jakarta.servlet.http.HttpServletRequest;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.metricshub.web.security.login.LoginAuthenticationResponse;
import org.metricshub.web.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to handle authentication requests such as login and logout.
 */
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthenticationController {

	private UserService userService;

	@Autowired
	public AuthenticationController(final UserService userService) {
		this.userService = userService;
	}

	/**
	 * Login endpoint that authenticates a user and returns a JWT token in a cookie.
	 * @param loginAuthenticationRequest the login request containing user credentials
	 * @return a ResponseEntity containing the JWT tokens and setting cookies on success
	 */
	@PostMapping
	public ResponseEntity<LoginAuthenticationResponse> login(
		@RequestBody final LoginAuthenticationRequest loginAuthenticationRequest
	) {
		// Perform the security
		final JwtAuthToken authentication = userService.performSecurity(loginAuthenticationRequest);

		return buildAuthResponse(authentication);
	}

	/**
	 * Builds the authentication response with JWT token, refresh token, and sets them in cookies.
	 *
	 * @param authentication the JwtAuthToken containing the token, refresh token, and expiration info
	 * @return a ResponseEntity containing the JWT tokens and setting cookies on success
	 */
	private ResponseEntity<LoginAuthenticationResponse> buildAuthResponse(final JwtAuthToken authentication) {
		// Inject into security context
		SecurityContextHolder.getContext().setAuthentication(authentication);

		// Build the response
		final LoginAuthenticationResponse response = LoginAuthenticationResponse
			.builder()
			.token(authentication.getToken())
			.build();

		// Build the cookie
		final ResponseCookie cookie = ResponseCookie
			.from(SecurityHelper.TOKEN_KEY, authentication.getToken())
			.path("/")
			.httpOnly(true)
			.maxAge(authentication.getExpiresIn())
			.build();

		// Build the refresh cookie
		final ResponseCookie refreshCookie = ResponseCookie
			.from(SecurityHelper.REFRESH_TOKEN_KEY, authentication.getRefreshToken())
			.path("/")
			.httpOnly(true)
			.maxAge(authentication.getRefreshExpiresIn())
			.build();

		return ResponseEntity
			.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(response);
	}

	/**
	 * Logout endpoint that clears the authentication and removes the JWT cookie.
	 *
	 * @return a ResponseEntity indicating successful logout
	 */
	@DeleteMapping
	public ResponseEntity<Void> logout() {
		// Remove authentication details from the current security context
		SecurityContextHolder.getContext().setAuthentication(null);

		// Remove the cookie
		final ResponseCookie cookie = ResponseCookie
			.from(SecurityHelper.TOKEN_KEY, MetricsHubConstants.EMPTY)
			.path("/")
			.httpOnly(true)
			.maxAge(0)
			.build();

		// Remove the refresh cookie
		final ResponseCookie refreshCookie = ResponseCookie
			.from(SecurityHelper.REFRESH_TOKEN_KEY, MetricsHubConstants.EMPTY)
			.path("/")
			.httpOnly(true)
			.maxAge(0)
			.build();

		return ResponseEntity
			.ok()
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.build();
	}

	/**
	 * Refresh endpoint that refreshes the JWT token using the refresh token from the request
	 *
	 * @param request the HttpServletRequest containing the refresh token cookie
	 * @return a ResponseEntity containing the new JWT tokens and setting cookies on success
	 */
	@PostMapping("/refresh")
	public ResponseEntity<LoginAuthenticationResponse> refresh(final HttpServletRequest request) {
		// Perform the security
		final JwtAuthToken authentication = userService.refreshSecurity(request);

		return buildAuthResponse(authentication);
	}
}
