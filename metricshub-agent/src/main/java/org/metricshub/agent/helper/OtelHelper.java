package org.metricshub.agent.helper;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Helper class providing methods related to OpenTelemetry (OTEL) configuration.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OtelHelper {

	/**
	 * Whether this key should be accepted or not.<br>
	 * If the key starts with '__' then it is not accepted.
	 * @param key OTEL key to test
	 *
	 * @return boolean value
	 */
	public static boolean isAcceptedKey(final String key) {
		return !key.startsWith("__");
	}

	/**
	 * Create the host attributes based on the given attributes.
	 *
	 * @param computedHostResourceAttributes Host Resource attributes: host.name, os.type, host.type, etc
	 *                                       collected by the engine.
	 * @param userAttributes                 User configured attributes.
	 * @return Map instance containing the host attributes.
	 */
	public static Map<String, String> buildHostAttributes(
		@NonNull final Map<String, String> computedHostResourceAttributes,
		@NonNull final Map<String, String> userAttributes
	) {
		// Prepare the resource attributes
		final Map<String, String> attributes = new HashMap<>(computedHostResourceAttributes);

		// Add user attributes to the resource attributes
		// host.name is managed by the engine based on the resolveHostnameToFqdn flag, so we shouldn't override it here.
		// host.type is managed by the engine using a set of rules to determine the host.type in OTEL format so we shouldn't override it here.
		// The other attributes are user-defined so we can override them.
		userAttributes
			.entrySet()
			.stream()
			.filter(keyValue -> {
				final String key = keyValue.getKey();
				return !key.equals("host.name") && !key.equals("host.type");
			})
			.forEach(entry -> attributes.put(entry.getKey(), entry.getValue()));

		return attributes;
	}
}
