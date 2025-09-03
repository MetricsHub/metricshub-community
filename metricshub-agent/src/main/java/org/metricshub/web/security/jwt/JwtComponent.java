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

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.metricshub.web.exception.UnauthorizedException;
import org.metricshub.web.security.SecurityConstants;
import org.metricshub.web.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

/**
 * Component for handling JWT operations such as creation, parsing, and validation.
 */
@Component
public class JwtComponent {


	@Value("${jwt.secret}")
	public String secret;

	@Value("${project.name}")
	private String appName;

	@Value("${jwt.short_expire}")
	@Getter
	private int shortExpire;

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
	 * @param expirationTime the amount of time we wish to add to given date
	 * @return {@link Date}
	 */
	private static Date computeExpirationDate(final Date date, final int expirationTime) {
		return new Date(date.getTime() + expirationTime * 1000L);
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
	public JwtBuilder createAuthorizationJwtBuilder(final User user, final int expirationTime) {

		final var date = new Date();
		return Jwts
			.builder()
			.issuer(appName)
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
	 * Get authorization token ({@value SecurityConstants#HWS_TOKEN_KEY}) from the
	 * cookie of the given {@link HttpServletRequest}
	 * 
	 * @param request The HTTP request which provides request information for HTTP
	 *                servlets.
	 * @return String value or <code>null</code> if the cookie is null
	 */
	public String getTokenFromRequestCookie(final HttpServletRequest request) {
		final var cookie = WebUtils.getCookie(request, SecurityConstants.TOKEN_KEY);
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
			return Jwts.parser()
               .verifyWith(key)
               .build()
               .parseSignedClaims(token)
               .getPayload();
		} catch (final Exception e) {
			throw new UnauthorizedException(e.getMessage(), e);
		}
		
	}

}

