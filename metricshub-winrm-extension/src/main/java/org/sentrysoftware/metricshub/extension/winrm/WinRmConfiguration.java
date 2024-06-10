package org.sentrysoftware.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.deserialization.TimeDeserializer;
import org.sentrysoftware.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.winrm.service.client.auth.AuthenticationEnum;

/**
 * The WinRmConfiguration interface represents the configuration for the Windows Remote Management protocol
 * in the MetricsHub Extension System.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WinRmConfiguration implements IWinConfiguration {

	private String username;

	private char[] password;

	private String namespace;

	@Default
	@JsonSetter(nulls = SKIP)
	private Integer port = 5985;

	@Default
	@JsonSetter(nulls = SKIP)
	private TransportProtocols protocol = TransportProtocols.HTTP;

	private List<AuthenticationEnum> authentications;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private Long timeout = 120L;

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
					"WinRm",
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
					"WinRm",
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
					"WinRm"
				)
		);
	}

	@Override
	public String toString() {
		String desc = "WinRm";
		if (username != null) {
			desc = desc + " as " + username;
		}
		return desc;
	}
}
