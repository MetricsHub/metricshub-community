package org.metricshub.web.service;

import java.util.Optional;

import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthenticationToken;
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
	 * authenticationRequest and generate {@link JwtAuthenticationToken} instance
	 * 
	 * @param authenticationRequest Login request defining username and password
	 * @return {@link JwtAuthenticationToken} instance
	 */
	public JwtAuthenticationToken performSecurity(LoginAuthenticationRequest authenticationRequest) {

		final var usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				authenticationRequest.getUsername(), authenticationRequest.getPassword());

		return setDetailsAndAuthenticate(authenticationRequest, usernamePasswordAuthenticationToken);
	}

	/**
	 * Set the given {@link LoginAuthenticationRequest} in the spring security
	 * {@link UsernamePasswordAuthenticationToken} then authenticate
	 * 
	 * @param request                             Login request defining username and password
	 * @param usernamePasswordAuthenticationToken Instance designed for simple presentation of a username and password
	 * @return The {@link JwtAuthenticationToken} returned after calling
	 *         <code>LoginAuthenticationProvider.authenticate</code>
	 */
	private JwtAuthenticationToken setDetailsAndAuthenticate(LoginAuthenticationRequest request,
			final UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) {

		// Set the request in the instance's details
		usernamePasswordAuthenticationToken.setDetails(request);

		// Create a new login authentication provider
		final var loginAuthenticationProvider = new LoginAuthenticationProvider(jwtComponent, this, passwordEncoder);

		// Perform the authentication
		return (JwtAuthenticationToken) loginAuthenticationProvider.authenticate(usernamePasswordAuthenticationToken);
	}
}