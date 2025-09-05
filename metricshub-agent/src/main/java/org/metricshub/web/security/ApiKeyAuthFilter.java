package org.metricshub.web.security;

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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to authenticate requests using an API key.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	/**
	 * The header name for the API key in the request.
	 */
	private static final String API_KEY_HEADER = "Authorization";

	private ApiKeyRegistry apiKeyRegistry;

	@Autowired
	public ApiKeyAuthFilter(ApiKeyRegistry apiKeyRegistry) {
		this.apiKeyRegistry = apiKeyRegistry;
	}

	/**
	 * Processes the incoming request to check for a valid API key.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		final var requestApiKey = request.getHeader(API_KEY_HEADER);

		// If no header -> let other filters try (e.g., JWT)
		if (requestApiKey == null) {
			filterChain.doFilter(request, response);
			return;
		}

		final var token = requestApiKey.replace("Bearer ", "");
		if (apiKeyRegistry.isValid(token)) {
			final var apiKey = apiKeyRegistry.getApiKeyByToken(token);
			final var authentication = new UsernamePasswordAuthenticationToken(
				apiKey.alias(),
				token,
				Collections.emptyList()
			);

			SecurityContextHolder.getContext().setAuthentication(authentication);

			filterChain.doFilter(request, response);
			return;
		}

		SecurityHelper.writeUnauthorizedResponse(response);
	}
}
