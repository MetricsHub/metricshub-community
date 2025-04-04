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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.springframework.util.Assert.isTrue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.metricshub.engine.common.helpers.MacrosUpdater;

/**
 * Represents the header of an HTTP request.
 */
public interface Header extends Serializable {
	/**
	 * Gets the HTTP header content as {@link Map} and performs macro replacements
	 *
	 * @param username            HTTP username
	 * @param password            HTTP password
	 * @param authenticationToken HTTP authentication token
	 * @param hostname            HTTP server's hostname
	 * @return header entries as {@link Map}
	 */
	Map<String, String> getContent(String username, char[] password, String authenticationToken, String hostname);

	/**
	 * Performs a deep copy
	 *
	 * @return new {@link Header} instance
	 */
	Header copy();

	/**
	 * Updates the actual header attributes
	 *
	 * @param updater updater function
	 */
	void update(UnaryOperator<String> updater);

	/**
	 * Gets the HTTP header string description
	 *
	 * @return string value
	 */
	String description();

	/**
	 * Parses the given string header and builds a header {@link Map}
	 *
	 * @param header Header content as string formatted like the following example:
	 *
	 *    <pre>
	 *     Accept: application/json
	 *     Content-Encoding: utf-8
	 *    </pre>
	 *
	 * @return Map which indexes keys (header keys) to values (header values)
	 */
	private static Map<String, String> parseHeader(final String header) {
		Map<String, String> result = new HashMap<>();
		for (String line : header.split(NEW_LINE)) {
			if (line != null && !line.trim().isEmpty()) {
				String[] tuple = line.split(":", 2);
				isTrue(tuple.length == 2, "Invalid header entry: " + line);

				result.put(tuple[0].trim(), tuple[1].trim());
			}
		}

		return result;
	}

	/**
	 * Replaces each known HTTP macro in the given header then parse the given header to produce a new {@link Map}
	 *
	 * @param header              header content
	 * @param username            The HTTP username
	 * @param password            The HTTP password
	 * @param authenticationToken The HTTP Authentication Token
	 * @param hostname            The HTTP server's hostname
	 * @return header entries as {@link Map}
	 */
	static Map<String, String> resolveAndParseHeader(
		String header,
		String username,
		char[] password,
		String authenticationToken,
		String hostname
	) {
		final String resolvedHeader = MacrosUpdater.update(
			header,
			username,
			password,
			authenticationToken,
			hostname,
			false
		);

		return parseHeader(resolvedHeader);
	}
}
