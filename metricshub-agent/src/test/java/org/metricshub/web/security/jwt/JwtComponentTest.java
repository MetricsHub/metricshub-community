package org.metricshub.web.security.jwt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtComponentTest {

	private JwtComponent jwtComponent;

	@BeforeEach
	void setup() throws Exception {
		jwtComponent = new JwtComponent();

		// Provide a strong enough secret for HS256 (>= 256 bits)
		jwtComponent.secret = "this_is_a_very_long_and_secure_test_secret_key_32bytes_min";
		jwtComponent.initSecretKey();
		jwtComponent.shortExpire = 3600L;
	}

	@Test
	void testShouldGenerateAndParseJwt() {
		final User user = new User();
		user.setUsername("john");

		final String token = jwtComponent.generateJwt(user);

		assertNotNull(token, "Generated JWT should not be null or empty");
		assertTrue(token.getBytes(StandardCharsets.UTF_8).length > 10, "JWT should look non-trivial");

		final Claims claims = jwtComponent.getAllClaimsFromToken(token);
		assertAll(
			() -> assertEquals("MetricsHub", claims.getIssuer(), "Issuer should be MetricsHub"),
			() -> assertEquals("john", claims.getSubject(), "Subject should be the username"),
			() -> assertNotNull(claims.getIssuedAt(), "iat should be present"),
			() -> assertNotNull(claims.getExpiration(), "exp should be present")
		);
	}

	@Test
	void testShouldCreateBuilderWithCustomExpiration() {
		final User user = new User();
		user.setUsername("alice");

		final long customExpire = 120L; // seconds

		final String token = jwtComponent.createAuthorizationJwtBuilder(user, customExpire).compact();
		final Claims claims = jwtComponent.getAllClaimsFromToken(token);

		final long expMs = claims.getExpiration().getTime();
		final long issuedAtMs = claims.getIssuedAt().getTime();

		assertAll(
			() -> assertEquals("alice", claims.getSubject(), "Subject should match"),
			() -> assertTrue(expMs > 0, "exp-iat should be positive"),
			() -> assertTrue(issuedAtMs >= 0, "iat should be positive")
		);
	}

	@Test
	void testShouldGetTokenFromRequestCookie() {
		final MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(SecurityHelper.TOKEN_KEY, "cookie-token"));

		final String fromCookie = jwtComponent.getTokenFromRequestCookie(request);
		assertEquals("cookie-token", fromCookie, "Should extract token value from cookie");
	}

	@Test
	void testShouldReturnNullWhenCookieMissing() {
		final MockHttpServletRequest request = new MockHttpServletRequest();
		// Different cookie name to ensure it's ignored
		request.setCookies(new Cookie("other", "x"));

		final String fromCookie = jwtComponent.getTokenFromRequestCookie(request);
		assertNull(fromCookie, "Should return null when token cookie is absent");
	}

	@Test
	void testShouldThrowUnauthorizedOnInvalidToken() {
		final String bad = "not-a-jwt";
		final UnauthorizedException ex = assertThrows(
			UnauthorizedException.class,
			() -> jwtComponent.getAllClaimsFromToken(bad),
			"Parsing an invalid token should throw UnauthorizedException"
		);
		assertNotNull(ex.getMessage(), "Exception should contain a message");
	}
}
