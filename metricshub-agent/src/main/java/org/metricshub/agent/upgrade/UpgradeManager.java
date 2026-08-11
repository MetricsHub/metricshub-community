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

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.api.UpgradeEvent;
import org.metricshub.agent.upgrade.api.UpgradeStatusListener;
import org.metricshub.agent.upgrade.download.PackageDownloader;
import org.metricshub.agent.upgrade.runner.DeploymentDetector;
import org.metricshub.agent.upgrade.runner.RunnerLauncher;
import org.metricshub.agent.upgrade.runner.RunnerMarkers;
import org.metricshub.agent.upgrade.runner.UnsupportedRunnerLauncher;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;
import org.metricshub.agent.upgrade.transaction.UpgradeTransactionStore;
import org.metricshub.agent.upgrade.validate.PackageValidator;
import org.metricshub.web.AgentContextHolder;

/**
 * Orchestrates automatic upgrades driven by OpAMP package offers: validates the offer, downloads
 * and validates the package, persists an upgrade transaction that survives the restart, launches
 * the detached installer, and reconciles the outcome when the agent starts again.
 * <p>
 * Offers are processed on a single worker thread; one upgrade at a time is enforced by
 * {@link UpgradeLock}. Lifecycle transitions are published to the registered
 * {@link UpgradeStatusListener}.
 * </p>
 */
@Slf4j
public class UpgradeManager {

	/**
	 * Name of the top-level package this agent manages.
	 */
	public static final String PACKAGE_NAME = "metricshub";

	private final Supplier<String> currentVersionSupplier;
	private final Supplier<UpgradeConfig> configSupplier;
	private final Path stagingDirectory;
	private final UpgradeTransactionStore transactionStore;
	private final UpgradeLock lock;
	private final PackageDownloader downloader;
	private final PackageValidator validator;
	private final DeploymentDetector deploymentDetector;
	private final RunnerLauncher runnerLauncher;
	private final ExecutorService worker;

	private volatile UpgradeStatusListener statusListener = event -> {};
	private volatile String installedHashHex;

	private final Object snapshotLock = new Object();
	private UpgradeEvent lastEvent;

	/**
	 * Creates the manager with production wiring.
	 *
	 * @param agentContextHolder the holder of the current agent context
	 */
	public UpgradeManager(final AgentContextHolder agentContextHolder) {
		this(
			() -> agentContextHolder.getAgentContext().getAgentInfo().getAttributes().get(AGENT_INFO_VERSION_ATTRIBUTE_KEY),
			() -> agentContextHolder.getAgentContext().getAgentConfig().getUpgrade(),
			UpgradeDirectories.resolveStagingDirectory(),
			new PackageDownloader(),
			new PackageValidator(),
			new DeploymentDetector(),
			new UnsupportedRunnerLauncher()
		);
	}

