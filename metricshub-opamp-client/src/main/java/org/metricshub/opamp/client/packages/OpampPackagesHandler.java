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

import org.metricshub.opamp.proto.PackageStatuses;
import org.metricshub.opamp.proto.PackagesAvailable;

/**
 * Receives OpAMP package offers ({@code PackagesAvailable}) from the server.
 * <p>
 * Implemented by the MetricsHub Agent's Upgrade Manager. When a handler is registered on the
 * OpAMP client before it starts, the client advertises the {@code AcceptsPackages} and
 * {@code ReportsPackageStatuses} capabilities; without a handler these capabilities are not
 * advertised and package offers are ignored.
 * </p>
 */
public interface OpampPackagesHandler {
	/**
	 * Called when the server offers packages to the agent.
	 * <p>
	 * This method is invoked on the OpAMP client's polling thread and must return quickly:
	 * long-running work (download, validation, installation) must be performed asynchronously.
	 * Status transitions are reported through the provided {@link PackageStatusSink}.
	 * </p>
	 *
	 * @param packagesAvailable the package offer received from the OpAMP server
	 * @param statusSink        the sink through which package status transitions are reported
	 * @param downloadContext   connection material (headers, trusted certificate) of the OpAMP
	 *                          endpoint, reusable for package downloads when appropriate
	 */
	void onPackagesAvailable(
		PackagesAvailable packagesAvailable,
		PackageStatusSink statusSink,
		PackageDownloadContext downloadContext
	);

	/**
	 * Returns the current package statuses known by the agent (e.g. the installed MetricsHub
	 * version, or the outcome of an upgrade reconciled at startup). Used by the OpAMP client to
	 * seed its first status report.
	 *
	 * @return the current {@link PackageStatuses}; never {@code null}
	 */
	PackageStatuses currentPackageStatuses();
}
