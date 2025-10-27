package org.metricshub.web.security.jwt;

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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityHelper;
import org.metricshub.web.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Component for handling JWT operations such as creation, parsing, and validation.
 */
@Component
public class JwtComponent {

	@Value("${jwt.secret}")
	String secret;

	@Value("${jwt.short_expire}")
	@Getter
	long shortExpire;

	@Value("${jwt.long_expire}")
	@Getter
	long longExpire;

	private SecretKey key;

	/**
	 * Initialize the secret key after the component is constructed.
	 */
	@PostConstruct
	public void initSecretKey() {
		key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Compute the expiration date by adding the number of seconds passed in the
	 * <code>expirationTime</code> argument
	 *
	 * @param date           the date we wish to use to start computing the
	 *                       expiration date
	 * @param expirationTimeSeconds the amount of time we wish to add to given date
	 * @return {@link Date}
	 */
	private static Date computeExpirationDate(final Date date, final long expirationTimeSeconds) {
		final var localDateTime = date
			.toInstant()
			.atZone(ZoneId.systemDefault())
			.toLocalDateTime()
			.plusSeconds(expirationTimeSeconds);
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
	}

	/**
	 * Create a {@link JwtBuilder} for the given {@link User}
	 *
	 * @param user           User which requests the REST API access
	 * @param expirationTime The JWT Claims <a href=
	 *                       "https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-25#section-4.1.4">
	 *                       <code>exp</code></a> (expiration) value. The JWT obtained after this timestamp should not be used.
	 * @return {@link JwtBuilder} object
	 */
	public JwtBuilder createAuthorizationJwtBuilder(final User user, final long expirationTime) {
		final var date = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
		return Jwts
			.builder()
			.issuer("MetricsHub")
			.subject(user.getUsername())
			.issuedAt(date)
			.expiration(computeExpirationDate(date, expirationTime))
			.signWith(key);
	}

	/**
	 * Generate a JWT token for the {@link User}
	 *
	 * @param user User which requests the REST API access
	 * @return JWT as {@link String}
	 */
	public String generateJwt(final User user) {
		return createAuthorizationJwtBuilder(user, shortExpire).compact();
	}

	/**
	 * Get authorization token ({@value SecurityHelper#TOKEN_KEY}) from the
	 * cookie of the given {@link HttpServletRequest}
	 *
	 * @param request The HTTP request which provides request information for HTTP
	 *                servlets.
	 * @return String value or <code>null</code> if the cookie is null
	 */
	public String getTokenFromRequestCookie(final HttpServletRequest request) {
		return getTokenFromCookie(request, SecurityHelper.TOKEN_KEY);
	}

	/**
	 * Get the token from the cookie identified by the given token key from the given request
	 * @param request  The HTTP request which provides request information for HTTP servlets.
	 * @param tokenKey The key of the token cookie
	 * @return String value or <code>null</code> if the cookie is null
	 */
	private static String getTokenFromCookie(final HttpServletRequest request, final String tokenKey) {
		final var cookie = WebUtils.getCookie(request, tokenKey);
		if (cookie != null) {
			return cookie.getValue();
		}
		return null;
	}

	/**
	 * Get All the claims from the given authentication token
	 *
	 * @param token The JWT token
	 * @return {@link Claims} instance
	 */
	public Claims getAllClaimsFromToken(final String token) {
		try {
			return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		} catch (final Exception e) {
			throw new UnauthorizedException(e.getMessage(), e);
		}
	}

	/**
	 * Generate a refresh JWT token for the {@link User}.
	 *
	 * @param user User which requests the REST API access
	 * @return JWT as {@link String}
	 */
	public String generateRefreshJwt(final User user) {
		return createAuthorizationJwtBuilder(user, longExpire).claim("type", "refresh").compact();
	}

	/**
	 * Check if the given token is a refresh token.
	 *
	 * @param claims The JWT token claims
	 * @return true if the token is a refresh token, false otherwise
	 */
	public boolean isRefreshToken(final Claims claims) {
		final var type = claims.get("type");
		return type != null && "refresh".equals(type.toString());
	}

	/**
	 * Get refresh token ({@value SecurityHelper#REFRESH_TOKEN_KEY}) from the
	 * cookie of the given {@link HttpServletRequest}
	 *
	 * @param request The HTTP request which provides request information for HTTP servlets.
	 * @return String value or <code>null</code> if the cookie is null
	 */
	public String getRefreshTokenFromRequestCookie(final HttpServletRequest request) {
		return getTokenFromCookie(request, SecurityHelper.REFRESH_TOKEN_KEY);
	}
}
