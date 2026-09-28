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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The MetricsHub Central server this agent is managed by, and the channels it opens toward it.
 * <p>
 * Two channels reach the SAME server: {@code opamp}, which polls for status, health and package
 * offers, and {@code tunnel}, the persistent outbound WebSocket the Central Governor invokes the
 * agent's tools through. They share a host, a credential, a trusted certificate and an identity, so
 * those are written ONCE here rather than repeated per channel.
 * </p>
 * <p>
 * {@code upgrade} sits beside them rather than under {@code opamp}: a package offer arrives over
 * OpAMP, but the download speaks to a REPOSITORY — its own hosts, its own credentials, its own
 * certificate authority. Nothing here happens without a Central server, which is why it lives under
 * this roof; it is not an OpAMP transport setting, which is why it is not under that key.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CentralConfig {

	/**
	 * Whether this agent is managed by a Central server. Disabled by default.
	 * <p>
	 * It turns on BOTH channels: a server that polls an agent it cannot invoke tools on is half a
	 * link. Either can be refused on its own with {@code opamp.enabled} or {@code tunnel.enabled} —
	 * an operator who wants fleet management without exposing tools to the Governor says so there.
	 * </p>
	 */
	private boolean enabled;

	/**
	 * Base URL of the Central server, {@code http(s)://host[:port]}, with no path: each channel adds
	 * its own. The tunnel derives {@code ws} from {@code http} and {@code wss} from {@code https},
	 * which is the only correct mapping — and the one an operator writing two endpoints by hand gets
	 * wrong.
	 */
	@JsonSetter(nulls = SKIP)
	private String url;

	/**
	 * Headers presented to the Central server on both channels, typically the agent's bearer secret.
	 * Values may be encrypted with the MetricsHub keystore.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> headers = new HashMap<>();

	/**
	 * Path to a PEM file holding the certificate that verifies the Central server's TLS credentials;
	 * {@code null} to use the system trust store. This is the SERVER's authority — the one that
	 * verifies package repositories is {@code upgrade.trustedCertificateFile}, and they are rarely
	 * the same.
	 */
	@JsonSetter(nulls = SKIP)
	private String certificateFile;

	/**
	 * Attributes reported to the Central server as this agent's identity. They are merged last and
	 * therefore override both the pre-built agent attributes and the agent-level
	 * {@code attributes:} section, so what the fleet manager sees can be tailored without changing
	 * the attributes attached to the exported metrics. Both channels report them.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> attributes = new HashMap<>();

	/**
	 * The polling channel: status, health and package offers.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private OpAmpConfig opamp = OpAmpConfig.builder().build();

	/**
	 * The tunnel channel: the Governor's tool invocations.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private M8bConfig tunnel = M8bConfig.builder().build();

	/**
	 * What an accepted package offer is allowed to do, and where it may be downloaded from.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private UpgradeConfig upgrade = UpgradeConfig.builder().build();

	/**
	 * The OpAMP channel as it actually runs: its own settings, with everything the two channels
	 * share filled in from this roof.
	 *
	 * @return a resolved copy, never the configured instance
	 */
	public OpAmpConfig opamp() {
		final OpAmpConfig channel = opamp == null ? OpAmpConfig.builder().build() : opamp;
		return OpAmpConfig.builder()
			.enabled(enabled && channel.isEnabled())
			.endpoint(endpointOf(channel.getEndpoint(), channel.getPath(), false))
			.headers(sharedWith(channel.getHeaders()))
			.certificateFile(inherited(channel.getCertificateFile(), certificateFile))
			.path(channel.getPath())
			.pollInterval(channel.getPollInterval())
			.requestTimeout(channel.getRequestTimeout())
			.reportHealth(channel.isReportHealth())
			.build();
	}

	/**
	 * The tunnel channel as it actually runs: its own settings, with everything the two channels
	 * share filled in from this roof.
	 *
	 * @return a resolved copy, never the configured instance
	 */
	public M8bConfig tunnel() {
		final M8bConfig channel = tunnel == null ? M8bConfig.builder().build() : tunnel;
		return M8bConfig.builder()
			.enabled(enabled && channel.isEnabled())
			.endpoint(endpointOf(channel.getEndpoint(), channel.getPath(), true))
			.headers(sharedWith(channel.getHeaders()))
			.certificateFile(inherited(channel.getCertificateFile(), certificateFile))
			.path(channel.getPath())
			.heartbeatInterval(channel.getHeartbeatInterval())
			.excludedTools(channel.getExcludedTools())
			.build();
	}

	/**
	 * Where a channel actually connects: what it was given, or the shared URL with its path.
	 *
	 * @param endpoint  the channel's own endpoint, which overrides everything when set
	 * @param path      the channel's path on the shared URL
	 * @param webSocket whether the scheme has to become {@code ws} / {@code wss}
	 * @return the endpoint, or {@code null} where neither was configured
	 */
	private String endpointOf(final String endpoint, final String path, final boolean webSocket) {
		if (endpoint != null && !endpoint.isBlank()) {
			return endpoint.trim();
		}
		if (url == null || url.isBlank()) {
			return null;
		}
		final String base = webSocket ? webSocketScheme(url.trim()) : url.trim();
		final String tail = path == null || path.isBlank() ? "" : path.trim();
		final boolean joined = base.endsWith("/") && tail.startsWith("/");
		return joined ? base + tail.substring(1) : base + tail;
	}

	/**
	 * @param base the shared URL
	 * @return it, addressed as a WebSocket — {@code http} becomes {@code ws} and {@code https}
	 *         becomes {@code wss}; anything else is left exactly as written
	 */
	private static String webSocketScheme(final String base) {
		final String scheme = base.toLowerCase();
		if (scheme.startsWith("https://")) {
			return "wss://" + base.substring("https://".length());
		}
		if (scheme.startsWith("http://")) {
			return "ws://" + base.substring("http://".length());
		}
		return base;
	}

	/**
	 * @param own what the channel was given
	 * @return the shared headers, with the channel's own overriding them key by key
	 */
	private Map<String, String> sharedWith(final Map<String, String> own) {
		final Map<String, String> merged = new LinkedHashMap<>();
		if (headers != null) {
			merged.putAll(headers);
		}
		if (own != null) {
			merged.putAll(own);
		}
		return merged;
	}

	/**
	 * @param own    what the channel was given
	 * @param shared what the roof carries
	 * @return the channel's own where it has one, the shared value otherwise
	 */
	private static String inherited(final String own, final String shared) {
		return own == null || own.isBlank() ? shared : own;
	}
}
