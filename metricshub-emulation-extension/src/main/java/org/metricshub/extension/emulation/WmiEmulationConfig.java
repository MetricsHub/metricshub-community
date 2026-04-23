package org.metricshub.extension.emulation;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2026 MetricsHub
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
import org.metricshub.extension.wmi.WmiConfiguration;

/**
 * Emulation-aware WMI configuration carrying the directory that stores WMI emulation files.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WmiEmulationConfig extends WmiConfiguration {

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	private String directory;

	/**
	 * Creates an emulation-aware WMI configuration by copying an existing WMI
	 * configuration and adding the emulation input directory.
	 *
	 * @param configuration source WMI configuration to copy
	 * @param directory emulation directory containing recorded WMI responses
	 */
	public WmiEmulationConfig(final WmiConfiguration configuration, final String directory) {
		super(
			configuration.getUsername(),
			configuration.getPassword(),
			configuration.getNamespace(),
			configuration.getHostname(),
			configuration.getTimeout()
		);
		this.directory = directory;
	}
}
