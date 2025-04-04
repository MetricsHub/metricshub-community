package org.metricshub.engine.connector.model.common;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * An enumeration representing different HTTP methods, including GET, POST, DELETE, and PUT.
 */
@Getter
@AllArgsConstructor
public enum HttpMethod {
	/**
	 * HTTP GET method.
	 */
	GET,
	/**
	 * HTTP POST method.
	 */
	POST,
	/**
	 * HTTP DELETE method.
	 */
	DELETE,
	/**
	 * HTTP PUT method.
	 */
	PUT;

	/**
	 * List of all HTTP methods.
	 */
	public static final List<HttpMethod> HTTP_METHODS = List.of(HttpMethod.values());
}
