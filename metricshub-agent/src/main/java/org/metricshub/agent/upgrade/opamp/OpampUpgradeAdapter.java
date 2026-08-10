package org.metricshub.agent.upgrade.opamp;

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

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.upgrade.UpgradeManager;
import org.metricshub.agent.upgrade.UpgradeState;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.api.UpgradeEvent;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.client.packages.PackageDownloadContext;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.proto.DownloadableFile;
import org.metricshub.opamp.proto.Header;
import org.metricshub.opamp.proto.PackageAvailable;
import org.metricshub.opamp.proto.PackageDownloadDetails;
import org.metricshub.opamp.proto.PackageStatus;
import org.metricshub.opamp.proto.PackageStatusEnum;
import org.metricshub.opamp.proto.PackageStatuses;
import org.metricshub.opamp.proto.PackagesAvailable;

/**
 * Bridges the OpAMP client and the {@link UpgradeManager}: translates {@code PackagesAvailable}
 * offers into {@link PackageOffer}s and converts upgrade lifecycle events back into OpAMP package
 * status reports.
 */
@Slf4j
public class OpampUpgradeAdapter implements OpampPackagesHandler {

	private final UpgradeManager upgradeManager;
	private volatile PackageStatusSink statusSink;

	/**
	 * Creates the adapter and registers it as the upgrade status listener.
	 *
	 * @param upgradeManager the upgrade manager
	 */
	public OpampUpgradeAdapter(final UpgradeManager upgradeManager) {
		this.upgradeManager = upgradeManager;
		upgradeManager.setStatusListener(this::forwardEvent);
	}

	/**
	 * Binds the sink of the currently active OpAMP client. Called whenever the OpAMP client is
	 * (re)created, so upgrade transitions occurring between a client rebuild and the next package
	 * offer are still delivered to the live client.
	 *
	 * @param sink the status sink of the active client
	 */
	public void bindSink(final PackageStatusSink sink) {
		this.statusSink = sink;
	}

	@Override
	public void onPackagesAvailable(
		final PackagesAvailable packagesAvailable,
		final PackageStatusSink sink,
		final PackageDownloadContext downloadContext
	) {
		this.statusSink = sink;
		final PackageAvailable offered = packagesAvailable.getPackagesOrDefault(UpgradeManager.PACKAGE_NAME, null);
		if (offered == null) {
			log.warn(
				"The OpAMP package offer does not contain the {} package; the offer is ignored.",
				UpgradeManager.PACKAGE_NAME
			);
			return;
		}
		final DownloadableFile file = offered.getFile();
		final Map<String, String> headers = new HashMap<>();
		for (final Header header : file.getHeaders().getHeadersList()) {
			headers.put(header.getKey(), header.getValue());
		}
		upgradeManager.onPackageOffer(
			new PackageOffer(
				UpgradeManager.PACKAGE_NAME,
				offered.getVersion(),
				file.getDownloadUrl(),
				file.getContentHash().toByteArray(),
				offered.getHash().toByteArray(),
				headers
			)
		);
	}

	@Override
	public PackageStatuses currentPackageStatuses() {
		return PackageStatuses.newBuilder()
			.putPackages(UpgradeManager.PACKAGE_NAME, toPackageStatus(upgradeManager.getCurrentSnapshot()))
			.build();
	}

	/**
	 * Forwards an upgrade event to the OpAMP status sink: download progress rides on the
	 * dedicated progress channel, other transitions replace the package status.
	 *
	 * @param event the upgrade event
	 */
	private void forwardEvent(final UpgradeEvent event) {
		final PackageStatusSink sink = statusSink;
		if (sink == null) {
			return;
		}
		if (
			event.state() == UpgradeState.DOWNLOADING && (event.downloadPercent() > 0 || event.downloadBytesPerSecond() > 0)
		) {
			sink.reportDownloadProgress(
				UpgradeManager.PACKAGE_NAME,
				PackageDownloadDetails.newBuilder()
					.setDownloadPercent(event.downloadPercent())
					.setDownloadBytesPerSecond(event.downloadBytesPerSecond())
					.build()
			);
			return;
		}
		sink.report(toPackageStatus(event));
	}

	/**
	 * Converts an upgrade event into an OpAMP package status.
	 *
	 * @param event the upgrade event
	 * @return the corresponding package status
	 */
	static PackageStatus toPackageStatus(final UpgradeEvent event) {
		final PackageStatus.Builder builder = PackageStatus.newBuilder()
			.setName(UpgradeManager.PACKAGE_NAME)
			.setStatus(toPackageStatusEnum(event.state()));
		if (event.currentVersion() != null && !event.currentVersion().isBlank()) {
			builder.setAgentHasVersion(event.currentVersion());
		}
		if (event.targetVersion() != null && !event.targetVersion().isBlank()) {
			builder.setServerOfferedVersion(event.targetVersion());
		}
		final byte[] targetHash = event.targetHash();
		if (targetHash != null && targetHash.length > 0) {
			// The offered package identity hash is required by the OpAMP specification for
			// statuses of an offer-initiated installation; once installed, the agent has that
			// same package.
			builder.setServerOfferedHash(ByteString.copyFrom(targetHash));
			if (event.state() == UpgradeState.SUCCEEDED) {
				builder.setAgentHasHash(ByteString.copyFrom(targetHash));
			}
		}
		if (event.errorMessage() != null && !event.errorMessage().isBlank()) {
			builder.setErrorMessage(event.errorMessage());
		}
		return builder.build();
	}

	/**
	 * Maps the fine-grained upgrade state onto the OpAMP package status enumeration.
	 *
	 * @param state the upgrade state
	 * @return the OpAMP package status
	 */
	static PackageStatusEnum toPackageStatusEnum(final UpgradeState state) {
		return switch (state) {
			case IDLE, SUCCEEDED -> PackageStatusEnum.PackageStatusEnum_Installed;
			case UPDATE_AVAILABLE, READY_TO_INSTALL -> PackageStatusEnum.PackageStatusEnum_InstallPending;
			case DOWNLOADING, VALIDATING -> PackageStatusEnum.PackageStatusEnum_Downloading;
			case INSTALLING, RESTARTING, VERIFYING -> PackageStatusEnum.PackageStatusEnum_Installing;
			case FAILED -> PackageStatusEnum.PackageStatusEnum_InstallFailed;
		};
	}
}
