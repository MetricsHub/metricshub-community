package org.metricshub.extension.ping;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ping Extension
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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;

/**
 * PingRequestExecutor is responsible for sending ping requests to a specified host to verify its reachability.
 * It utilizes the ICMP protocol to perform the ping operation.
 */
@Slf4j
public class PingRequestExecutor {

	/**
	 * Sends a ping request to the specified host to verify its reachability using the ICMP protocol.
	 *
	 * @param hostname The name of the host that will be tested. It must be a valid hostname or IP address.
	 * @param timeout  The timeout in milliseconds for the ping request.
	 * @return true if the host is reachable within the specified timeout; false otherwise.
	 * @throws UnknownHostException if the host cannot be determined from the given hostname.
	 */
	@WithSpan("Ping")
	boolean ping(@SpanAttribute("host.hostname") final String hostname, @SpanAttribute("ping.timeout") int timeout)
		throws UnknownHostException {
		final var pingAddress = InetAddress.getByName(hostname);

		try {
			return pingAddress.isReachable(timeout);
		} catch (IOException e) {
			log.error("Hostname {}. A network error occurred: %s", hostname, e);
			return false;
		}
	}
}
