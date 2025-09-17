package org.metricshub.web.security.login;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginAuthenticationProviderTest {

	private JwtComponent jwtComponent;
	private UserService userService;
	private PasswordEncoder passwordEncoder;
	private LoginAuthenticationProvider provider;

	@BeforeEach
	void setup() {
		jwtComponent = mock(JwtComponent.class);
		userService = mock(UserService.class);
		passwordEncoder = mock(PasswordEncoder.class);
		provider = new LoginAuthenticationProvider(jwtComponent, userService, passwordEncoder);
	}

	@Test
	void testShouldSupportAnyAuthenticationClass() {
		assertTrue(provider.supports(Object.class), "Provider should return true for supports(..)");
	}

	@Test
	void testShouldRejectWhenDetailsIsNotLoginAuthenticationRequest() {
		final JwtAuthToken auth = new JwtAuthToken("user", null, "jwt", 100L);
		auth.setAuthenticated(false);
		auth.setDetails("not-a-login-request");

		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> provider.authenticate(auth),
			"Unsupported authentication method exception expected."
		);

		assertEquals("Unsuported authentication method.", ex.getMessage(), "Exception message should match");
	}

	@Test
	void testShouldThrowWhenUserNotFound() {
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("ghost");
		when(req.getPassword()).thenReturn("pw");

		final JwtAuthToken auth = new JwtAuthToken("ghost", null, "token", 100L);
		auth.setAuthenticated(false);
		auth.setDetails(req);

		when(userService.find("ghost")).thenReturn(null);

		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> provider.authenticate(auth),
			"User not found exception expected."
		);

		assertEquals("User not found.", ex.getMessage(), "Exception message should match");
	}

	@Test
	void testShouldThrowWhenBadPassword() {
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("john");
		when(req.getPassword()).thenReturn("bad");

		final JwtAuthToken auth = new JwtAuthToken("john", null, "token", 100L);
		auth.setAuthenticated(false);
		auth.setDetails(req);

		final User stored = new User();
		stored.setUsername("john");
		stored.setPassword("ENCODED");

		when(userService.find("john")).thenReturn(stored);
		when(passwordEncoder.matches("bad", "ENCODED")).thenReturn(false);

		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> provider.authenticate(auth),
			"Bad password exception expected."
		);

		assertEquals("Bad password.", ex.getMessage(), "Exception message should match");
	}

	@Test
	void testShouldAuthenticateAndReturnJwtToken() {
		final LoginAuthenticationRequest req = mock(LoginAuthenticationRequest.class);
		when(req.getUsername()).thenReturn("alice");
		when(req.getPassword()).thenReturn("secret");

		final JwtAuthToken input = new JwtAuthToken("alice", null, "token", 100L);
		input.setAuthenticated(false);
		input.setDetails(req);

		final User stored = new User();
		stored.setUsername("alice");
		stored.setPassword("ENC");

		when(userService.find("alice")).thenReturn(stored);
		when(passwordEncoder.matches("secret", "ENC")).thenReturn(true);

		final String jwt = "jwt-123";
		when(jwtComponent.generateJwt(stored)).thenReturn(jwt);
		when(jwtComponent.getShortExpire()).thenReturn(1800L);

		final Authentication out = provider.authenticate(input);

		assertAll(
			() -> assertNotNull(out, "Authentication should not be null"),
			() -> assertTrue(out instanceof JwtAuthToken, "Should return JwtAuthToken"),
			() -> assertEquals(jwt, ((JwtAuthToken) out).getToken(), "JWT token should match"),
			() -> assertEquals(1800L, ((JwtAuthToken) out).getExpiresIn(), "ExpiresIn should come from jwtComponent"),
			() -> assertEquals("alice", ((User) out.getPrincipal()).getUsername(), "Principal username should match"),
			() ->
				assertEquals(
					Collections.singleton(SecurityHelper.ROLE_APP_USER),
					out.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet()),
					"Should have ROLE_APP_USER authority only"
				)
		);
	}
}
