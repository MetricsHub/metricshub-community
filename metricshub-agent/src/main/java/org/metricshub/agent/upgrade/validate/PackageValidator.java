package org.metricshub.agent.upgrade.validate;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.VersionHelper;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.runner.DeploymentKind;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Validates package offers and staged packages: source package type against the deployment kind,
 * version applicability (downgrade policy), mandatory SHA-256, size bounds and disk-space
 * preflight.
 */
public class PackageValidator {

	/**
	 * Free disk space safety margin kept on top of the package size (200 MiB).
	 */
	static final long DISK_SPACE_MARGIN_BYTES = 200L * 1024 * 1024;

	/**
	 * Validates a package offer before downloading anything.
	 *
	 * @param offer          the package offer
	 * @param currentVersion the version the agent currently runs
	 * @param config         the upgrade configuration
	 * @param deploymentKind the detected deployment kind
	 * @throws UpgradeException when the offer must be rejected
	 */
	public void validateOffer(
		final PackageOffer offer,
		final String currentVersion,
		final UpgradeConfig config,
		final DeploymentKind deploymentKind
	) throws UpgradeException {
		if (!deploymentKind.isUpgradable()) {
			throw new UpgradeException(
				"Automatic upgrade is not supported for " + deploymentKind.name().toLowerCase(Locale.ROOT) + " deployments"
			);
		}
		if (offer.version() == null || offer.version().isBlank()) {
			throw new UpgradeException("The package offer does not carry a version");
		}
		if (offer.downloadUrl() == null || offer.downloadUrl().isBlank()) {
			throw new UpgradeException("The package offer does not carry a download URL");
		}
		if (offer.sha256() == null || offer.sha256().length == 0) {
			throw new UpgradeException("The package offer does not carry the mandatory SHA-256 content hash");
		}

		final String expectedExtension = deploymentKind.getPackageExtension();
		final String urlPath = offer.downloadUrl().toLowerCase(Locale.ROOT);
		final int queryIndex = urlPath.indexOf('?');
		final String pathWithoutQuery = queryIndex >= 0 ? urlPath.substring(0, queryIndex) : urlPath;
		if (!pathWithoutQuery.endsWith(expectedExtension)) {
			throw new UpgradeException(
				"The offered package type does not match this deployment: expected a " +
					expectedExtension +
					" package for a " +
					deploymentKind.name().toLowerCase(Locale.ROOT) +
					" installation"
			);
		}

		final int comparison = VersionHelper.compare(offer.version(), currentVersion);
		if (comparison < 0) {
			if (deploymentKind == DeploymentKind.MSI) {
				throw new UpgradeException("Downgrades are not supported on Windows MSI installations");
			}
			if (!config.isAllowDowngrade()) {
				throw new UpgradeException(
					"The offered version " +
						offer.version() +
						" is older than the running version " +
						currentVersion +
						" and downgrades are disabled (upgrade.allowDowngrade)"
				);
			}
		}
	}

	/**
	 * Validates the staged package file: existence, size bounds, SHA-256 recomputed from disk and
	 * free disk space for the installation.
	 *
	 * @param stagedPackage the staged package file
	 * @param offer         the package offer
	 * @param config        the upgrade configuration
	 * @throws UpgradeException when the staged package must be rejected
	 */
	public void validateStagedPackage(final Path stagedPackage, final PackageOffer offer, final UpgradeConfig config)
		throws UpgradeException {
		try {
			if (!Files.isRegularFile(stagedPackage)) {
				throw new UpgradeException("The staged package file is missing: " + stagedPackage);
			}
			final long size = Files.size(stagedPackage);
			if (size == 0) {
				throw new UpgradeException("The staged package file is empty: " + stagedPackage);
			}
			if (size > config.getMaxPackageSizeBytes()) {
				throw new UpgradeException(
					"The staged package size (" +
						size +
						" bytes) exceeds the configured maximum of " +
						config.getMaxPackageSizeBytes() +
						" bytes"
				);
			}
			if (!MessageDigest.isEqual(offer.sha256(), sha256Of(stagedPackage))) {
				throw new UpgradeException("The staged package SHA-256 does not match the offered content hash");
			}
			final long usableSpace = Files.getFileStore(stagedPackage).getUsableSpace();
			final long requiredSpace = requiredFreeSpace(size);
			if (usableSpace < requiredSpace) {
				throw new UpgradeException(
					"Not enough free disk space to install the package: " +
						usableSpace +
						" bytes available, " +
						requiredSpace +
						" bytes required"
				);
			}
		} catch (IOException e) {
			throw new UpgradeException("Cannot validate the staged package: " + e.getMessage(), e);
		}
	}

	/**
	 * Computes the free space required to safely install a package of the given size.
	 *
	 * @param packageSize the staged package size in bytes
	 * @return the required free space in bytes
	 */
	static long requiredFreeSpace(final long packageSize) {
		final long multiplied = LocalOsHandler.isWindows() ? packageSize * 3 : packageSize * 2;
		return multiplied + DISK_SPACE_MARGIN_BYTES;
	}

	/**
	 * Computes the SHA-256 of a file.
	 *
	 * @param file the file to hash
	 * @return the SHA-256 digest
	 * @throws IOException on I/O failure
	 */
	static byte[] sha256Of(final Path file) throws IOException {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(file)) {
				final byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = input.read(buffer)) >= 0) {
					digest.update(buffer, 0, read);
				}
			}
			return digest.digest();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
