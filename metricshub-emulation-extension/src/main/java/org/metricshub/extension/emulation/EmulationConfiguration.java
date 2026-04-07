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
import org.metricshub.engine.deserialization.MultiValueDeserializer;

/**
 * The EmulationConfiguration represents the configuration for the emulation protocol.
 * This is a marker configuration that enables file-based protocol emulation for offline
 * testing and development.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmulationConfiguration implements IConfiguration {

	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String hostname;

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
		if (property == null || property.isEmpty()) {
			return null;
		}
		if ("hostname".equalsIgnoreCase(property)) {
			return getHostname();
		}
		return null;
	}

	@Override
	public boolean isCorrespondingProtocol(final String protocol) {
		return EmulationExtension.IDENTIFIER.equalsIgnoreCase(protocol);
	}
}
