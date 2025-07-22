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

import java.util.Map;

/**
 * Registry for API keys used for authentication. This class provides a method
 * to validate API keys against a predefined set of keys.
 */
public class ApiKeyRegistry {

	private final Map<String, String> apiKeys;

	/**
	 * Constructor for ApiKeyRegistry.
	 * @param apiKeys a map of API keys where the key is the identifier and the value is the actual API key.
	 */
	public ApiKeyRegistry(Map<String, String> apiKeys) {
		this.apiKeys = apiKeys;
	}

	/**
	 * Validates the provided API key against the registered keys.
	 *
	 * @param token the API key to validate.
	 * @return true if the API key is valid, false otherwise.
	 */
	public boolean isValid(final String token) {
		return apiKeys.containsValue(token);
	}

	/**
	 * Retrieves the principal (key) associated with the provided API token.
	 *
	 * @param token the API token to look up.
	 * @return the principal associated with the token, or null if not found.
	 */
	public String getPrincipal(final String token) {
		return apiKeys
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue().equals(token))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}
}
