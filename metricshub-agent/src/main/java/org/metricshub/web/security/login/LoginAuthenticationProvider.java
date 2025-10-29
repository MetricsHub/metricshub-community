package org.metricshub.web.security.login;

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
import java.util.Collections;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication provider for login requests using username and password.
 */
public class LoginAuthenticationProvider {

	private final JwtComponent jwtComponent;
	private final UserService userService;
	private final PasswordEncoder passwordEncoder;

	/**
	 * Constructor to initialize the authentication provider with necessary components.
	 *
	 * @param jwtComponent    JWT handling component
	 * @param userService     User service to retrieve user details
	 * @param passwordEncoder Password encoder to verify user passwords
	 */
	public LoginAuthenticationProvider(
		JwtComponent jwtComponent,
		UserService userService,
		PasswordEncoder passwordEncoder
	) {
		this.jwtComponent = jwtComponent;
		this.userService = userService;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Constructor to initialize the authentication provider with necessary components.
	 *
	 * @param jwtComponent JWT handling component
	 * @param userService  User service to retrieve user details
	 */
	public LoginAuthenticationProvider(JwtComponent jwtComponent, UserService userService) {
		this(jwtComponent, userService, null);
	}

	/**
	 * Authenticate the user based on the provided authentication details.
	 *
	 * @param authentication Authentication request containing login details
	 * @return Authentication instance with user details and JWT
	 */
	public Authentication authenticate(Authentication authentication) {
		// The application supports only LoginAuthenticationRequest
		if (!(authentication.getDetails() instanceof LoginAuthenticationRequest)) {
			throw new UnauthorizedException("Unsupported authentication method.");
		}

		// Perform authentication and get the User instance with the generated JWT
		final var userAndJwt = doAuth((LoginAuthenticationRequest) authentication.getDetails());

		return generateAuthentication(userAndJwt.user(), userAndJwt.jwt());
	}

	/**
	 * Refresh the JWT using the refresh token from the request cookie.
	 *
	 * @param request HTTP request containing the refresh token in a cookie
	 * @return Authentication instance with new JWT and refresh token
	 */
	public Authentication refresh(final HttpServletRequest request) {
		// Perform the refresh strategy and get the User instance with the new JWT
		final var userAndNewJwt = doRefresh(request);

		return generateAuthentication(userAndNewJwt.user(), userAndNewJwt.jwt());
	}

	/**
	 * Generate the Authentication instance wrapping user details, JWT, authority and JWT expiration time
	 *
	 * @param user The authenticated user
	 * @param jwt  The generated JWT
	 * @return     Authentication instance
	 */
	private Authentication generateAuthentication(final User user, final String jwt) {
		// Generate the refresh token
		final String refreshToken = jwtComponent.generateRefreshJwt(user);

		// Create the granted authority as application user
		final GrantedAuthority authority = new SimpleGrantedAuthority(SecurityHelper.ROLE_APP_USER);

		// Return the JWT authentication token wrapping user details, JWT, authority and JWT expiration time
		return new JwtAuthToken(
			user,
			null,
			jwt,
			jwtComponent.getShortExpire(),
			refreshToken,
			jwtComponent.getLongExpire(),
			Collections.singleton(authority)
		);
	}

	/**
	 * Perform the refresh strategy
	 *
	 * @param request HTTP request containing the refresh token in a cookie
	 * @return UserAndJwt instance with user and new JWT
	 */
	private UserAndJwt doRefresh(final HttpServletRequest request) {
		// Get the refresh token from the request cookie
		final String refreshToken = jwtComponent.getRefreshTokenFromRequestCookie(request);
		if (refreshToken == null) {
			throw new UnauthorizedException("Missing refresh token");
		}

		// Get all the claims from the refresh token
		final var claims = jwtComponent.getAllClaimsFromToken(refreshToken);

		// Check that we have a refresh token
		if (!jwtComponent.isRefreshToken(claims)) {
			throw new UnauthorizedException("Invalid token type");
		}

		final var user = findUserByUsername(claims.getSubject());
		final String newJwt = jwtComponent.generateJwt(user);
		return new UserAndJwt(user, newJwt);
	}

	/**
	 * Perform authentication using the given {@link LoginAuthenticationRequest}
	 * having the username and password
	 *
	 * @param request login query containing username and password
	 * @return {@link UserAndJwt} instance
	 */
	private UserAndJwt doAuth(final LoginAuthenticationRequest request) {
		// Get the user and check if submitted password matches the stored password
		final var user = getUserAndCheckPassword(request);

		// We have a user, let's generate a JWT
		final String jwt = jwtComponent.generateJwt(user);

		// Build User and JWT
		return new UserAndJwt(user, jwt);
	}

	/**
	 * Retrieve the user instance and check the password
	 *
	 * @param request login query containing username and password
	 * @return {@link User} instance
	 */
	User getUserAndCheckPassword(final LoginAuthenticationRequest request) {
		// Find the user by username
		final var user = findUserByUsername(request.getUsername());

		// Verify the encoded user password obtained from storage matches the submitted raw password
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new UnauthorizedException("Bad password.");
		}

		return user;
	}

	/**
	 * Find the user by username or throw an exception if not found
	 *
	 * @param username The username to search for
	 * @return The found {@link User} instance
	 */
	User findUserByUsername(final String username) {
		final var user = userService.find(username);
		if (user == null) {
			throw new UnauthorizedException("User not found.");
		}
		return user;
	}

	/**
	 * Helper record to wrap user and JWT
	 */
	private record UserAndJwt(User user, String jwt) {}
}
