package org.metricshub.agent.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.api.PackageOffer;
import org.metricshub.agent.upgrade.api.UpgradeEvent;
import org.metricshub.agent.upgrade.download.DownloadProgressListener;
import org.metricshub.agent.upgrade.download.PackageDownloader;
import org.metricshub.agent.upgrade.runner.DeploymentDetector;
import org.metricshub.agent.upgrade.runner.RunnerLauncher;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;
import org.metricshub.agent.upgrade.transaction.UpgradeTransactionStore;
import org.metricshub.agent.upgrade.validate.PackageValidator;
import org.metricshub.engine.common.helpers.LocalOsHandler;

class UpgradeManagerTest {

	private static final String CURRENT_VERSION = "3.9.05";
	private static final String TARGET_VERSION = "3.10.00";

	@TempDir
	Path tempDir;

	private final List<UpgradeEvent> events = new CopyOnWriteArrayList<>();
	private final AtomicInteger launches = new AtomicInteger();
	private UpgradeConfig config = UpgradeConfig.builder().build();

	/**
	 * Downloader test double staging a fixed file without any network access.
	 */
	private class FakeDownloader extends PackageDownloader {

		@Override
		public Path download(
			final PackageOffer offer,
			final UpgradeConfig upgradeConfig,
			final Path targetDirectory,
			final DownloadProgressListener progressListener
		) throws UpgradeException {
			try {
				Files.createDirectories(targetDirectory);
				final Path staged = targetDirectory.resolve("staged.pkg");
				Files.writeString(staged, "package");
				progressListener.onProgress(50, 1024);
				progressListener.onProgress(100, 2048);
				return staged;
			} catch (Exception e) {
				throw new UpgradeException(e.getMessage(), e);
			}
		}
	}

	/**
	 * Validator test double accepting everything.
	 */
	private static class AcceptingValidator extends PackageValidator {

		@Override
		public void validateOffer(
			final PackageOffer offer,
			final String currentVersion,
			final UpgradeConfig config,
			final org.metricshub.agent.upgrade.runner.DeploymentKind deploymentKind
		) {}

		@Override
		public void validateStagedPackage(final Path stagedPackage, final PackageOffer offer, final UpgradeConfig config) {}
	}

	private UpgradeManager newManager(final RunnerLauncher launcher) {
		return new UpgradeManager(
			() -> CURRENT_VERSION,
			() -> config,
			tempDir,
			new FakeDownloader(),
			new AcceptingValidator(),
			new DeploymentDetector(command -> true),
			launcher
		);
	}

	private static PackageOffer offer(final String version) {
		return new PackageOffer(
			UpgradeManager.PACKAGE_NAME,
			version,
			"https://repo.example.com/metricshub.pkg",
			new byte[] { 1 },
			new byte[] { 7, 7 },
			Map.of()
		);
	}

	private CountDownLatch expectState(final UpgradeManager manager, final UpgradeState expected) {
		final CountDownLatch latch = new CountDownLatch(1);
		manager.setStatusListener(event -> {
			events.add(event);
			if (event.state() == expected) {
				latch.countDown();
			}
		});
		return latch;
	}

