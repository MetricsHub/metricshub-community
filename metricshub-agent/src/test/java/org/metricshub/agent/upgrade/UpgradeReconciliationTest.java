package org.metricshub.agent.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.api.UpgradeEvent;
import org.metricshub.agent.upgrade.download.PackageDownloader;
import org.metricshub.agent.upgrade.runner.DeploymentDetector;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;
import org.metricshub.agent.upgrade.transaction.UpgradeTransactionStore;
import org.metricshub.agent.upgrade.validate.PackageValidator;

class UpgradeReconciliationTest {

	@TempDir
	Path tempDir;

	private final List<UpgradeEvent> events = new CopyOnWriteArrayList<>();

	private UpgradeManager newManager(final String runningVersion) {
		final UpgradeManager manager = new UpgradeManager(
			() -> runningVersion,
			() -> UpgradeConfig.builder().build(),
			tempDir,
			new PackageDownloader(),
			new PackageValidator(),
			new DeploymentDetector(command -> true),
			(transaction, stagedPackage, stagingDirectory) -> {}
		);
		manager.setStatusListener(events::add);
		return manager;
	}

	private UpgradeTransaction pendingTransaction(final UpgradeState state, final Path stagedFile) throws IOException {
		final UpgradeTransaction transaction = UpgradeTransaction.builder()
			.upgradeId("pending")
			.packageName(UpgradeManager.PACKAGE_NAME)
			.fromVersion("3.9.05")
			.toVersion("3.10.00")
			.packageHash("0506")
			.state(state)
			.createdAt(System.currentTimeMillis())
			.installStartedAt(System.currentTimeMillis())
			.installTimeoutSeconds(1800)
			.packageFile(stagedFile != null ? stagedFile.toString() : null)
			.build();
		new UpgradeTransactionStore(tempDir).write(transaction);
		return transaction;
	}

	@Test
	void versionMatchAfterRestartShouldSucceed() throws IOException {
		final Path stagedFile = tempDir.resolve("staged.pkg");
		Files.writeString(stagedFile, "package");
		pendingTransaction(UpgradeState.RESTARTING, stagedFile);
		Files.createFile(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME));

		final UpgradeManager manager = newManager("3.10.00");
		manager.reconcileOnStartup();

		final UpgradeEvent verdict = events.get(events.size() - 1);
		assertEquals(UpgradeState.SUCCEEDED, verdict.state());
		assertEquals("3.10.00", verdict.currentVersion());
		org.junit.jupiter.api.Assertions.assertArrayEquals(
			new byte[] { 5, 6 },
			verdict.targetHash(),
			"The verdict must carry the offered package hash persisted in the transaction"
		);

		// Transaction archived, staged file cleaned, lock released
		assertNull(new UpgradeTransactionStore(tempDir).read());
		assertFalse(Files.exists(stagedFile));
		assertFalse(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));

		// The verdict is part of the snapshot for the first OpAMP report
		assertEquals(UpgradeState.SUCCEEDED, manager.getCurrentSnapshot().state());
	}

	@Test
	void versionMismatchAfterRestartShouldFail() throws IOException {
		pendingTransaction(UpgradeState.INSTALLING, null);

		final UpgradeManager manager = newManager("3.9.05");
		manager.reconcileOnStartup();

		final UpgradeEvent verdict = events.get(events.size() - 1);
		assertEquals(UpgradeState.FAILED, verdict.state());
		assertTrue(verdict.errorMessage().contains("3.10.00"));
		assertNull(new UpgradeTransactionStore(tempDir).read());
	}

	@Test
	void snapshotVersionShouldIgnoreQualifiers() throws IOException {
		pendingTransaction(UpgradeState.RESTARTING, null);

		final UpgradeManager manager = newManager("3.10.00-SNAPSHOT");
		manager.reconcileOnStartup();

		assertEquals(UpgradeState.SUCCEEDED, events.get(events.size() - 1).state());
	}

	@Test
	void sameVersionHotfixShouldRequireTheRunnerMarker() throws IOException {
		final UpgradeTransaction transaction = pendingTransaction(UpgradeState.RESTARTING, null);
		transaction.setFromVersion("3.10.00");
		transaction.setToVersion("3.10.00");
		new UpgradeTransactionStore(tempDir).write(transaction);

		// The running version is identical before and after: without the runner marker the
		// installation cannot be considered successful
		final UpgradeManager manager = newManager("3.10.00");
		manager.reconcileOnStartup();

		final UpgradeEvent verdict = events.get(events.size() - 1);
		assertEquals(UpgradeState.FAILED, verdict.state());
		assertTrue(verdict.errorMessage().contains("could not be verified"));
	}

	@Test
	void sameVersionHotfixShouldSucceedWithTheRunnerMarker() throws IOException {
		final UpgradeTransaction transaction = pendingTransaction(UpgradeState.RESTARTING, null);
		transaction.setFromVersion("3.10.00");
		transaction.setToVersion("3.10.00");
		new UpgradeTransactionStore(tempDir).write(transaction);
		Files.writeString(
			tempDir.resolve(org.metricshub.agent.upgrade.runner.RunnerMarkers.RESULT_FILE_NAME),
			"INSTALL_OK"
		);

		final UpgradeManager manager = newManager("3.10.00");
		manager.reconcileOnStartup();

		assertEquals(UpgradeState.SUCCEEDED, events.get(events.size() - 1).state());
		// The marker is cleared so it can never influence the next upgrade
		assertFalse(Files.exists(tempDir.resolve(org.metricshub.agent.upgrade.runner.RunnerMarkers.RESULT_FILE_NAME)));
	}

	@Test
	void preInstallInterruptionShouldFail() throws IOException {
		pendingTransaction(UpgradeState.DOWNLOADING, null);

		final UpgradeManager manager = newManager("3.9.05");
		manager.reconcileOnStartup();

		final UpgradeEvent verdict = events.get(events.size() - 1);
		assertEquals(UpgradeState.FAILED, verdict.state());
		assertTrue(verdict.errorMessage().contains("interrupted"));
	}

	@Test
	void terminalTransactionShouldBeRepublishedAndArchived() throws IOException {
		final UpgradeTransaction transaction = pendingTransaction(UpgradeState.SUCCEEDED, null);
		transaction.setState(UpgradeState.SUCCEEDED);
		new UpgradeTransactionStore(tempDir).write(transaction);

		final UpgradeManager manager = newManager("3.10.00");
		manager.reconcileOnStartup();

		assertEquals(UpgradeState.SUCCEEDED, events.get(events.size() - 1).state());
		assertNull(new UpgradeTransactionStore(tempDir).read());
	}

	@Test
	void missingTransactionShouldLeaveTheManagerIdle() {
		final UpgradeManager manager = newManager("3.9.05");
		manager.reconcileOnStartup();

		assertTrue(events.isEmpty());
		assertEquals(UpgradeState.IDLE, manager.getCurrentSnapshot().state());
	}

	@Test
	void staleLockWithoutTransactionShouldBeReleased() throws IOException {
		Files.createDirectories(tempDir);
		Files.createFile(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME));

		newManager("3.9.05").reconcileOnStartup();

		assertFalse(Files.exists(tempDir.resolve(UpgradeLock.LOCK_FILE_NAME)));
	}
}
