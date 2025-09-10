package org.metricshub.extension.wmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WMI Extension
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
import org.metricshub.extension.win.IWinConfiguration;

/**
 * The WmiConfiguration interface represents the configuration for the Windows Management Instrumentation protocol
 * in the MetricsHub extension system.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WmiConfiguration implements IWinConfiguration {

	private String username;
	private char[] password;
	private String namespace;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

	@Override
	public String toString() {
		String description = "WMI";
		if (username != null) {
			description = description + " as " + username;
		}
		return description;
	}

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
					"WMI",
					timeout
				)
		);
	}

	@Override
	public IConfiguration copy() {
		return WmiConfiguration
			.builder()
			.namespace(namespace)
			.password(password)
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
			case "username":
				return getUsername();
			case "password":
				return getPassword();
			case "namespace":
				return getNamespace();
			case "hostname":
				return getHostname();
			case "timeout":
				return getTimeout();
			default:
				return null;
		}
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return "wmi".equalsIgnoreCase(protocol);
	}
}
