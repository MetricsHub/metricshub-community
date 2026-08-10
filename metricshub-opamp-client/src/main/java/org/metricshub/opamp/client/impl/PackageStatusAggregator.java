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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;

/**
 * Thread-safe aggregation of the package statuses reported by the Upgrade Manager, turned into
 * the {@code PackageStatuses} message sent to the OpAMP server. Terminal transitions
 * ({@code Installed}, {@code InstallFailed}) trigger the configured notifier so the client
 * reports them immediately; download progress updates are throttled.
 */
@Slf4j
public class PackageStatusAggregator implements PackageStatusSink {

	/**
	 * Minimum interval between two recorded download progress updates for the same package.
	 */
	static final long PROGRESS_UPDATE_MIN_INTERVAL_MS = 1000L;

	private final Map<String, PackageStatus> statuses = new ConcurrentHashMap<>();
	private final Map<String, Long> lastProgressUpdateMs = new ConcurrentHashMap<>();
	private volatile ByteString serverProvidedAllPackagesHash = ByteString.EMPTY;
	private final Runnable terminalStatusNotifier;

	/**
	 * Creates an aggregator.
	 *
	 * @param terminalStatusNotifier invoked when a terminal package status is reported, so the
	 *                               client can trigger an immediate poll
	 */
	public PackageStatusAggregator(final Runnable terminalStatusNotifier) {
		this.terminalStatusNotifier = terminalStatusNotifier;
	}

	/**
	 * Seeds the aggregator with the statuses currently known by the agent (called once at client
	 * start with the handler's current package statuses).
	 *
	 * @param packageStatuses the initial package statuses
	 */
	public void seed(final PackageStatuses packageStatuses) {
		statuses.putAll(packageStatuses.getPackagesMap());
		if (!packageStatuses.getServerProvidedAllPackagesHash().isEmpty()) {
			serverProvidedAllPackagesHash = packageStatuses.getServerProvidedAllPackagesHash();
		}
	}

	/**
	 * Records the hash of the last package offer received from the server, echoed back in every
	 * status report as required by the OpAMP specification.
	 *
	 * @param allPackagesHash the {@code all_packages_hash} of the last offer
	 */
	public void setServerProvidedAllPackagesHash(final ByteString allPackagesHash) {
		serverProvidedAllPackagesHash = allPackagesHash;
	}

	/**
	 * Builds the {@code PackageStatuses} message reflecting the current aggregated state.
	 *
	 * @return the current package statuses
	 */
	public PackageStatuses toProto() {
		return PackageStatuses.newBuilder()
			.putAllPackages(statuses)
			.setServerProvidedAllPackagesHash(serverProvidedAllPackagesHash)
			.build();
	}

	@Override
	public void report(final PackageStatus status) {
		final String name = status.getName();
		if (name.isEmpty()) {
			log.warn("Ignored a package status report without a package name.");
			return;
		}
		statuses.put(name, status);
		lastProgressUpdateMs.remove(name);
		if (isTerminal(status.getStatus())) {
			terminalStatusNotifier.run();
		}
	}

	@Override
	public void reportDownloadProgress(final String packageName, final PackageDownloadDetails downloadDetails) {
		final PackageStatus current = statuses.get(packageName);
		if (current == null) {
			log.warn("Ignored a download progress report for the unknown package {}.", packageName);
			return;
		}
		final long now = System.currentTimeMillis();
		final Long lastUpdate = lastProgressUpdateMs.get(packageName);
		final boolean complete = downloadDetails.getDownloadPercent() >= 100.0;
		if (!complete && lastUpdate != null && now - lastUpdate < PROGRESS_UPDATE_MIN_INTERVAL_MS) {
			return;
		}
		lastProgressUpdateMs.put(packageName, now);
		statuses.put(packageName, current.toBuilder().setDownloadDetails(downloadDetails).build());
	}

	/**
	 * Indicates whether the given status is terminal for an upgrade attempt.
	 *
	 * @param status the package status
	 * @return {@code true} for {@code Installed} and {@code InstallFailed}
	 */
	private static boolean isTerminal(final PackageStatusEnum status) {
		return switch (status) {
			case PackageStatusEnum_Installed, PackageStatusEnum_InstallFailed -> true;
			default -> false;
		};
	}
}
