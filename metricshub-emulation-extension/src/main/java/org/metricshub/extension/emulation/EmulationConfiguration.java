package org.metricshub.extension.emulation;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.configuration.IProtocolScopedPropertyAccessor;
import org.metricshub.engine.deserialization.MultiValueDeserializer;

/**
 * Configuration for the emulation protocol.
 *
 * <p>This marker configuration enables file-based protocol emulation for offline
 * testing and development.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmulationConfiguration implements IConfiguration, IProtocolScopedPropertyAccessor {

	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

	private HttpEmulationConfig http;

	private SnmpEmulationConfig snmp;

	private OsCommandEmulationConfig oscommand;

	private SshEmulationConfig ssh;

	@Override
	public void validateConfiguration(final String resourceKey) throws InvalidConfigurationException {
		// No specific validation needed for the emulation configuration
	}

	@Override
	public void setTimeout(final Long timeout) {
		// No timeout needed for emulation
	}

	@Override
	public IConfiguration copy() {
		return EmulationConfiguration.builder().hostname(hostname).build();
	}

	@Override
	public String getProperty(final String property) {
		throw new UnsupportedOperationException("Use getProperty with protocol parameter for EmulationConfiguration");
	}

	@Override
	public String getProperty(final String protocol, final String property) {
		if (protocol == null || protocol.isBlank() || property == null || property.isBlank()) {
			return null;
		}

		if ("hostname".equalsIgnoreCase(property)) {
			return getHostname();
		}

		switch (protocol.toLowerCase()) {
			case "http":
				return http != null ? http.getProperty(property) : null;
			case "snmp":
				return snmp != null ? snmp.getProperty(property) : null;
			case "oscommand":
				return oscommand != null ? oscommand.getProperty(property) : null;
			case "ssh":
				return ssh != null ? ssh.getProperty(property) : null;
			default:
				return null;
		}
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return EmulationExtension.EMULATED_PROTOCOLS.contains(protocol);
	}
}
