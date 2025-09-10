package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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
import org.metricshub.engine.deserialization.MultiValueDeserializer;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * The IpmiConfiguration class represents the configuration for IPMI (Intelligent Platform Management Interface) connections
 * in the MetricsHub engine.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IpmiConfiguration implements IConfiguration {

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	private String username;
	private char[] password;
	private boolean skipAuth;
	private String bmcKey;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	@Override
	public String toString() {
		String description = "IPMI";
		if (username != null) {
			description = description + " as " + username;
		}
		return description;
	}

	/**
	 * Validate the given IPMI information (username and timeout)
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
					"IPMI",
					timeout
				)
		);
	}

	@Override
	public IConfiguration copy() {
		return IpmiConfiguration
			.builder()
			.bmcKey(bmcKey)
			.password(password)
			.skipAuth(skipAuth)
			.timeout(timeout)
			.username(username)
			.hostname(hostname)
			.build();
	}

	@Override
	public Object getProperty(final String property) {
		if (property == null || property.isEmpty()) {
			return null;
		}
		switch (property.toLowerCase()) {
			case "bmckey":
				return getBmcKey();
			case "password":
				return getPassword();
			case "skipauth":
				return isSkipAuth();
			case "timeout":
				return getTimeout();
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
		return "ipmi".equalsIgnoreCase(protocol);
	}
}
