package org.metricshub.web.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.service.EphemeralApiKeyService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to authenticate M8B proxy requests using ephemeral API keys.
 * <p>
 * This filter checks for the X-M8B-Ephemeral-Token header and validates
 * the token against the EphemeralApiKeyService. Tokens are single-use
 * and short-lived.
 * </p>
 * <p>
 * This filter is placed after ApiKeyAuthFilter and before JwtAuthFilter
 * in the security filter chain.
 * </p>
 */
@Slf4j
public class EphemeralApiKeyFilter extends OncePerRequestFilter {

	/**
	 * The header name for ephemeral tokens.
	 */
	public static final String EPHEMERAL_TOKEN_HEADER = "X-M8B-Ephemeral-Token";

	/**
	 * Principal name for M8B proxy authentication.
	 */
	private static final String M8B_PROXY_PRINCIPAL = "m8b-proxy";

	private final EphemeralApiKeyService ephemeralApiKeyService;

	/**
	 * Creates a new EphemeralApiKeyFilter.
	 *
	 * @param ephemeralApiKeyService the ephemeral API key service
	 */
	public EphemeralApiKeyFilter(EphemeralApiKeyService ephemeralApiKeyService) {
		this.ephemeralApiKeyService = ephemeralApiKeyService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		// Skip if already authenticated (e.g., by ApiKeyAuthFilter)
		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			filterChain.doFilter(request, response);
			return;
		}

		final String ephemeralToken = request.getHeader(EPHEMERAL_TOKEN_HEADER);

		// If no ephemeral token header -> let other filters try (e.g., JWT)
		if (ephemeralToken == null || ephemeralToken.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		// Validate ephemeral token (single-use, consumed immediately)
		if (ephemeralApiKeyService.validateAndConsume(ephemeralToken)) {
			log.debug("Authenticated M8B proxy request with ephemeral token");

			final var authentication = new UsernamePasswordAuthenticationToken(
				M8B_PROXY_PRINCIPAL,
				ephemeralToken,
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_M8B_PROXY"))
			);

			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
			return;
		}

		// Invalid ephemeral token - let other filters try
		log.debug("Invalid or expired ephemeral token, passing to next filter");
		filterChain.doFilter(request, response);
	}
}
