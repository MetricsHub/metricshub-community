package org.metricshub.agent.fleet;

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

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.ConfigHelper;

/**
 * Resolves the HTTP headers a fleet channel (OpAMP, M8B tunnel) sends to its server: values
 * encrypted with the MetricsHub keystore are decrypted, plain values pass through unchanged.
 */
@Slf4j
public final class FleetHeaders {

	private FleetHeaders() {}

	/**
	 * @param headers the configured headers, possibly {@code null}
	 * @param channel the channel name, for the warning logged on a skipped entry
	 * @return a new map with the decrypted values; entries without a name or a value are skipped
	 */
	public static Map<String, String> decrypt(final Map<String, String> headers, final String channel) {
		final Map<String, String> result = new HashMap<>();
		if (headers == null) {
			return result;
		}
		headers.forEach((key, value) -> {
			// A YAML entry without a value (Authorization:) deserializes to a null the whole-map
			// @JsonSetter(nulls = SKIP) does not catch; the HTTP client rejects null header values,
			// and one bad entry would fail every connection.
			if (key == null || key.isBlank() || value == null) {
				log.warn("Ignoring the {} header '{}': it has no value.", channel, key);
				return;
			}
			result.put(key, new String(ConfigHelper.decrypt(value.toCharArray())));
		});
		return result;
	}
}
