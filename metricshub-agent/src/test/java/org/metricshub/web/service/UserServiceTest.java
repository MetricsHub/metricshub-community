package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.metricshub.web.security.UserRegistry;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

	private JwtComponent jwtComponent;
	private PasswordEncoder passwordEncoder;
	private UserRegistry userRegistry;

	private UserService userService;

	@BeforeEach
	void setup() {
		jwtComponent = mock(JwtComponent.class);
		passwordEncoder = mock(PasswordEncoder.class);
		userRegistry = mock(UserRegistry.class);
		userService = new UserService(jwtComponent, passwordEncoder, userRegistry);
	}

	@Test
	void testShouldFindUserWhenPresent() {
		final User user = new User();
		user.setUsername("bob");
		user.setPassword("enc");
		when(userRegistry.getUserByUsername("bob")).thenReturn(user);

		final Optional<User> result = userService.find("bob");

		assertTrue(result.isPresent(), "Optional should be present");
		assertEquals("bob", result.get().getUsername(), "Username should match");
	}

	@Test
	void testShouldReturnEmptyWhenUserMissing() {
		when(userRegistry.getUserByUsername("ghost")).thenReturn(null);

		final Optional<User> result = userService.find("ghost");

		assertTrue(result.isEmpty(), "Optional should be empty when user not found");
	}

	@Test
	void testShouldPerformSecurityAndReturnJwtAuthToken() {
		// Request
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("alice");
		when(req.getPassword()).thenReturn("secret");

		// Stored user
		final User stored = new User();
		stored.setUsername("alice");
		stored.setPassword("ENC");

		// Registry & password match
		when(userRegistry.getUserByUsername("alice")).thenReturn(stored);
		when(passwordEncoder.matches("secret", "ENC")).thenReturn(true);

		// JWT generation and expiry
		when(jwtComponent.generateJwt(stored)).thenReturn("jwt-123");
		when(jwtComponent.getShortExpire()).thenReturn(1800L);

		final JwtAuthToken token = userService.performSecurity(req);

		assertNotNull(token, "JwtAuthToken should not be null");
		assertEquals("jwt-123", token.getToken(), "Token should match generated JWT");
		assertEquals(1800L, token.getExpiresIn(), "ExpiresIn should match jwtComponent.getShortExpire()");
		assertTrue(token.getPrincipal() instanceof User, "Principal should be a User");

		final User principal = (User) token.getPrincipal();
		assertEquals("alice", principal.getUsername(), "Username should match");

		final Set<String> authorities = token
			.getAuthorities()
			.stream()
			.map(a -> a.getAuthority())
			.collect(Collectors.toSet());
		assertEquals(Set.of(SecurityHelper.ROLE_APP_USER), authorities, "Should have ROLE_APP_USER only");
	}

	@Test
	void testShouldThrowWhenUserNotFoundDuringPerformSecurity() {
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("ghost");
		when(req.getPassword()).thenReturn("pw");

		when(userRegistry.getUserByUsername("ghost")).thenReturn(null);

		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> userService.performSecurity(req),
			"Expected UnauthorizedException"
		);

		assertEquals("User not found.", ex.getMessage(), "Exception message should match");
	}

	@Test
	void testShouldThrowWhenBadPasswordDuringPerformSecurity() {
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("john");
		when(req.getPassword()).thenReturn("bad");

		final User stored = new User();
		stored.setUsername("john");
		stored.setPassword("ENCODED");

		when(userRegistry.getUserByUsername("john")).thenReturn(stored);
		when(passwordEncoder.matches("bad", "ENCODED")).thenReturn(false);

		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> userService.performSecurity(req),
			"Expected UnauthorizedException"
		);

		assertEquals("Bad password.", ex.getMessage(), "Exception message should match");
	}
}
