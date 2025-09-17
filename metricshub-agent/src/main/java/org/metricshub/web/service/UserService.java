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

import static org.metricshub.agent.helper.AgentConstants.USER_INFO_SEPARATOR;
import static org.metricshub.agent.helper.AgentConstants.USER_PREFIX;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.SecurityManager;
import org.metricshub.web.dto.UserDto;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.security.login.LoginAuthenticationProvider;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service class for managing user authentication and retrieval.
 */
@Service
@Slf4j
public class UserService {

	private JwtComponent jwtComponent;
	private PasswordEncoder passwordEncoder;

	/**
	 * Constructor for UserService.
	 * @param jwtComponent    The JWT component for token generation and validation
	 * @param passwordEncoder The password encoder for hashing and verifying passwords
	 */
	@Autowired
	public UserService(final JwtComponent jwtComponent, final PasswordEncoder passwordEncoder) {
		this.jwtComponent = jwtComponent;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Get a user by username from the KeyStore.
	 *
	 * @return the User if found, null otherwise
	 */
	public User find(final String username) {
		final var sepPattern = Pattern.compile(USER_INFO_SEPARATOR, Pattern.LITERAL);

		try {
			final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
			final var ks = SecurityManager.loadKeyStore(keyStoreFile);

			final var aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				final var alias = aliases.nextElement();
				if (!alias.startsWith(USER_PREFIX)) {
					continue;
				}

				final var entry = ks.getEntry(alias, new PasswordProtection(new char[] { 's', 'e', 'c', 'r', 'e', 't' }));
				if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
					final var secretKey = secretKeyEntry.getSecretKey();
					final var raw = new String(secretKey.getEncoded(), StandardCharsets.UTF_8);

					// payload = username <SEP> bcrypt(password) <SEP> role
					final var parts = sepPattern.split(raw, -1);
					if (parts.length < 3) {
						// Malformed record; skip but keep logs for diagnostics
						log.warn("Malformed user entry for alias '{}': expected 3 parts, got {}", alias, parts.length);
						continue;
					}

					final String storedUsername = parts[0];
					final String bcryptHash = parts[1];
					final String role = parts[2];

					if (username.equals(storedUsername)) {
						return User.builder().username(storedUsername).password(bcryptHash).role(role).build();
					}
				}
			}
		} catch (Exception e) {
			log.error("Failed to resolve users from KeyStore");
			log.debug("Exception details: ", e);
		}

		return null;
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

	/**
	 * Get the current authenticated user details
	 * @return The {@link UserDto} instance representing the current user
	 */
	public UserDto getCurrent() {
		final var user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return UserDto.fromUser(user);
	}
}
