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
 * Configuration of the Central tunnel: the persistent outbound WebSocket connection through which
 * the MetricsHub Agent registers itself (identity, tool registry, host inventory) with Central and
 * executes the tools Central invokes.
 * <p>
 * It is written under {@code central:} as {@code tunnel:}, which carries everything the two channels
 * share — the URL, the credential, the trusted certificate and the reported identity. What is left
 * here is what only this channel has. {@link CentralConfig#tunnel()} hands out the resolved
 * combination; the fields below are what the operator wrote, which is not the same thing.
 * </p>
 * <p>
 * Invocation timeouts, payload caps and concurrency limits are dictated by the server during
 * registration and are deliberately not configurable here.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TunnelConfig {

	/**
	 * Default interval, in seconds, between two application-level heartbeats.
	 */
	public static final long DEFAULT_HEARTBEAT_INTERVAL = 30;

	/**
	 * Default path of the tunnel endpoint on the Central server.
	 */
	public static final String DEFAULT_PATH = "/ws/agent";

	/**
	 * Whether this channel is enabled, which {@code central.enabled} still has to allow. Enabled
	 * here by default: the tunnel is how Central reaches this agent at all, and an operator
	 * who wants fleet management without it turns this one off explicitly.
	 */
	@Default
	private boolean enabled = true;

	/**
	 * Path of the tunnel endpoint on {@code central.url}, whose scheme becomes {@code ws} or
	 * {@code wss} accordingly.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private String path = DEFAULT_PATH;

	/**
	 * A complete endpoint for this channel, which overrides {@code central.url} and {@link #path}
	 * entirely (e.g. {@code wss://central.example.com/ws/agent}). For the deployment where the two
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
	 * Interval, in seconds, between two heartbeats. The server may impose a different value at
	 * registration time.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;

	/**
	 * Names of the tools that must never be advertised to (nor invokable by) Central.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Set<String> excludedTools = new HashSet<>();
}
