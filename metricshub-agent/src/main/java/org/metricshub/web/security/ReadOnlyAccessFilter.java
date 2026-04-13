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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Restrict non-GET requests for read-only users, except authentication endpoints.
 */
@Component
public class ReadOnlyAccessFilter extends OncePerRequestFilter {

	private static final String ROLE_READ_ONLY = "ro";
	private static final String AUTH_PATH = "/auth";
	private static final String AUTH_REFRESH_PATH = "/auth/refresh";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			filterChain.doFilter(request, response);
			return;
		}

		final Object principal = authentication.getPrincipal();
		if (principal instanceof User user && isReadOnly(user) && !isAllowedRequest(request)) {
			SecurityHelper.writeForbiddenResponse(response);
			return;
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * Check whether the given user has the read-only role.
	 *
	 * @param user the authenticated user
	 * @return true when the role is read-only
	 */
	private boolean isReadOnly(final User user) {
		return ROLE_READ_ONLY.equalsIgnoreCase(user.getRole());
	}

	/**
	 * Determine if the incoming request is allowed for read-only users.
	 *
	 * @param request the HTTP request
	 * @return true when the request is allowed
	 */
	private boolean isAllowedRequest(final HttpServletRequest request) {
		final String method = request.getMethod();
		if ("GET".equalsIgnoreCase(method)) {
			return true;
		}

		if (("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) && isAuthEndpoint(request)) {
			return true;
		}

		return false;
	}

	/**
	 * Check if the request targets the authentication endpoints.
	 *
	 * @param request the HTTP request
	 * @return true when the endpoint is /auth or /auth/refresh
	 */
	private boolean isAuthEndpoint(final HttpServletRequest request) {
		String path = request.getRequestURI();
		final String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}

		if (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}

		return AUTH_PATH.equals(path) || AUTH_REFRESH_PATH.equals(path);
	}
}
