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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration of the automatic upgrade feature driven by OpAMP package offers: download limits,
 * source restrictions and installation policy. Honored only when the {@code opamp:} section is
 * enabled.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpgradeConfig {

	/**
	 * Default maximum package size in bytes (1 GiB).
	 */
	public static final long DEFAULT_MAX_PACKAGE_SIZE_BYTES = 1024L * 1024 * 1024;

	/**
	 * Default download timeout in seconds.
	 */
	public static final long DEFAULT_DOWNLOAD_TIMEOUT = 1800;

	/**
	 * Default number of download attempts.
	 */
	public static final int DEFAULT_DOWNLOAD_RETRIES = 3;

	/**
	 * Default installation timeout in seconds: an upgrade still not finished after this delay is
	 * reconciled as failed.
	 */
	public static final long DEFAULT_INSTALL_TIMEOUT = 1800;

	/**
	 * Whether automatic upgrades are enabled (when OpAMP itself is enabled).
	 */
	@Default
	private boolean enabled = true;

	/**
	 * Whether package offers older than the running version are accepted.
	 */
	private boolean allowDowngrade;

	/**
	 * Maximum accepted package size in bytes.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private long maxPackageSizeBytes = DEFAULT_MAX_PACKAGE_SIZE_BYTES;

	/**
	 * Timeout in seconds of one download attempt.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long downloadTimeout = DEFAULT_DOWNLOAD_TIMEOUT;

	/**
	 * Number of download attempts before the upgrade fails.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private int downloadRetries = DEFAULT_DOWNLOAD_RETRIES;

	/**
	 * Timeout in seconds of the detached installation.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long installTimeout = DEFAULT_INSTALL_TIMEOUT;

	/**
	 * Hosts allowed as package download sources; empty to allow any HTTPS host.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private List<String> hostAllowlist = new ArrayList<>();

	/**
	 * HTTP headers added to package download requests, keyed by repository host, for
	 * repositories that require authentication (e.g. a private Nexus). A header set is sent
	 * only when the offered download host equals its configured host (case-insensitively) —
	 * never to any other host, whatever URL an OpAMP offer carries — and on redirects only
	 * within the offered origin: a different scheme, host or port receives nothing. Values
	 * may be encrypted with the MetricsHub keystore, exactly like
	 * {@code opamp.headers}, and override same-named headers carried by the offer. Binding
	 * each credential to an operator-named host is deliberate: credentials stay on the agent,
	 * and a compromised OpAMP server cannot redirect them to a host of its choosing.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, Map<String, String>> downloadHeaders = new HashMap<>();

	/**
	 * Path to a PEM file containing the trusted certificate used to verify the package
	 * repository's TLS credentials; {@code null} to use the system trust store.
	 */
	@JsonSetter(nulls = SKIP)
	private String trustedCertificateFile;

	/**
	 * Name of the service the detached upgrade runner must stop and restart
	 * ({@code metricshub-<edition>-service.service} on Linux, {@code MetricsHub <Edition>} on
	 * Windows). Left empty, it is discovered from the installed services, so each edition works
	 * without configuration.
	 */
	@JsonSetter(nulls = SKIP)
	private String serviceName;

	/**
	 * Substring the Windows MSI Authenticode signer subject must contain for the package to be
	 * installed.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private String msiSignatureSubjectContains = "MetricsHub";
}
