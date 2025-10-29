package org.metricshub.engine.configuration;

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
 * The IWinConfiguration interface represents the configuration for Windows protocols in the MetricsHub engine.
 */
public interface IWinConfiguration extends IConfiguration {
	/**
	 * Gets the namespace for the Windows protocol.
	 *
	 * @return The namespace as a string.
	 */
	String getNamespace();

	/**
	 * Gets the username for the Windows protocol.
	 *
	 * @return The username as a string.
	 */
	String getUsername();

	/**
	 * Gets the timeout for the Windows protocol.
	 *
	 * @return The timeout as a Long value.
	 */
	Long getTimeout();

	/**
	 * Gets the password for the Windows protocol.
	 *
	 * @return The password as a character array.
	 */
	char[] getPassword();
}
