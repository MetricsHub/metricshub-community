package org.metricshub.agent.m8b.tunnel;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegister;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegistered;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;

/**
 * What the agent plugs into the tunnel: the registration payload and the reactions to the server.
 * Every callback runs on the tunnel thread and must return quickly; tool executions are dispatched
 * elsewhere by the implementation.
 */
public interface M8bTunnelListener {
	/**
	 * Builds the registration sent right after each (re)connection. Called every time, so the payload
	 * always reflects the current tool registry and host inventory.
	 *
	 * @return the registration message
	 */
	AgentRegister buildRegistration();

	/**
	 * The server acknowledged the registration.
	 *
	 * @param registered the limits imposed by the server
	 */
	default void onRegistered(final AgentRegistered registered) {}

	/**
	 * The server asks to run a tool. The answer is sent back through {@link M8bTunnelClient#send}.
	 *
	 * @param invoke the invocation request
	 */
	default void onInvoke(final ToolInvoke invoke) {}

	/**
	 * The connection was lost or closed. In-flight invocations are pointless from here on: the server
	 * has discarded them.
	 *
	 * @param code   WebSocket close code, {@code -1} on a transport error
	 * @param reason close reason or error message
	 */
	default void onDisconnected(final int code, final String reason) {}
}
