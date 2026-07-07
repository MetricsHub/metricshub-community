package org.metricshub.cli.service.protocol;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * Interface for CLI configurations of protocols to convert to core engine configurations.
 */
public interface IProtocolConfigCli {
	/**
	 * Set a string value in the given JSON node if the value is not null.
	 *
	 * @param node The JSON node to update
	 * @param key The node key
	 * @param value The string value
	 */
	static void setIfNotNull(final ObjectNode node, final String key, final String value) {
		if (value != null) {
			node.put(key, value);
		}
	}

	/**
	 * Convert the CLI configuration to the core engine configuration
	 *
	 * @param defaultUsername Username to use in case the protocol username is undefined
	 * @param defaultPassword Password to use in case the protocol password is undefined
	 *
	 * @return Instance of {@link IConfiguration}
	 * @throws InvalidConfigurationException if the configuration is invalid.
	 */
	IConfiguration toConfiguration(String defaultUsername, char[] defaultPassword) throws InvalidConfigurationException;
}