	/**
	 * Creates the manager with caller-provided collaborators (used by tests).
	 *
	 * @param currentVersionSupplier supplies the version the agent currently runs
	 * @param configSupplier         supplies the upgrade configuration
	 * @param stagingDirectory       the upgrade staging directory
	 * @param downloader             the package downloader
	 * @param validator              the package validator
	 * @param deploymentDetector     the deployment detector
	 * @param runnerLauncher         the detached runner launcher
	 */
	UpgradeManager(
		final Supplier<String> currentVersionSupplier,
		final Supplier<UpgradeConfig> configSupplier,
		final Path stagingDirectory,
		final PackageDownloader downloader,
		final PackageValidator validator,
		final DeploymentDetector deploymentDetector,
		final RunnerLauncher runnerLauncher
	) {
		this.currentVersionSupplier = currentVersionSupplier;
		this.configSupplier = configSupplier;
		this.stagingDirectory = stagingDirectory;
		this.transactionStore = new UpgradeTransactionStore(stagingDirectory);
		this.lock = new UpgradeLock(stagingDirectory);
		this.downloader = downloader;
		this.validator = validator;
		this.deploymentDetector = deploymentDetector;
		this.runnerLauncher = runnerLauncher;
		this.worker = Executors.newSingleThreadExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-upgrade");
			thread.setDaemon(true);
			return thread;
		});
		loadInstalledPackageIdentity();
	}

	/**
	 * Loads the identity hash of the currently installed package, learned from a previously
	 * completed OpAMP upgrade, when it still matches the running version.
	 */
	private void loadInstalledPackageIdentity() {
		final UpgradeTransactionStore.InstalledPackageRecord record = transactionStore.readInstalledPackage();
		if (record != null && VersionHelper.isSameVersion(record.version(), currentVersion())) {
			installedHashHex = record.packageHash();
		}
	}

	/**
	 * Registers the listener receiving upgrade lifecycle transitions.
	 *
	 * @param listener the status listener
	 */
	public void setStatusListener(final UpgradeStatusListener listener) {
		this.statusListener = listener != null ? listener : event -> {};
	}

	/**
	 * Indicates whether this deployment supports automatic in-place upgrades (deb/rpm/msi).
	 *
	 * @return {@code true} when package offers can be honored
	 */
	public boolean isPackageUpgradeSupported() {
		return deploymentDetector.detect().isUpgradable();
	}

	/**
	 * Returns the current upgrade state snapshot, used to seed the first OpAMP status report.
	 *
	 * @return the last published upgrade event
	 */
	public UpgradeEvent getCurrentSnapshot() {
		synchronized (snapshotLock) {
			if (lastEvent == null) {
				lastEvent = UpgradeEvent.of(
					PACKAGE_NAME,
					UpgradeState.IDLE,
					currentVersion(),
					installedHash(),
					null,
					null,
					null
				);
			}
			return lastEvent;
		}
	}

	/**
	 * Reconciles a pending upgrade transaction at agent startup: an upgrade interrupted by the
	 * installation restart is resolved by comparing the running version with the target version.
	 * Must be called before the OpAMP client starts so the verdict is part of the first report.
	 */
	public void reconcileOnStartup() {
		final UpgradeTransaction transaction = transactionStore.read();
		if (transaction == null) {
			lock.releaseStale();
			return;
		}

		final String current = currentVersion();
		final UpgradeState state = transaction.getState();
		log.info(
			"Reconciling the pending upgrade transaction {} ({} -> {}, state {}).",
			transaction.getUpgradeId(),
			transaction.getFromVersion(),
			transaction.getToVersion(),
			state
		);

		if (state != null && state.isInstallPhase()) {
			publish(
				UpgradeEvent.of(
					PACKAGE_NAME,
					UpgradeState.VERIFYING,
					current,
					installedHash(),
					transaction.getToVersion(),
					hashOf(transaction),
					null
				)
			);
			if (VersionHelper.isSameVersion(transaction.getFromVersion(), transaction.getToVersion())) {
				// Same-version hotfix: the running version is identical whether or not the
				// installer ran, so the verdict relies on the runner's completion marker.
				if (RunnerMarkers.installSucceeded(stagingDirectory)) {
					finishReconciliation(transaction, UpgradeState.SUCCEEDED, null, current);
				} else {
					finishReconciliation(
						transaction,
						UpgradeState.FAILED,
						"The installation of the same-version package " +
							transaction.getToVersion() +
							" could not be verified (" +
							RunnerMarkers.readResult(stagingDirectory).orElse("no runner result marker") +
							")",
						current
					);
				}
			} else if (VersionHelper.isSameVersion(current, transaction.getToVersion())) {
				finishReconciliation(transaction, UpgradeState.SUCCEEDED, null, current);
			} else {
				finishReconciliation(
					transaction,
					UpgradeState.FAILED,
					"The agent restarted with version " +
						current +
						" while version " +
						transaction.getToVersion() +
						" was expected",
					current
				);
			}
		} else if (state == UpgradeState.SUCCEEDED || state == UpgradeState.FAILED) {
			if (state == UpgradeState.SUCCEEDED) {
				adoptInstalledIdentity(transaction);
			}
			publish(
				UpgradeEvent.of(
					PACKAGE_NAME,
					state,
					current,
					installedHash(),
					transaction.getToVersion(),
					hashOf(transaction),
					transaction.getError()
				)
			);
			transactionStore.archive(transaction);
			lock.releaseStale();
		} else {
			finishReconciliation(
				transaction,
				UpgradeState.FAILED,
				"The upgrade was interrupted before the installation started",
				current
			);
		}
	}

	/**
	 * Accepts a package offer for asynchronous processing.
	 *
	 * @param offer the package offer
	 */
	public void onPackageOffer(final PackageOffer offer) {
		try {
			worker.submit(() -> processOffer(offer));
		} catch (RejectedExecutionException e) {
			log.warn("The upgrade worker rejected a package offer: {}", e.getMessage());
		}
	}

	/**
	 * Runs the upgrade pipeline for one offer: sanity checks, transaction persistence, download,
	 * validation and detached installer launch.
	 *
	 * @param offer the package offer
	 */
	private void processOffer(final PackageOffer offer) {
		final UpgradeConfig config = configSupplier.get();
		final String current = currentVersion();

		if (config == null || !config.isEnabled()) {
			publish(
				UpgradeEvent.of(
					PACKAGE_NAME,
					UpgradeState.FAILED,
					current,
					installedHash(),
					offer.version(),
					offer.packageHash(),
					"Automatic upgrades are disabled by configuration (upgrade.enabled)"
				)
			);
			return;
		}

		if (VersionHelper.isSameVersion(current, offer.version()) && !hasDifferentPackageIdentity(offer)) {
			log.info("The offered version {} is already installed.", offer.version());
			// The agent runs the offered version: adopt the offered identity hash so the fleet
			// state converges even for packages that were not installed through OpAMP.
			if (installedHashHex == null && offer.packageHash() != null && offer.packageHash().length > 0) {
				installedHashHex = offer.packageHashHex();
				transactionStore.writeInstalledPackage(
					new UpgradeTransactionStore.InstalledPackageRecord(current, installedHashHex)
				);
			}
			publish(
				UpgradeEvent.of(
					PACKAGE_NAME,
					UpgradeState.IDLE,
					current,
					installedHash(),
					offer.version(),
					offer.packageHash(),
					null
				)
			);
			return;
		}

		if (!lock.tryAcquire()) {
			log.warn("Ignoring the package offer for version {}: another upgrade is already in progress.", offer.version());
			return;
		}

		UpgradeTransaction transaction = null;
		try {
			validator.validateOffer(offer, current, config, deploymentDetector.detect());

			transaction = UpgradeTransaction.builder()
				.upgradeId(UUID.randomUUID().toString())
				.packageName(offer.packageName())
				.fromVersion(current)
				.toVersion(offer.version())
				.downloadUrl(offer.downloadUrl())
				.sha256(offer.sha256Hex())
				.packageHash(offer.packageHashHex())
				.deploymentKind(deploymentDetector.detect().name())
				.state(UpgradeState.UPDATE_AVAILABLE)
				.createdAt(System.currentTimeMillis())
				.installTimeoutSeconds(config.getInstallTimeout())
				.build();
			persistAndPublish(transaction, UpgradeState.UPDATE_AVAILABLE, null);

			persistAndPublish(transaction, UpgradeState.DOWNLOADING, null);
			final String targetVersion = offer.version();
			final Path stagedPackage = downloader.download(offer, config, stagingDirectory, (percent, bytesPerSecond) ->
				publish(
					new UpgradeEvent(
						PACKAGE_NAME,
						UpgradeState.DOWNLOADING,
						current,
						installedHash(),
						targetVersion,
						offer.packageHash(),
						null,
						percent,
						bytesPerSecond
					)
				)
			);
			transaction.setPackageFile(stagedPackage.toAbsolutePath().toString());

			persistAndPublish(transaction, UpgradeState.VALIDATING, null);
			validator.validateStagedPackage(stagedPackage, offer, config);

			persistAndPublish(transaction, UpgradeState.READY_TO_INSTALL, null);

			transaction.setInstallStartedAt(System.currentTimeMillis());
			persistAndPublish(transaction, UpgradeState.INSTALLING, null);
			runnerLauncher.launch(transaction, stagedPackage, stagingDirectory);

			persistAndPublish(transaction, UpgradeState.RESTARTING, null);
			log.info(
				"The detached upgrade runner was launched for version {}; the agent is about to be stopped.",
				offer.version()
			);
			// The lock and the transaction remain: the outcome is reconciled at the next startup.
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			failUpgrade(transaction, offer, current, "The upgrade was interrupted");
		} catch (Exception e) {
			failUpgrade(transaction, offer, current, e.getMessage());
		}
	}

	/**
	 * Marks the current upgrade attempt as failed: persists the terminal state, publishes the
	 * event, cleans the staged file and releases the lock.
	 *
	 * @param transaction the transaction, or {@code null} when the failure happened before it was
	 *                    created
	 * @param offer       the package offer
	 * @param current     the current agent version
	 * @param error       the failure cause
	 */
	private void failUpgrade(
		final UpgradeTransaction transaction,
		final PackageOffer offer,
		final String current,
		final String error
	) {
		log.error("Upgrade to version {} failed: {}", offer.version(), error);
		if (transaction != null) {
			transaction.setState(UpgradeState.FAILED);
			transaction.setError(error);
			try {
				transactionStore.write(transaction);
			} catch (IOException e) {
				log.warn("Cannot persist the failed upgrade transaction: {}", e.getMessage());
			}
			transactionStore.archive(transaction);
			deleteStagedFile(transaction);
		}
		// Release the lock before publishing: the event is the observable signal that the
		// attempt is fully terminated.
		lock.release();
		publish(
			UpgradeEvent.of(
				PACKAGE_NAME,
				UpgradeState.FAILED,
				current,
				installedHash(),
				offer.version(),
				offer.packageHash(),
				error
			)
		);
	}

	/**
	 * Persists the transaction in the given state and publishes the corresponding event.
	 *
	 * @param transaction the transaction to persist
	 * @param state       the new state
	 * @param error       the failure cause, when any
	 * @throws IOException when the transaction cannot be persisted
	 */
	private void persistAndPublish(final UpgradeTransaction transaction, final UpgradeState state, final String error)
		throws IOException {
		transaction.setState(state);
		transaction.setError(error);
		transactionStore.write(transaction);
		publish(
			UpgradeEvent.of(
				PACKAGE_NAME,
				state,
				transaction.getFromVersion(),
				installedHash(),
				transaction.getToVersion(),
				hashOf(transaction),
				error
			)
		);
	}

	/**
	 * Terminates a reconciliation: persists the verdict, publishes it, archives the transaction,
	 * cleans the staged file and releases the lock.
	 *
	 * @param transaction the reconciled transaction
	 * @param verdict     the terminal state
	 * @param error       the failure cause, when any
	 * @param current     the current agent version
	 */
	private void finishReconciliation(
		final UpgradeTransaction transaction,
		final UpgradeState verdict,
		final String error,
		final String current
	) {
		transaction.setState(verdict);
		transaction.setError(error);
		try {
			transactionStore.write(transaction);
		} catch (IOException e) {
			log.warn("Cannot persist the reconciled upgrade transaction: {}", e.getMessage());
		}
		transactionStore.archive(transaction);
		deleteStagedFile(transaction);
		RunnerMarkers.clear(stagingDirectory);
		lock.releaseStale();
		if (verdict == UpgradeState.SUCCEEDED) {
			adoptInstalledIdentity(transaction);
		}
		publish(
			UpgradeEvent.of(
				PACKAGE_NAME,
				verdict,
				current,
				installedHash(),
				transaction.getToVersion(),
				hashOf(transaction),
				error
			)
		);
		if (verdict == UpgradeState.SUCCEEDED) {
			log.info("Upgrade to version {} succeeded.", transaction.getToVersion());
		} else {
			log.error("Upgrade to version {} failed: {}", transaction.getToVersion(), error);
		}
	}

	/**
	 * Deletes the staged package file of a finished upgrade attempt.
	 *
	 * @param transaction the finished transaction
	 */
	private void deleteStagedFile(final UpgradeTransaction transaction) {
		if (transaction.getPackageFile() == null) {
			return;
		}
		try {
			Files.deleteIfExists(Path.of(transaction.getPackageFile()));
		} catch (IOException e) {
			log.debug("Cannot delete the staged package {}: {}", transaction.getPackageFile(), e.getMessage());
		}
	}

	/**
	 * Publishes an event to the registered listener and records it as the current snapshot.
	 *
	 * @param event the event to publish
	 */
	private void publish(final UpgradeEvent event) {
		synchronized (snapshotLock) {
			lastEvent = event;
		}
		try {
			statusListener.onUpgradeEvent(event);
		} catch (Exception e) {
			log.warn("The upgrade status listener failed: {}", e.getMessage());
		}
	}

	/**
	 * Indicates whether the offered package identity differs from the installed one. Per the
	 * OpAMP specification, package identity is defined by the offered package hash: a differing
	 * hash must be installed even when the version string is unchanged (same-version hotfix). An
	 * offer without a hash, or an unknown installed identity, is treated as identical.
	 *
	 * @param offer the package offer
	 * @return {@code true} when the offered identity is known to differ from the installed one
	 */
	private boolean hasDifferentPackageIdentity(final PackageOffer offer) {
		if (offer.packageHash() == null || offer.packageHash().length == 0 || installedHashHex == null) {
			return false;
		}
		return !installedHashHex.equalsIgnoreCase(offer.packageHashHex());
	}

	/**
	 * Records the identity of a successfully installed package.
	 *
	 * @param transaction the succeeded transaction
	 */
	private void adoptInstalledIdentity(final UpgradeTransaction transaction) {
		final String hash = transaction.getPackageHash();
		if (hash != null && !hash.isBlank()) {
			installedHashHex = hash;
			transactionStore.writeInstalledPackage(
				new UpgradeTransactionStore.InstalledPackageRecord(transaction.getToVersion(), hash)
			);
		}
	}

	/**
	 * Decodes the identity hash of the currently installed package.
	 *
	 * @return the hash bytes, or {@code null} when unknown
	 */
	private byte[] installedHash() {
		final String hex = installedHashHex;
		if (hex == null || hex.isBlank()) {
			return null;
		}
		try {
			return HexFormat.of().parseHex(hex);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Decodes the package identity hash persisted in a transaction.
	 *
	 * @param transaction the transaction
	 * @return the hash bytes, or {@code null} when the transaction carries none
	 */
	private static byte[] hashOf(final UpgradeTransaction transaction) {
		final String hex = transaction.getPackageHash();
		if (hex == null || hex.isBlank()) {
			return null;
		}
		try {
			return HexFormat.of().parseHex(hex);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Returns the version the agent currently runs.
	 *
	 * @return the current version, or an empty string when unavailable
	 */
	private String currentVersion() {
		try {
			final String version = currentVersionSupplier.get();
			return version != null ? version : "";
		} catch (Exception e) {
			log.debug("Cannot determine the current agent version: {}", e.getMessage());
			return "";
		}
	}
}
