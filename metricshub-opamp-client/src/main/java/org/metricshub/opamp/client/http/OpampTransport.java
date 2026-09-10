package org.metricshub.opamp.client.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
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

import java.io.IOException;

/**
 * Transport abstraction for one OpAMP exchange: sends a serialized {@code AgentToServer} message
 * and returns the server response. The production implementation is {@link OpampHttpTransport};
 * tests may substitute a fake.
 */
public interface OpampTransport extends AutoCloseable {
	/**
	 * Sends a serialized {@code AgentToServer} message to the OpAMP server.
	 *
	 * @param agentToServerBytes the serialized {@code AgentToServer} message
	 * @return the transport response, whose body is a serialized {@code ServerToAgent} message on
	 *         success
	 * @throws IOException          when the exchange fails at the network level
	 * @throws InterruptedException when the sending thread is interrupted
	 */
	TransportResponse send(byte[] agentToServerBytes) throws IOException, InterruptedException;

	@Override
	void close();
}
