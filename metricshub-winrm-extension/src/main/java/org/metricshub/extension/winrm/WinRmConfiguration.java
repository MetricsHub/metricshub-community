package org.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.engine.deserialization.MultiValueDeserializer;
import org.metricshub.engine.deserialization.TimeDeserializer;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

/**
 * The WinRmConfiguration interface represents the configuration for the Windows Remote Management protocol
 * in the MetricsHub Extension System.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WinRmConfiguration implements IWinConfiguration {

	private static final String WINRM_DESCRIPTION = "WinRm";

	private String username;

	private char[] password;

	private String namespace;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	@Default
	@JsonSetter(nulls = SKIP)
	private Integer port = 5985;

	@Default
	@JsonSetter(nulls = SKIP)
	private TransportProtocols protocol = TransportProtocols.HTTP;

	@Default
	private List<AuthenticationEnum> authentications = new ArrayList<>(List.of(AuthenticationEnum.NTLM));

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	/**
	 * Whether to trust all the server certificates and skip the hostname verification when
	 * connecting over HTTPS. Enabled by default: WinRM services are commonly exposed with the
	 * self-signed certificate Windows generates for them. Set it to {@code false} to have the
	 * certificate chain validated and the hostname verified during the handshake: the certificate,
	 * or its issuing CA, must then be present in the trust store of the runtime embedded in
	 * MetricsHub - {@code C:\Program Files\MetricsHub\runtime\lib\security\cacerts} on Windows,
	 * {@code /opt/metricshub/lib/runtime/lib/security/cacerts} on Linux - or in the store designated
	 * by {@code -Djavax.net.ssl.trustStore}.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private boolean trustAllCertificates = true;

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			port,
			attr -> attr == null || attr < 1 || attr > 65535,
			() ->
				String.format(
					"Resource %s - Invalid port configured for protocol %s. Port value returned: %s." +
						" This resource will not be monitored. Please verify the configured port value.",
					resourceKey,
					WINRM_DESCRIPTION,
					port
				)
		);

		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid for protocol %s." +
						" Timeout value returned: %s. This resource will not be monitored. Please verify the configured timeout value.",
					resourceKey,
					WINRM_DESCRIPTION,
					timeout
				)
		);

		StringHelper.validateConfigurationAttribute(
			username,
			attr -> attr == null || attr.isBlank(),
			() ->
				String.format(
					"Resource %s - No username configured for protocol %s." +
						" This resource will not be monitored. Please verify the configured username.",
					resourceKey,
					WINRM_DESCRIPTION
				)
		);
	}

	@Override
	public String toString() {
		String desc = WINRM_DESCRIPTION;
		if (username != null) {
			desc = desc + " as " + username;
		}
		return desc;
	}

	@Override
	public IConfiguration copy() {
		return WinRmConfiguration.builder()
			.authentications(new ArrayList<>(authentications))
			.namespace(namespace)
			.password(password)
			.port(port)
			.protocol(protocol)
			.timeout(timeout)
			.trustAllCertificates(trustAllCertificates)
			.username(username)
			.hostname(hostname)
			.build();
	}

	@Override
	public String getProperty(final String property) {
		if (property == null || property.isEmpty()) {
			return null;
		}
		switch (property.toLowerCase()) {
			case "namespace":
				return getNamespace();
			case "password":
				return String.valueOf(getPassword());
			case "port":
				return getPort().toString();
			case "protocol":
				return getProtocol().toString();
			case "timeout":
				return getTimeout().toString();
			case "trustallcertificates":
				return String.valueOf(isTrustAllCertificates());
			case "username":
				return getUsername();
			case "hostname":
				return getHostname();
			default:
				return null;
		}
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return WinRmExtension.IDENTIFIER.equalsIgnoreCase(protocol);
	}
}
