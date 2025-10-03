package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.cli.service.UserCliService;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.metricshub.web.security.jwt.JwtAuthToken;
import org.metricshub.web.security.jwt.JwtComponent;
import org.metricshub.web.security.login.LoginAuthenticationRequest;
import org.mockito.MockedStatic;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import picocli.CommandLine;

class UserServiceTest {

	private JwtComponent jwtComponent;
	private PasswordEncoder passwordEncoder;
	private UserService userService;

	@BeforeEach
	void setup() {
		jwtComponent = mock(JwtComponent.class);
		passwordEncoder = new BCryptPasswordEncoder();
		userService = new UserService(jwtComponent, passwordEncoder);
		tempKeystore = tempDir.resolve("metricshub-keystore.p12").toFile();
	}

	@TempDir
	Path tempDir;

	private File tempKeystore;

	@Test
	void testShouldFindUserWhenPresent() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			createUser("bob", "enc".toCharArray(), "rw");

			final User user = new User();
			user.setUsername("bob");
			user.setPassword(new BCryptPasswordEncoder().encode("enc"));
			user.setRole("rw");

			final User result = userService.find("bob");

			assertNotNull(result, "User should not be null");
			assertEquals("bob", result.getUsername(), "Username should match");
		}
	}

	/**
	 * Helper method to create a user via UserCliService.
	 *
	 * @param username The actual username
	 * @param password The actual password
	 * @param role     The role (e.g., "rw" or "ro")
	 * @throws Exception if user creation fails
	 */
	private void createUser(final String username, final char[] password, final String role) throws Exception {
		final UserCliService svc = new UserCliService();
		UserCliService.spec = new CommandLine(svc).getCommandSpec();

		final UserCliService.CreateCommand create = new UserCliService.CreateCommand(username, role, password);
		create.call();
	}

	@Test
	void testShouldReturnNullWhenUserMissing() {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final User result = userService.find("ghost");

			assertNull(result, "User should be null");
		}
	}

	@Test
	void testShouldPerformSecurityAndReturnJwtAuthToken() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			createUser("alice", "secret".toCharArray(), "rw");

			// Request
			final LoginAuthenticationRequest req = new LoginAuthenticationRequest();
			req.setUsername("alice");
			req.setPassword("secret");

			// Stored user
			final User stored = userService.find("alice");

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
	}

	@Test
	void testShouldThrowWhenUserNotFoundDuringPerformSecurity() {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			final LoginAuthenticationRequest req = new LoginAuthenticationRequest();
			req.setUsername("missing");
			req.setPassword("any");

			final UnauthorizedException ex = assertThrows(
				UnauthorizedException.class,
				() -> userService.performSecurity(req),
				"Expected UnauthorizedException"
			);

			assertEquals("User not found.", ex.getMessage(), "Exception message should match");
		}
	}

	@Test
	void testShouldThrowWhenBadPasswordDuringPerformSecurity() throws Exception {
		try (MockedStatic<PasswordEncrypt> mockedEncrypt = mockStatic(PasswordEncrypt.class)) {
			mockedEncrypt.when(() -> PasswordEncrypt.getKeyStoreFile(true)).thenReturn(tempKeystore);

			createUser("john", "good".toCharArray(), "rw");

			final LoginAuthenticationRequest req = new LoginAuthenticationRequest();
			req.setUsername("john");
			req.setPassword("bad");

			final UnauthorizedException ex = assertThrows(
				UnauthorizedException.class,
				() -> userService.performSecurity(req),
				"Expected UnauthorizedException"
			);

			assertEquals("Bad password.", ex.getMessage(), "Exception message should match");
		}
	}
}
