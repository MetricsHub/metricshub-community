package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.UpgradeState;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

class LinuxSystemdRunnerLauncherTest {

	@TempDir
	Path tempDir;

	private UpgradeTransaction transaction(final String kind) {
		return transaction(kind, "3.9.05", "3.10.00");
	}

	private UpgradeTransaction transaction(final String kind, final String fromVersion, final String toVersion) {
		return UpgradeTransaction.builder()
			.upgradeId("abc-123")
			.packageName("metricshub")
			.fromVersion(fromVersion)
			.toVersion(toVersion)
			.sha256("deadbeef")
			.deploymentKind(kind)
			.state(UpgradeState.INSTALLING)
			.installTimeoutSeconds(1800)
			.build();
	}

	private Path shippedScriptDir() throws Exception {
		final Path dir = Files.createDirectories(tempDir.resolve("upgrade-runner"));
		Files.writeString(dir.resolve(LinuxSystemdRunnerLauncher.SCRIPT_NAME), "#!/bin/sh\n");
		return dir;
	}

	@Test
	void launchShouldStageTheScriptAndBuildTheSystemdRunCommand() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging"));
		final Path pkg = Files.writeString(staging.resolve("metricshub.deb"), "pkg");
		final AtomicReference<List<String>> captured = new AtomicReference<>();

		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> {
				captured.set(command);
				return 0;
			},
			"metricshub-community-service.service",
			() -> true,
			() -> true
		);

		launcher.launch(transaction("deb"), pkg, staging);

		final List<String> command = captured.get();
		assertEquals("systemd-run", command.get(0));
		assertTrue(command.contains("--unit=metricshub-upgrade-abc-123"));
		assertTrue(command.contains("--collect"));
		assertTrue(command.contains("--property=Type=oneshot"));
		assertTrue(command.contains("deb"));
		assertTrue(command.contains("metricshub-community-service.service"));
		assertTrue(command.contains("deadbeef"));
		// The transient unit must not inherit the manager's DefaultTimeoutStartSec (commonly 90s)
		assertTrue(command.contains("--property=TimeoutStartSec=1800"));
		// A newer version is a plain install
		assertEquals("install", command.get(command.indexOf("--mode") + 1));
		// The script was staged out of the install tree and made executable
		final Path stagedScript = staging.resolve(LinuxSystemdRunnerLauncher.SCRIPT_NAME);
		assertTrue(Files.isRegularFile(stagedScript));
		assertTrue(command.contains(stagedScript.toAbsolutePath().toString()));
	}

	@Test
	void launchShouldFailWhenSystemdIsUnavailable() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> 0,
			"svc",
			() -> false,
			() -> true
		);

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			launcher.launch(transaction("deb"), tempDir.resolve("p.deb"), tempDir)
		);
		assertTrue(failure.getMessage().contains("systemd"));
	}

	@Test
	void launchShouldFailWhenNotRoot() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> 0,
			"svc",
			() -> true,
			() -> false
		);

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			launcher.launch(transaction("deb"), tempDir.resolve("p.deb"), tempDir)
		);
		assertTrue(failure.getMessage().contains("root"));
	}

	@Test
	void launchShouldFailWhenSystemdRunReturnsNonZero() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging"));
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> 1,
			"svc",
			() -> true,
			() -> true
		);

		assertThrows(UpgradeException.class, () -> launcher.launch(transaction("rpm"), staging.resolve("p.rpm"), staging));
	}

	@Test
	void installModeShouldFollowTheVersionComparison() {
		assertEquals("install", LinuxSystemdRunnerLauncher.installMode(transaction("deb", "3.9.05", "3.10.00")));
		// A same-version hotfix must be reinstalled, otherwise the package manager changes nothing
		assertEquals("reinstall", LinuxSystemdRunnerLauncher.installMode(transaction("deb", "3.10.00", "3.10.00")));
		assertEquals("downgrade", LinuxSystemdRunnerLauncher.installMode(transaction("deb", "3.10.00", "3.9.05")));
	}

	@Test
	void enterpriseUnitShouldBeHonored() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging-ent"));
		final AtomicReference<List<String>> captured = new AtomicReference<>();
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> {
				captured.set(command);
				return 0;
			},
			"metricshub-enterprise-service.service",
			() -> true,
			() -> true
		);

		launcher.launch(transaction("rpm"), staging.resolve("p.rpm"), staging);

		assertTrue(captured.get().contains("metricshub-enterprise-service.service"));
	}

	@Test
	void buildCommandShouldRejectNonLinuxPackages() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> shippedDir,
			command -> 0,
			"svc",
			() -> true,
			() -> true
		);

		assertThrows(UpgradeException.class, () ->
			launcher.buildCommand(transaction("msi"), tempDir.resolve("p.msi"), tempDir, tempDir.resolve("r.sh"))
		);
	}

	@Test
	void missingScriptShouldFail() {
		final LinuxSystemdRunnerLauncher launcher = new LinuxSystemdRunnerLauncher(
			() -> tempDir.resolve("does-not-exist"),
			command -> 0,
			"svc",
			() -> true,
			() -> true
		);

		assertThrows(UpgradeException.class, () -> launcher.launch(transaction("deb"), tempDir.resolve("p.deb"), tempDir));
	}
}
