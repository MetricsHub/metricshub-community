package org.metricshub.extension.wbem;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Wbem Extension
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

/**
 * The WbemConfiguration interface represents the configuration for the Web-Based Enterprise Management protocol in the MetricsHub engine.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WbemConfiguration implements IConfiguration {

	@Default
	@JsonSetter(nulls = SKIP)
	private final TransportProtocols protocol = TransportProtocols.HTTPS;

	@Default
	@JsonSetter(nulls = SKIP)
	private final Integer port = 5989;

	private String namespace;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	String username;
	char[] password;
	String vCenter;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	@Override
	public String toString() {
		String description = protocol + "/" + port;
		if (username != null) {
			description = description + " as " + username;
		}
		return description;
	}

	/**
	 * Validate the given WBEM information (username and timeout)
	 *
	 * @param resourceKey Resource unique identifier
	 * @throws InvalidConfigurationException
	 */
	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid for protocol %s." +
					" Timeout value returned: %s. This resource will not be monitored. Please verify the configured timeout value.",
					resourceKey,
					"WBEM",
					timeout
				)
		);

		StringHelper.validateConfigurationAttribute(
			port,
			attr -> attr == null || attr < 1 || attr > 65535,
			() ->
				String.format(
					"Resource %s - Invalid port configured for protocol %s. Port value returned: %s." +
					" This resource will not be monitored. Please verify the configured port value.",
					resourceKey,
					"WBEM",
					port
				)
		);

		StringHelper.validateConfigurationAttribute(
			username,
			attr -> attr != null && attr.isBlank(),
			() ->
				String.format(
					"Resource %s - No username configured for protocol %s." +
					" This resource will not be monitored. Please verify the configured username.",
					resourceKey,
					"WBEM"
				)
		);

		StringHelper.validateConfigurationAttribute(
			vCenter,
			attr -> attr != null && attr.isBlank(),
			() ->
				String.format(
					"Resource %s - vCenter value is invalid for protocol %s." +
					" This resource will not be monitored. Please verify the configured vCenter value.",
					resourceKey,
					"WBEM"
				)
		);
	}

	@Override
	public IConfiguration copy() {
		return WbemConfiguration
			.builder()
			.namespace(namespace)
			.password(password)
			.port(port)
			.protocol(protocol)
			.timeout(timeout)
			.username(username)
			.vCenter(vCenter)
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
			case "username":
				return getUsername();
			case "vcenter":
				return getVCenter();
			case "hostname":
				return getHostname();
			default:
				return null;
		}
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return WbemExtension.IDENTIFIER.equalsIgnoreCase(protocol);
	}
}
