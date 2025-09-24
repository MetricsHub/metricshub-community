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

import java.util.Collections;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication provider for login requests using username and password.
 */
public class LoginAuthenticationProvider extends DaoAuthenticationProvider {

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
		super();
		this.jwtComponent = jwtComponent;
		this.userService = userService;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		// The application supports only LoginAuthenticationRequest
		if (!(authentication.getDetails() instanceof LoginAuthenticationRequest)) {
			throw new UnauthorizedException("Unsuported authentication method.");
		}

		// Perform authentication and get the User instance with the generated JWT
		final var userAndJwt = doAuth((LoginAuthenticationRequest) authentication.getDetails());

		final User user = userAndJwt.user();
		final String jwt = userAndJwt.jwt();

		// Create the granted authority as application user
		final GrantedAuthority authority = new SimpleGrantedAuthority(SecurityHelper.ROLE_APP_USER);

		// Return the JWT authentication token wrapping user details, JWT, authority and JWT expiration time
		return new JwtAuthToken(user, null, jwt, Collections.singleton(authority), jwtComponent.getShortExpire());
	}

	@Override
	public boolean supports(final Class<?> authentication) {
		return true;
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
		// Find user by username
		final var user = userService.find(request.getUsername());

		// Are we able to find the user?
		if (user == null) {
			throw new UnauthorizedException("User not found.");
		}

		// Verify the encoded user password obtained from storage matches the submitted raw password
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new UnauthorizedException("Bad password.");
		}

		return user;
	}

	/**
	 * Helper record to wrap user and JWT
	 */
	private record UserAndJwt(User user, String jwt) {}
}
