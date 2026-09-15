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
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration of the OpAMP (Open Agent Management Protocol) client embedded in the MetricsHub
 * Agent: remote management endpoint, authentication headers, TLS trust material and polling
 * cadence. Disabled by default.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OpAmpConfig {

	/**
	 * Default interval in seconds between two OpAMP polls.
	 */
	public static final long DEFAULT_POLL_INTERVAL = 30;

	/**
	 * Default timeout in seconds of one OpAMP HTTP exchange.
	 */
	public static final long DEFAULT_REQUEST_TIMEOUT = 10;

	/**
	 * Whether the OpAMP client is enabled.
	 */
	private boolean enabled;

	/**
	 * The OpAMP server endpoint (e.g. {@code https://opamp.example.com/v1/opamp}).
	 */
	@JsonSetter(nulls = SKIP)
	private String endpoint;

	/**
	 * Headers sent with every OpAMP request (e.g. {@code Authorization}). Values may be encrypted
	 * with the MetricsHub keystore.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> headers = new HashMap<>();

	/**
	 * Attributes reported to the OpAMP server in the {@code AgentDescription}. They are merged last
	 * and therefore override both the pre-built agent attributes and the agent-level
	 * {@code attributes:} section, so the identity exposed to the fleet manager can be tailored
	 * without changing the attributes attached to the exported metrics.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> attributes = new HashMap<>();

	/**
	 * Path to a PEM file containing the trusted certificate used to verify the OpAMP server's TLS
	 * credentials; {@code null} to use the system trust store.
	 */
	@JsonSetter(nulls = SKIP)
	private String certificateFile;

	/**
	 * Interval in seconds between two OpAMP polls.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long pollInterval = DEFAULT_POLL_INTERVAL;

	/**
	 * Timeout in seconds of one OpAMP HTTP exchange.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long requestTimeout = DEFAULT_REQUEST_TIMEOUT;

	/**
	 * Whether the agent reports its health to the OpAMP server.
	 */
	@Default
	private boolean reportHealth = true;
}
