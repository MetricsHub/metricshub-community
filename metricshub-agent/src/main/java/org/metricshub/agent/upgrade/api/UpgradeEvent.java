package org.metricshub.agent.upgrade.api;

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

import org.metricshub.agent.upgrade.UpgradeState;

/**
 * Snapshot of an upgrade lifecycle transition, published to the registered
 * {@link UpgradeStatusListener}.
 *
 * @param packageName            the package being upgraded
 * @param state                  the new upgrade state
 * @param currentVersion         the version the agent currently runs
 * @param currentHash            the identity hash of the currently installed package, when known
 *                               (learned from a previous OpAMP-driven installation)
 * @param targetVersion          the offered version; {@code null} outside an upgrade attempt
 * @param targetHash             the offered package identity hash ({@code PackageAvailable.hash});
 *                               {@code null} outside an upgrade attempt
 * @param errorMessage           the failure cause when {@code state} is {@code FAILED}
 * @param downloadPercent        the download progress (0-100) during {@code DOWNLOADING}
 * @param downloadBytesPerSecond the download rate during {@code DOWNLOADING}
 */
public record UpgradeEvent(
	String packageName,
	UpgradeState state,
	String currentVersion,
	byte[] currentHash,
	String targetVersion,
	byte[] targetHash,
	String errorMessage,
	double downloadPercent,
	double downloadBytesPerSecond
) {
	/**
	 * Creates an event without download progress.
	 *
	 * @param packageName    the package being upgraded
	 * @param state          the new upgrade state
	 * @param currentVersion the version the agent currently runs
	 * @param currentHash    the identity hash of the currently installed package, when known
	 * @param targetVersion  the offered version
	 * @param targetHash     the offered package identity hash
	 * @param errorMessage   the failure cause when the state is {@code FAILED}
	 * @return the event
	 */
	public static UpgradeEvent of(
		final String packageName,
		final UpgradeState state,
		final String currentVersion,
		final byte[] currentHash,
		final String targetVersion,
		final byte[] targetHash,
		final String errorMessage
	) {
		return new UpgradeEvent(
			packageName,
			state,
			currentVersion,
			currentHash,
			targetVersion,
			targetHash,
			errorMessage,
			0,
			0
		);
	}
}
