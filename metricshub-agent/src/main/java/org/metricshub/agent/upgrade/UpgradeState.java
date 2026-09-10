package org.metricshub.agent.upgrade;

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

import java.util.Set;

/**
 * Fine-grained lifecycle of an upgrade attempt. The states map onto the coarser OpAMP
 * {@code PackageStatusEnum} for fleet reporting, while the full state rides in the agent health
 * status string.
 */
public enum UpgradeState {
	/**
	 * No upgrade in progress.
	 */
	IDLE,
	/**
	 * A package offer was accepted and the upgrade is starting.
	 */
	UPDATE_AVAILABLE,
	/**
	 * The package is being downloaded from the repository.
	 */
	DOWNLOADING,
	/**
	 * The downloaded package is being validated (checksum, size, type).
	 */
	VALIDATING,
	/**
	 * The package is staged and the detached installer is about to be launched.
	 */
	READY_TO_INSTALL,
	/**
	 * The detached installer has been launched.
	 */
	INSTALLING,
	/**
	 * The agent is about to be stopped by the installer.
	 */
	RESTARTING,
	/**
	 * The agent restarted and is verifying the running version against the target.
	 */
	VERIFYING,
	/**
	 * The upgrade completed and the agent runs the target version.
	 */
	SUCCEEDED,
	/**
	 * The upgrade failed.
	 */
	FAILED;

	private static final Set<UpgradeState> TERMINAL_STATES = Set.of(IDLE, SUCCEEDED, FAILED);
	private static final Set<UpgradeState> INSTALL_PHASE_STATES = Set.of(INSTALLING, RESTARTING, VERIFYING);

	/**
	 * Indicates whether this state ends an upgrade attempt (including the idle state).
	 *
	 * @return {@code true} for {@code IDLE}, {@code SUCCEEDED} and {@code FAILED}
	 */
	public boolean isTerminal() {
		return TERMINAL_STATES.contains(this);
	}

	/**
	 * Indicates whether this state belongs to the installation phase, during which the agent
	 * process is expected to be stopped and restarted by the detached installer.
	 *
	 * @return {@code true} for {@code INSTALLING}, {@code RESTARTING} and {@code VERIFYING}
	 */
	public boolean isInstallPhase() {
		return INSTALL_PHASE_STATES.contains(this);
	}
}
