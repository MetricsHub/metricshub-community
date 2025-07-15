package org.metricshub.engine.configuration;

import org.metricshub.engine.common.exception.InvalidConfigurationException;

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

/**
 * This interface represents the generic configuration that's used and implemented by various protocol configurations like
 * HttpConfiguration, IpmiConfiguration, etc ...
 */
public interface IConfiguration {
	/**
	 * Retrieves the declared hostname of the IConfiguration.
	 *
	 * @return the IConfiguration hostname value.
	 */
	String getHostname();

	/**
	 * Replaces the IConfiguration's hostname value by the hostname parameter's value.
	 *
	 * @param hostname the hostname of the local or remote device.
	 */
	void setHostname(String hostname);

	/**
	 * Sets the timeout in seconds
	 *
	 * @param timeout configuration timeout in seconds.
	 */
	void setTimeout(Long timeout);
	/**
	 * Validates the current configuration for the given configured resource key. This method ensures that
	 * the configuration meets all required criteria.
	 * Criteria may include checking for necessary fields, verifying values against allowed ranges or formats,
	 * and ensuring compatibility with the resource's requirements.
	 *
	 * @param resourceKey    A {@link String} representing the unique identifier for the resource
	 *                       used for logging purpose.
	 * @throws InvalidConfigurationException if the provided configuration does not meet the
	 *         necessary criteria.
	 */
	void validateConfiguration(String resourceKey) throws InvalidConfigurationException;

	/**
	 * Creates and returns a deep copy of the current {@code IConfiguration} instance.
	 * The returned instance will have the same attribute values as the original,
	 * but modifications to either instance will not affect the other.
	 *
	 * @return a new {@code IConfiguration} instance that is a deep copy of the original.
	 */
	IConfiguration copy();
}