	@Test
	void successfulPipelineShouldReachRestarting() throws Exception {
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) ->
			launches.incrementAndGet()
		);
		final CountDownLatch restarting = expectState(manager, UpgradeState.RESTARTING);

		manager.onPackageOffer(offer(TARGET_VERSION));

		assertTrue(restarting.await(10, TimeUnit.SECONDS));
		assertEquals(1, launches.get());

		final List<UpgradeState> states = events.stream().map(UpgradeEvent::state).distinct().toList();
		assertEquals(
			List.of(
				UpgradeState.UPDATE_AVAILABLE,
				UpgradeState.DOWNLOADING,
				UpgradeState.VALIDATING,
				UpgradeState.READY_TO_INSTALL,
				UpgradeState.INSTALLING,
				UpgradeState.RESTARTING
			),
			states
		);

		// The transaction and the lock survive for the post-restart reconciliation
		final UpgradeTransaction transaction = new UpgradeTransactionStore(tempDir).read();
		assertNotNull(transaction);
		assertEquals(UpgradeState.RESTARTING, transaction.getState());
		assertEquals(TARGET_VERSION, transaction.getToVersion());
		assertTrue(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));

		// Download progress was published
		assertTrue(events.stream().anyMatch(event -> event.downloadPercent() == 50));
	}

	@Test
	void sameVersionOfferShouldReportInstalledWithoutUpgrading() throws Exception {
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) ->
			launches.incrementAndGet()
		);
		final CountDownLatch idle = expectState(manager, UpgradeState.IDLE);

		manager.onPackageOffer(offer(CURRENT_VERSION));

		assertTrue(idle.await(10, TimeUnit.SECONDS));
		assertEquals(0, launches.get());
		assertFalse(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));
	}

	@Test
	void launcherFailureShouldFailTheUpgradeAndReleaseTheLock() throws Exception {
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) -> {
			throw new UnsupportedOperationException("No runner on this platform");
		});
		final CountDownLatch failed = expectState(manager, UpgradeState.FAILED);

		manager.onPackageOffer(offer(TARGET_VERSION));

		assertTrue(failed.await(10, TimeUnit.SECONDS));
		final UpgradeEvent last = events.get(events.size() - 1);
		assertEquals(UpgradeState.FAILED, last.state());
		assertTrue(last.errorMessage().contains("No runner"));
		assertFalse(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));
		// The staged file was cleaned up
		assertFalse(Files.exists(tempDir.resolve("staged.pkg")));
	}

	@Test
	void concurrentOfferShouldBeIgnoredWhileAnUpgradeIsInFlight() throws Exception {
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) ->
			launches.incrementAndGet()
		);
		final CountDownLatch restarting = expectState(manager, UpgradeState.RESTARTING);

		manager.onPackageOffer(offer(TARGET_VERSION));
		assertTrue(restarting.await(10, TimeUnit.SECONDS));

		final int eventCountAfterFirstUpgrade = events.size();
		manager.onPackageOffer(offer("3.11.00"));
		// Give the worker a moment to (not) process the second offer
		Thread.sleep(300);

		assertEquals(1, launches.get());
		assertEquals(eventCountAfterFirstUpgrade, events.size());
	}

	@Test
	void disabledConfigurationShouldRejectOffers() throws Exception {
		config = UpgradeConfig.builder().enabled(false).build();
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) ->
			launches.incrementAndGet()
		);
		final CountDownLatch failed = expectState(manager, UpgradeState.FAILED);

		manager.onPackageOffer(offer(TARGET_VERSION));

		assertTrue(failed.await(10, TimeUnit.SECONDS));
		assertEquals(0, launches.get());
		assertTrue(events.get(events.size() - 1).errorMessage().contains("disabled"));
	}

	@Test
	void sameVersionOfferWithDifferentIdentityShouldTriggerTheUpgrade() throws Exception {
		final UpgradeManager manager = newManager((transaction, stagedPackage, stagingDirectory) ->
			launches.incrementAndGet()
		);

		// First same-version offer: the unknown installed identity adopts the offered hash
		final CountDownLatch idle = expectState(manager, UpgradeState.IDLE);
		manager.onPackageOffer(offer(CURRENT_VERSION));
		assertTrue(idle.await(10, TimeUnit.SECONDS));
		assertEquals(0, launches.get());

		// Second same-version offer with a DIFFERENT identity hash: a hotfix must be installed
		final CountDownLatch restarting = expectState(manager, UpgradeState.RESTARTING);
		manager.onPackageOffer(
			new PackageOffer(
				UpgradeManager.PACKAGE_NAME,
				CURRENT_VERSION,
				"https://repo.example.com/metricshub.pkg",
				new byte[] { 1 },
				new byte[] { 9, 9 },
				Map.of()
			)
		);
		assertTrue(restarting.await(10, TimeUnit.SECONDS));
		assertEquals(1, launches.get());
	}

	@Test
	void upgradeSupportShouldFollowTheDeploymentKind() {
		assertTrue(newManager((t, p, s) -> {}).isPackageUpgradeSupported());

		final UpgradeManager archiveManager = new UpgradeManager(
			() -> CURRENT_VERSION,
			() -> config,
			tempDir,
			new FakeDownloader(),
			new AcceptingValidator(),
			new DeploymentDetector(command -> false),
			(t, p, s) -> {}
		);
		assertFalse(archiveManager.isPackageUpgradeSupported());
	}

	@Test
	void snapshotShouldStartIdleWithTheCurrentVersion() {
		final UpgradeManager manager = newManager((t, p, s) -> {});

		final UpgradeEvent snapshot = manager.getCurrentSnapshot();

		assertEquals(UpgradeState.IDLE, snapshot.state());
		assertEquals(CURRENT_VERSION, snapshot.currentVersion());
		// LocalOsHandler is exercised transitively by the detector; reference it to document the
		// platform-dependence of the detection tests
		assertNotNull(LocalOsHandler.getOS());
	}
}
