package org.metricshub.cli.service.protocol;

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

/**
 * AbstractTransportProtocolCli is the base class for implementing command-line interface (CLI) transport protocols
 * in the MetricsHub command-line interface.
 * It provides common functionality for deducing port numbers based on transport protocols and checking whether HTTPS is configured.
 */
public abstract class AbstractTransportProtocolCli implements IProtocolConfigCli {

	/**
	 * Get or deduce the port number based on the transport protocol
	 *
	 * @return int value
	 */
	protected int getOrDeducePortNumber() {
		final Integer port = getPort();
		if (port != null) {
			return port;
		} else if (isHttps()) {
			return defaultHttpsPortNumber();
		}
		return defaultHttpPortNumber();
	}

	/**
	 * Check whether HTTPS is configured or not
	 *
	 * @return boolean value
	 */
	protected abstract boolean isHttps();

	/**
	 * @return Default HTTPS port number
	 */
	protected abstract int defaultHttpsPortNumber();

	/**
	 * @return Default HTTP port number
	 */
	protected abstract int defaultHttpPortNumber();

	/**
	 * @return Configured port number
	 */
	protected abstract Integer getPort();
}
