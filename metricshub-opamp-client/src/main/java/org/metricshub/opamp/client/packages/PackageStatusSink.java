package org.metricshub.opamp.client.packages;

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

import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;

/**
 * Thread-safe sink through which the Upgrade Manager reports package status transitions to the
 * OpAMP client. Reported statuses are aggregated into the {@code PackageStatuses} message sent
 * to the server. Terminal transitions ({@code Installed}, {@code InstallFailed}) trigger an
 * immediate status report; non-terminal transitions ride on the regular polling cadence.
 */
public interface PackageStatusSink {
	/**
	 * Reports the current status of a package. The {@code name} field of the given status
	 * identifies the package entry to create or replace.
	 *
	 * @param status the new package status; its {@code name} field must be set
	 */
	void report(PackageStatus status);

	/**
	 * Updates the download progress of a package currently in the {@code Downloading} state.
	 * Updates may be throttled by the implementation; the latest value is reported with the next
	 * message sent to the server.
	 *
	 * @param packageName     the name of the package being downloaded
	 * @param downloadDetails the download progress details (percent, bytes per second)
	 */
	void reportDownloadProgress(String packageName, PackageDownloadDetails downloadDetails);
}
