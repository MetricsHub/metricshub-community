package org.metricshub.agent.config;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration of the M8B tunnel: the persistent outbound WebSocket connection through which the
 * MetricsHub Agent registers itself (identity, tool registry, host inventory) with the M8B AI
 * Governor and executes the tools the Governor invokes.
 * <p>
 * The tunnel is opt-in. Invocation timeouts, payload caps and concurrency limits are dictated by the
 * server during registration and are deliberately not configurable here.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class M8bConfig {

	/**
	 * Default interval, in seconds, between two application-level heartbeats.
	 */
	public static final long DEFAULT_HEARTBEAT_INTERVAL = 30;

	/**
	 * Whether the tunnel is enabled. Disabled by default.
	 */
	private boolean enabled;

	/**
	 * WebSocket endpoint of the M8B Governor, e.g. {@code wss://m8b.example.com/ws/agent}.
	 */
	@JsonSetter(nulls = SKIP)
	private String endpoint;

	/**
	 * HTTP headers sent with the WebSocket handshake. Carries the agent credentials, typically
	 * {@code Authorization: Bearer ...}. Values may be encrypted with the MetricsHub keystore.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> headers = new HashMap<>();

	/**
	 * PEM file of the certificate to trust for the M8B server. Defaults to the system trust store.
	 */
	@JsonSetter(nulls = SKIP)
	private String certificateFile;

	/**
	 * Interval, in seconds, between two heartbeats. The server may impose a different value at
	 * registration time.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;

	/**
	 * Names of the tools that must never be advertised to (nor invokable by) the Governor.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Set<String> excludedTools = new HashSet<>();
}
