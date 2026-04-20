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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.deserialization.MultiValueDeserializer;
import org.metricshub.extension.ipmi.IpmiConfiguration;

/**
 * Emulation-aware IPMI configuration carrying the directory that stores IPMI emulation files.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IpmiEmulationConfig extends IpmiConfiguration {

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String directory;

	/**
	 * Creates an emulation-aware IPMI configuration by copying an existing IPMI
	 * configuration and adding the emulation input directory.
	 *
	 * @param configuration source IPMI configuration to copy
	 * @param directory emulation directory containing recorded IPMI responses
	 */
	public IpmiEmulationConfig(final IpmiConfiguration configuration, final String directory) {
		super(
			configuration.getTimeout(),
			configuration.getUsername(),
			configuration.getPassword(),
			configuration.isSkipAuth(),
			configuration.getBmcKey(),
			configuration.getHostname()
		);
		this.directory = directory;
	}
}
