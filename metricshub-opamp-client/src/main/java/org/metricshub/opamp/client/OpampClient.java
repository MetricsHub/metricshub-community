package org.metricshub.opamp.client;

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

import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.ComponentHealth;

/**
 * OpAMP client embedded in the MetricsHub Agent: reports the agent status, description and health
 * to an OpAMP server over plain HTTP polling, and dispatches package offers to a registered
 * {@link OpampPackagesHandler}.
 */
public interface OpampClient extends AutoCloseable {
	/**
	 * Starts the client: loads (or creates) the persisted agent instance UID, computes the
	 * advertised capabilities and schedules the first poll. A client can be started only once.
	 */
	void start();

	/**
	 * Stops the client: cancels pending polls, sends a best-effort {@code AgentDisconnect}
	 * message and releases resources.
	 *
	 * @param reason a human-readable reason, logged for troubleshooting
	 */
	void stop(String reason);

	/**
	 * Sets the agent description reported to the server. The new value is sent with the next poll
	 * when it differs from the last reported one.
	 *
	 * @param agentDescription the agent description
	 */
	void setAgentDescription(AgentDescription agentDescription);

	/**
	 * Sets the agent health reported to the server. The new value is sent with the next poll when
	 * it differs from the last reported one.
	 *
	 * @param health the agent health
	 */
	void setHealth(ComponentHealth health);

	/**
	 * Registers the handler receiving package offers. Must be called before {@link #start()}: the
	 * presence of a handler determines whether the {@code AcceptsPackages} and
	 * {@code ReportsPackageStatuses} capabilities are advertised.
	 *
	 * @param packagesHandler the packages handler
	 */
	void setPackagesHandler(OpampPackagesHandler packagesHandler);

	/**
	 * Returns the sink through which package status transitions are reported.
	 *
	 * @return the package status sink
	 */
	PackageStatusSink packageStatusSink();

	/**
	 * Requests an immediate poll, without waiting for the current polling delay to elapse.
	 */
	void pollNow();

	/**
	 * Indicates whether the client has been started and not stopped.
	 *
	 * @return {@code true} when the client is running
	 */
	boolean isStarted();

	@Override
	void close();
}
