package org.metricshub.web.service;

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

import java.util.Optional;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.security.login.LoginAuthenticationProvider;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private JwtComponent jwtComponent;
	private PasswordEncoder passwordEncoder;

	@Autowired
	public UserService(final JwtComponent jwtComponent, final PasswordEncoder passwordEncoder) {
		this.jwtComponent = jwtComponent;
		this.passwordEncoder = passwordEncoder;
	}

	public static final String DEFAULT_USERNAME = "admin";

	public static final User DEFAULT_USER = User
		.builder()
		.username(DEFAULT_USERNAME)
		.password("$2a$10$4dj5/Yw6N1Y1iZ4SY0DTHecPpUrACeK1nMWOXSTFg.21Xj7Z5Lgru")
		.build();

	/**
	 * Find user by username
	 *
	 * @param username The user name
	 * @return {@link Optional} of user instance
	 */
	public Optional<User> find(final String username) {
		if (username.equals(DEFAULT_USERNAME)) {
			return Optional.of(DEFAULT_USER.copy());
		}
		return Optional.empty();
	}

	/**
	 * Perform security and authenticate the user with the given
	 * authenticationRequest and generate {@link JwtAuthToken} instance
	 *
	 * @param authenticationRequest Login request defining username and password
	 * @return {@link JwtAuthToken} instance
	 */
	public JwtAuthToken performSecurity(LoginAuthenticationRequest authenticationRequest) {
		final var usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
			authenticationRequest.getUsername(),
			authenticationRequest.getPassword()
		);

		return setDetailsAndAuthenticate(authenticationRequest, usernamePasswordAuthenticationToken);
	}

	/**
	 * Set the given {@link LoginAuthenticationRequest} in the spring security
	 * {@link UsernamePasswordAuthenticationToken} then authenticate
	 *
	 * @param request                             Login request defining username and password
	 * @param usernamePasswordAuthenticationToken Instance designed for simple presentation of a username and password
	 * @return The {@link JwtAuthToken} returned after calling
	 *         <code>LoginAuthenticationProvider.authenticate</code>
	 */
	private JwtAuthToken setDetailsAndAuthenticate(
		LoginAuthenticationRequest request,
		final UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken
	) {
		// Set the request in the instance's details
		usernamePasswordAuthenticationToken.setDetails(request);

		// Create a new login authentication provider
		final var loginAuthenticationProvider = new LoginAuthenticationProvider(jwtComponent, this, passwordEncoder);

		// Perform the authentication
		return (JwtAuthToken) loginAuthenticationProvider.authenticate(usernamePasswordAuthenticationToken);
	}
}
