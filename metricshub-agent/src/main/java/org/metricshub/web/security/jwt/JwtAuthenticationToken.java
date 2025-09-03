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

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Custom authentication token that includes a JWT token and its expiration time.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JwtAuthenticationToken extends UsernamePasswordAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private String token;
	private long expiresIn;

	/**
	 * Constructor to create a JwtAuthenticationToken with principal, credentials, token, and expiration time.
	 * @param principal   The principal (usually the username or user details).
	 * @param credentials The credentials (usually the password).
	 * @param token       The JWT token.
	 * @param expiresIn   The expiration time in seconds.
	 */
	public JwtAuthenticationToken(Object principal, Object credentials, String token, long expiresIn) {
		super(principal, credentials);
		this.token = token;
		this.expiresIn = expiresIn;
	}

	/**
	 * Constructor to create a JwtAuthenticationToken with principal, credentials, token, authorities, and expiration time.
	 *
	 * @param principal   The principal (usually the username or user details).
	 * @param credentials The credentials (usually the password).
	 * @param token       The JWT token.
	 * @param authorities The granted authorities for the user.
	 * @param expiresIn   The expiration time in seconds.
	 */
	public JwtAuthenticationToken(Object principal, Object credentials, String token,
			Collection<? extends GrantedAuthority> authorities, long expiresIn) {
		super(principal, credentials, authorities);
		this.token = token;
		this.expiresIn = expiresIn;
	}
}