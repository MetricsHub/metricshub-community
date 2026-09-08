package org.metricshub.agent.m8b.tunnel;

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

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Connection settings of the M8B tunnel, resolved from the {@code m8b:} configuration.
 *
 * @param endpoint          WebSocket endpoint of the Governor ({@code wss://host/ws/agent})
 * @param headers           handshake headers, values already decrypted
 * @param certificateFile   PEM certificate to trust for the server; {@code null} for the system trust store
 * @param agentUid          persistent agent instance uid, sent in the handshake
 * @param heartbeatInterval interval between heartbeats until the server imposes its own
 * @param connectTimeout    deadline for the WebSocket handshake and for the registration acknowledgement
 * @param maxBackoff        cap of the reconnection backoff
 */
public record M8bTunnelSettings(
	URI endpoint,
	Map<String, String> headers,
	String certificateFile,
	String agentUid,
	Duration heartbeatInterval,
	Duration connectTimeout,
	Duration maxBackoff
) {
	/**
	 * Name of the handshake header carrying the agent instance uid.
	 */
	public static final String AGENT_UID_HEADER = "X-MetricsHub-Agent-Uid";

	/**
	 * Default handshake and registration deadline.
	 */
	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Default cap of the reconnection backoff.
	 */
	public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(10);

	/**
	 * Settings with the default connect timeout and backoff cap.
	 *
	 * @param endpoint          WebSocket endpoint
	 * @param headers           handshake headers
	 * @param certificateFile   PEM certificate or {@code null}
	 * @param agentUid          agent instance uid
	 * @param heartbeatInterval heartbeat interval
	 */
	public M8bTunnelSettings(
		final URI endpoint,
		final Map<String, String> headers,
		final String certificateFile,
		final String agentUid,
		final Duration heartbeatInterval
	) {
		this(endpoint, headers, certificateFile, agentUid, heartbeatInterval, DEFAULT_CONNECT_TIMEOUT, DEFAULT_MAX_BACKOFF);
	}

	/**
	 * Defensive copies and validation. Credentials travel in the handshake headers, so a cleartext
	 * {@code ws://} endpoint is accepted only for a host written as a loopback literal — never for a
	 * name that merely resolves to one, since the resolution that matters is the one the HTTP client
	 * does when it connects, and it can differ from this one.
	 */
	public M8bTunnelSettings {
		if (endpoint == null) {
			throw new IllegalArgumentException("endpoint is required");
		}
		final String scheme = endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
		if (!"wss".equals(scheme) && !("ws".equals(scheme) && isLiteralLoopback(endpoint.getHost()))) {
			throw new IllegalArgumentException(
				"The M8B endpoint must use wss:// (ws:// is accepted for loopback only): " + endpoint
			);
		}
		if (agentUid == null || agentUid.isBlank()) {
			throw new IllegalArgumentException("agentUid is required");
		}
		headers = headers == null ? Map.of() : Map.copyOf(headers);
		heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(30) : heartbeatInterval;
		connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
		maxBackoff = maxBackoff == null ? DEFAULT_MAX_BACKOFF : maxBackoff;
	}

	/**
	 * Whether a host is loopback by the way it is WRITTEN, never by what DNS says it resolves to.
	 *
	 * <p>Resolving would be two lookups: this one, and the one the HTTP client does when it
	 * connects. Between them a record can change, or a cached one expire, and the second answer is
	 * the one the credentials actually travel to. A name that resolves to loopback today and
	 * elsewhere on the next reconnect would put the handshake headers on the wire in cleartext, so
	 * the exception is granted to literal forms only.
	 *
	 * @param host the host as configured
	 * @return whether cleartext may be allowed for it
	 */
	private static boolean isLiteralLoopback(final String host) {
		if (host == null) {
			return false;
		}
		final String written = host.toLowerCase(Locale.ROOT).strip();
		// A URI keeps the brackets around an IPv6 literal on some paths and not others.
		final String bare =
			written.startsWith("[") && written.endsWith("]") ? written.substring(1, written.length() - 1) : written;
		if ("localhost".equals(bare) || "::1".equals(bare) || "0:0:0:0:0:0:0:1".equals(bare)) {
			return true;
		}
		// The whole of 127.0.0.0/8, written as a literal: parsed here rather than resolved, so no
		// name lookup can be involved in the answer.
		if (!bare.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
			return false;
		}
		final String[] octets = bare.split("\\.");
		for (final String octet : octets) {
			if (Integer.parseInt(octet) > 255) {
				return false;
			}
		}
		return "127".equals(octets[0]);
	}
}
