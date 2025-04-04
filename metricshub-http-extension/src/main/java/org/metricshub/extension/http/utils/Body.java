package org.metricshub.extension.http.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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

import java.io.Serializable;
import java.util.function.UnaryOperator;

/**
 * Represents the body of an HTTP request.
 */
public interface Body extends Serializable {
	/**
	 * Gets the HTTP body content as a string and performs macro replacements.
	 *
	 * @param username            The HTTP username
	 * @param password            The HTTP password as a character array
	 * @param authenticationToken The HTTP authentication token
	 * @param hostname            The remote hostname
	 * @return The HTTP body content with resolved and parsed HTTP macros
	 */
	String getContent(String username, char[] password, String authenticationToken, String hostname);

	/**
	 * Performs a deep copy of the body.
	 *
	 * @return A new {@link Body} instance with the same content
	 */
	Body copy();

	/**
	 * Updates the actual body attributes using the provided updater function.
	 *
	 * @param updater The function to apply to the current body content
	 */
	void update(UnaryOperator<String> updater);

	/**
	 * Gets a string description of the HTTP body.
	 *
	 * @return The content of the HTTP body as a string
	 */
	String description();
}
