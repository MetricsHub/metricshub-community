package org.metricshub.agent.config.protocols;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * This abstract class defines a generic protocol configuration that will be extended to configure all the protocols.
 */
public abstract class AbstractProtocolConfig {

	/**
	 * Decrypt the given encrypted password.
	 *
	 * @param encrypted The encrypted password
	 * @return char array that contain the decrypted password
	 */
	protected char[] decrypt(final char[] encrypted) {
		return ConfigHelper.decrypt(encrypted);
	}

	/**
	 * Convert the configuration to the engine {@link IConfiguration}
	 *
	 * @return {@link IConfiguration} instance
	 */
	public abstract IConfiguration toConfiguration();
}
