package org.metricshub.opamp.client.impl;

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

import com.google.protobuf.ByteString;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentDisconnect;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.PackageStatuses;

/**
 * Builds the {@code AgentToServer} messages sent by the OpAMP client, applying the OpAMP status
 * compression rules: the first message and any message following a
 * {@code ReportFullState} request or a transport failure carry the full agent state; subsequent
 * messages only carry the fields whose value changed since the last successful report.
 * <p>
 * State setters may be called from any thread; {@link #assemble()} and {@link #commit()} are
 * called from the polling thread only.
 * </p>
 */
public class AgentToServerAssembler {

	private volatile ByteString instanceUid;
	private final long capabilities;
	private final AtomicLong sequenceNumber = new AtomicLong(0);
	private final AtomicReference<AgentDescription> agentDescription = new AtomicReference<>();
	private final AtomicReference<ComponentHealth> health = new AtomicReference<>();
	private Supplier<PackageStatuses> packageStatusesSupplier;

	private boolean fullStateRequested = true;

	private AgentDescription lastSentAgentDescription;
	private ComponentHealth lastSentHealth;
	private PackageStatuses lastSentPackageStatuses;

	private AgentDescription pendingAgentDescription;
	private ComponentHealth pendingHealth;
	private PackageStatuses pendingPackageStatuses;
	private boolean pendingFullState;

	/**
	 * Creates an assembler.
	 *
	 * @param instanceUid  the agent instance UID (16 bytes)
	 * @param capabilities the advertised capabilities bitmask
	 */
	public AgentToServerAssembler(final ByteString instanceUid, final long capabilities) {
		this.instanceUid = instanceUid;
		this.capabilities = capabilities;
	}

	/**
	 * Updates the agent instance UID after the server assigned a new identity.
	 *
	 * @param instanceUid the new instance UID
	 */
	public void setInstanceUid(final ByteString instanceUid) {
		this.instanceUid = instanceUid;
	}

	/**
	 * Sets the agent description to report.
	 *
	 * @param description the agent description
	 */
	public void setAgentDescription(final AgentDescription description) {
		agentDescription.set(description);
	}

	/**
	 * Sets the agent health to report.
	 *
	 * @param componentHealth the agent health
	 */
	public void setHealth(final ComponentHealth componentHealth) {
		health.set(componentHealth);
	}

	/**
	 * Sets the supplier providing the current package statuses; {@code null} when package
	 * reporting is not enabled.
	 *
	 * @param supplier the package statuses supplier
	 */
	public void setPackageStatusesSupplier(final Supplier<PackageStatuses> supplier) {
		this.packageStatusesSupplier = supplier;
	}

	/**
	 * Requests the next assembled message to carry the full agent state (after a
	 * {@code ReportFullState} flag from the server or a transport failure).
	 */
	public void requestFullState() {
		fullStateRequested = true;
	}

	/**
	 * Returns the current instance UID.
	 *
	 * @return the instance UID
	 */
	public ByteString getInstanceUid() {
		return instanceUid;
	}

	/**
	 * Assembles the next {@code AgentToServer} message. The sequence number is incremented for
	 * every assembled message, including retries, as required by the OpAMP specification.
	 *
	 * @return the assembled message
	 */
	public AgentToServer assemble() {
		final AgentToServer.Builder builder = newMessageBuilder();

		pendingFullState = fullStateRequested;
		pendingAgentDescription = agentDescription.get();
		pendingHealth = health.get();
		pendingPackageStatuses = packageStatusesSupplier != null ? packageStatusesSupplier.get() : null;

		if (shouldInclude(pendingAgentDescription, lastSentAgentDescription)) {
			builder.setAgentDescription(pendingAgentDescription);
		}
		if (shouldInclude(pendingHealth, lastSentHealth)) {
			builder.setHealth(pendingHealth);
		}
		if (shouldInclude(pendingPackageStatuses, lastSentPackageStatuses)) {
			builder.setPackageStatuses(pendingPackageStatuses);
		}

		return builder.build();
	}

	/**
	 * Assembles the final {@code AgentDisconnect} message sent when the client stops.
	 *
	 * @return the assembled disconnect message
	 */
	public AgentToServer assembleDisconnect() {
		return newMessageBuilder().setAgentDisconnect(AgentDisconnect.getDefaultInstance()).build();
	}

	/**
	 * Records the last assembled message as successfully reported: fields included in that
	 * message become the new comparison baseline, and any pending full-state request is cleared.
	 */
	public void commit() {
		if (pendingFullState || !Objects.equals(pendingAgentDescription, lastSentAgentDescription)) {
			lastSentAgentDescription = pendingAgentDescription;
		}
		if (pendingFullState || !Objects.equals(pendingHealth, lastSentHealth)) {
			lastSentHealth = pendingHealth;
		}
		if (pendingFullState || !Objects.equals(pendingPackageStatuses, lastSentPackageStatuses)) {
			lastSentPackageStatuses = pendingPackageStatuses;
		}
		if (pendingFullState) {
			fullStateRequested = false;
		}
	}

	/**
	 * Creates a message builder pre-filled with the fields present in every message:
	 * instance UID, sequence number and capabilities.
	 *
	 * @return the pre-filled builder
	 */
	private AgentToServer.Builder newMessageBuilder() {
		return AgentToServer.newBuilder()
			.setInstanceUid(instanceUid)
			.setSequenceNum(sequenceNumber.incrementAndGet())
			.setCapabilities(capabilities);
	}

	/**
	 * Indicates whether a field must be included in the assembled message: when a full-state
	 * report is requested, or when the value changed since the last successful report.
	 *
	 * @param current  the current value; {@code null} values are never included
	 * @param lastSent the last successfully reported value
	 * @return {@code true} when the field must be included
	 */
	private boolean shouldInclude(final Object current, final Object lastSent) {
		return current != null && (pendingFullState || !Objects.equals(current, lastSent));
	}
}
