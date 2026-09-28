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
 * Agent: the polling channel toward the Central server, and its cadence.
 * <p>
 * It is written under {@code central:}, which carries everything the two channels share — the URL,
 * the credential, the trusted certificate and the reported identity. What is left here is what only
 * this channel has. {@link CentralConfig#opamp()} hands out the resolved combination; the fields
 * below are what the operator wrote, which is not the same thing.
 * </p>
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
	 * Default path of the OpAMP endpoint on the Central server.
	 */
	public static final String DEFAULT_PATH = "/v1/opamp";

	/**
	 * Whether this channel is enabled, which {@code central.enabled} still has to allow. Enabled
	 * here by default: saying a server manages this agent says it polls it, and an operator who
	 * wants the tunnel alone turns this one off explicitly.
	 */
	@Default
	private boolean enabled = true;

	/**
	 * Path of the OpAMP endpoint on {@code central.url}.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private String path = DEFAULT_PATH;

	/**
	 * A complete endpoint for this channel, which overrides {@code central.url} and {@link #path}
	 * entirely (e.g. {@code https://opamp.example.com/v1/opamp}). For the deployment where the two
	 * channels do not come out of the same ingress; elsewhere, leave it unset.
	 */
	@JsonSetter(nulls = SKIP)
	private String endpoint;

	/**
	 * Headers for this channel alone, merged over {@code central.headers} key by key. Both channels
	 * present the same credential to the same server, so this is for the deployment that splits
	 * them; elsewhere, write it once on the roof.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> headers = new HashMap<>();

	/**
	 * A trusted certificate for this channel alone, overriding {@code central.certificateFile}.
	 * Same rule as {@link #headers}: one server, one authority, unless the deployment splits them.
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
