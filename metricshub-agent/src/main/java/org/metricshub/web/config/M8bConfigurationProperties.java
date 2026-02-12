package org.metricshub.web.config;

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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for connecting to the M8B Control Plane.
 * <p>
 * This configuration controls whether the MetricsHub agent connects
 * to a central M8B control plane for remote management via WebSocket.
 * </p>
 * <p>
 * Example configuration in metricshub.yaml:
 * </p>
 * <pre>
 * web:
 *   m8b.enabled: true
 *   m8b.url: ws://localhost:8080/ws/agent
 *   m8b.api-key: poc-test-key
 *   m8b.agent-id: agent-01
 *   m8b.reconnect-interval-seconds: 30
 * </pre>
 */
@ConfigurationProperties(prefix = "m8b")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class M8bConfigurationProperties {

	/**
	 * Default reconnect interval in seconds.
	 */
	public static final int DEFAULT_RECONNECT_INTERVAL_SECONDS = 30;

	/**
	 * Default maximum reconnect attempts (0 = unlimited).
	 */
	public static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 0;

	/**
	 * Whether M8B control plane integration is enabled.
	 * Defaults to false.
	 */
	private boolean enabled = false;

	/**
	 * The WebSocket URL of the M8B control plane.
	 * Example: ws://localhost:8080/ws/agent
	 */
	private String url;

	/**
	 * The API key used to authenticate with the M8B control plane.
	 */
	private String apiKey;

	/**
	 * Optional custom agent ID. If not provided, a unique ID will be generated
	 * based on hostname.
	 */
	private String agentId;

	/**
	 * Interval in seconds between reconnection attempts.
	 * Defaults to 30 seconds.
	 */
	private int reconnectIntervalSeconds = DEFAULT_RECONNECT_INTERVAL_SECONDS;

	/**
	 * Maximum number of reconnection attempts. 0 means unlimited.
	 * Defaults to 0 (unlimited).
	 */
	private int maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS;

	/**
	 * Checks if the M8B configuration is valid for establishing a connection.
	 *
	 * @return true if the configuration has a URL and API key, false otherwise
	 */
	public boolean isValid() {
		return enabled && url != null && !url.isBlank() && apiKey != null && !apiKey.isBlank();
	}
}
