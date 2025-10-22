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

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Security Helper class defining constants and utilities
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityHelper {

	/**
	 * The key used for storing the JWT token in cookies and headers.
	 */
	public static final String TOKEN_KEY = "metricshub-jwt";

	/**
	 * Refresh token key used in cookies and headers.
	 */
	public static final String REFRESH_TOKEN_KEY = "metricshub-refresh-jwt";

	/**
	 * APP User role
	 */
	public static final String ROLE_APP_USER = "ROLE_APP_USER";

	/**
	 * Write unauthorized response in the given {@link HttpServletResponse}
	 *
	 * @param response   HTTP response
	 * @throws IOException if an input or output exception occurred
	 */
	public static void writeUnauthorizedResponse(final HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"Unauthorized\"}");
	}

	/**
	 * Write forbidden response in the given {@link HttpServletResponse}
	 *
	 * @param response   HTTP response
	 * @throws IOException if an input or output exception occurred
	 */
	public static void writeForbiddenResponse(final HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"message\":\"Forbidden\"}");
	}
}
