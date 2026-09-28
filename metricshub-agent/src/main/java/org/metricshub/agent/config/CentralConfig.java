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
import java.util.Map;
import java.util.TreeMap;
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
	 * A resolved channel carries no {@code path}, on purpose: it is an INPUT to the resolution and
	 * never an output. The supervisors compare these copies to decide whether to rebuild a client,
	 * so a path that produced the same endpoint -- or one an explicit {@code endpoint} overrode
	 * entirely -- would report a change that is not one, and the tunnel would drop its in-flight
	 * invocations for a setting with no effect. What the channel runs on is the endpoint.
	 */

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
		if (tail.isEmpty()) {
			return base;
		}
		// BOTH WAYS ROUND, because an operator writes the path either way. Two separators make a
		// path no server routes; NONE silently welds the path onto the host -- `central.example.com`
		// and `fleet/v1/opamp` become a hostname that resolves to nothing, which reads like a DNS
		// problem rather than a typo. Same rule as `UrlHelper.format`, which lives in the HTTP
		// extension: it is a test-scoped dependency here, and the agent's configuration has no
		// business compiling against an extension for a concatenation.
		final String separator = base.endsWith("/") || tail.startsWith("/") ? "" : "/";
		final String joined = base.endsWith("/") && tail.startsWith("/") ? tail.substring(1) : tail;
		return base + separator + joined;
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
	 * Merges a channel's headers over the shared ones, the way HTTP reads header names.
	 *
	 * <p>CASE-INSENSITIVELY, because that is what a header name is. Kept apart, a shared
	 * {@code Authorization} and a channel's {@code authorization} both reach
	 * {@code HttpRequest.Builder.header}, which appends rather than replaces — the request then
	 * carries the credential TWICE, and a server that reads one value refuses it. An override that
	 * merely spells the name differently must still be an override.
	 *
	 * <p>The first spelling seen wins the key and the last value wins the entry, which is exactly
	 * "the channel overrides the roof": on the wire the spelling means nothing, the value means
	 * everything.
	 *
	 * @param own what the channel was given
	 * @return the shared headers, with the channel's own overriding them name by name
	 */
	private Map<String, String> sharedWith(final Map<String, String> own) {
		final Map<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		copyNamed(headers, merged);
		copyNamed(own, merged);
		return merged;
	}

	/**
	 * Copies headers that have a name, because a comparator cannot order one that has not.
	 *
	 * <p>A map ordered case-insensitively throws on a null key where the plain map this replaced
	 * simply held it — and this runs before {@link org.metricshub.agent.fleet.FleetHeaders#decrypt},
	 * which is where a nameless header is meant to be dropped. Both channels resolve through here,
	 * so one such entry would stop OpAMP and the tunnel alike rather than being ignored.
	 *
	 * <p>Not something this YAML parser produces: {@code ~}, {@code null}, {@code ""} and an empty
	 * key all deserialize to a literal string, never a null. It is a configuration built in code
	 * that can carry one, and the tolerance is older than this merge.
	 *
	 * @param from  what was configured, possibly {@code null}
	 * @param into  where the named entries go
	 */
	private static void copyNamed(final Map<String, String> from, final Map<String, String> into) {
		if (from == null) {
			return;
		}
		from.forEach((name, value) -> {
			if (name != null) {
				into.put(name, value);
			}
		});
	}

	/**
	 * What a channel trusts: its own authority, or the one the roof carries.
	 *
	 * <p>ONLY AN ABSENT VALUE INHERITS, and blank is not absent. Both transports read a blank
	 * certificate file as "use the system trust store", so a channel whose overridden endpoint
	 * presents a publicly trusted certificate says {@code certificateFile: ""} — and treating that
	 * as nothing would hand it the private authority of the other channel, whose TLS handshake then
	 * fails for a reason nothing in the configuration shows. The file says which: written
	 * {@code certificateFile:} or left out it is null and inherits; written {@code ""} it is the
	 * operator saying none.
	 *
	 * @param own    what the channel was given
	 * @param shared what the roof carries
	 * @return the channel's own where it was given one at all, the shared value otherwise
	 */
	private static String inherited(final String own, final String shared) {
		return own == null ? shared : own;
	}
}
