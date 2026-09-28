package org.metricshub.agent.upgrade.transaction;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.agent.upgrade.UpgradeState;

/**
 * Persistent record of an upgrade attempt. Written by the agent (the only writer) before the
 * detached installer stops the process, and read back at the next startup to reconcile the
 * outcome. Serialized as JSON in the upgrade staging directory, which survives the package
 * upgrade on all platforms.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpgradeTransaction {

	/**
	 * Current schema version of the transaction file.
	 */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	@Default
	@JsonSetter(nulls = SKIP)
	private int schemaVersion = CURRENT_SCHEMA_VERSION;

	/**
	 * Unique identifier of the upgrade attempt.
	 */
	private String upgradeId;

	/**
	 * Name of the package being upgraded.
	 */
	private String packageName;

	/**
	 * Version the agent was running when the upgrade started.
	 */
	private String fromVersion;

	/**
	 * Version offered by the OpAMP server.
	 */
	private String toVersion;

	/**
	 * URL the package was downloaded from.
	 */
	private String downloadUrl;

	/**
	 * Absolute path of the staged package file.
	 */
	private String packageFile;

	/**
	 * Expected SHA-256 of the package content, hexadecimal.
	 */
	private String sha256;

	/**
	 * Offered package identity hash ({@code PackageAvailable.hash}), hexadecimal; echoed back in
	 * the package statuses as required by the OpAMP specification.
	 */
	private String packageHash;

	/**
	 * Detected deployment kind (deb, rpm, msi).
	 */
	private String deploymentKind;

	/**
	 * Current upgrade state.
	 */
	private UpgradeState state;

	/**
	 * Failure cause when the state is {@code FAILED}.
	 */
	private String error;

	/**
	 * Creation timestamp, epoch milliseconds.
	 */
	private long createdAt;

	/**
	 * Last update timestamp, epoch milliseconds.
	 */
	private long updatedAt;

	/**
	 * Timestamp at which the detached installer was launched, epoch milliseconds; 0 before the
	 * installation phase.
	 */
	private long installStartedAt;

	/**
	 * Installation timeout in seconds, captured from the configuration when the upgrade started.
	 */
	private long installTimeoutSeconds;
}
