package org.metricshub.agent.upgrade.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.UpgradeState;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

class WindowsScheduledTaskRunnerLauncherTest {

	@TempDir
	Path tempDir;

	private UpgradeTransaction transaction() {
		return UpgradeTransaction.builder()
			.upgradeId("win-1")
			.packageName("metricshub")
			.toVersion("3.10.00")
			.sha256("cafebabe")
			.deploymentKind("MSI")
			.state(UpgradeState.INSTALLING)
			.build();
	}

	private Path shippedScriptDir() throws Exception {
		final Path dir = Files.createDirectories(tempDir.resolve("upgrade-runner"));
		Files.writeString(dir.resolve(WindowsScheduledTaskRunnerLauncher.SCRIPT_NAME), "# runner");
		return dir;
	}

	@Test
	void launchShouldCreateAndRunTheScheduledTask() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging"));
		final Path msi = Files.writeString(staging.resolve("metricshub.msi"), "pkg");
		final List<List<String>> commands = new ArrayList<>();

		final WindowsScheduledTaskRunnerLauncher launcher = new WindowsScheduledTaskRunnerLauncher(
			() -> shippedDir,
			command -> {
				commands.add(command);
				return 0;
			},
			"MetricsHub Community",
			"MetricsHub"
		);

		launcher.launch(transaction(), msi, staging);

		assertEquals(2, commands.size());
		final List<String> create = commands.get(0);
		assertEquals("schtasks", create.get(0));
		assertTrue(create.contains("/Create"));
		assertTrue(create.contains("SYSTEM"));
		assertTrue(create.contains("HIGHEST"));
		assertTrue(create.contains("ONCE"));

		// schtasks limits the /TR value to 262 characters, so it points at the generated wrapper
		// instead of carrying the whole PowerShell invocation
		final String taskRun = create.get(create.size() - 1);
		assertTrue(taskRun.length() < 262, "The /TR value must stay well under the schtasks limit");
		assertTrue(taskRun.contains(WindowsScheduledTaskRunnerLauncher.LAUNCH_WRAPPER_NAME));

		// The wrapper carries every runner parameter
		final Path wrapper = staging.resolve(WindowsScheduledTaskRunnerLauncher.LAUNCH_WRAPPER_NAME);
		assertTrue(Files.isRegularFile(wrapper));
		final String wrapperContent = Files.readString(wrapper);
		assertTrue(wrapperContent.contains("-ExecutionPolicy Bypass"));
		assertTrue(wrapperContent.contains("-Sha256 cafebabe"));
		assertTrue(wrapperContent.contains("-Service \"MetricsHub Community\""));
		assertTrue(wrapperContent.contains("-SignatureSubjectContains \"MetricsHub\""));
		assertTrue(wrapperContent.contains("-Mode install"));
		// The transaction carries no timeout: the configured default applies
		assertTrue(wrapperContent.contains("-InstallTimeoutSeconds 1800"));
		assertTrue(wrapperContent.contains(staging.resolve(WindowsScheduledTaskRunnerLauncher.SCRIPT_NAME).toString()));

		assertEquals(List.of("schtasks", "/Run", "/TN", WindowsScheduledTaskRunnerLauncher.TASK_NAME), commands.get(1));

		// The script was staged out of the install tree, which msiexec is about to replace
		assertTrue(Files.isRegularFile(staging.resolve(WindowsScheduledTaskRunnerLauncher.SCRIPT_NAME)));
	}

	@Test
	void enterpriseServiceNameShouldBeHonored() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging-ent"));
		final WindowsScheduledTaskRunnerLauncher launcher = new WindowsScheduledTaskRunnerLauncher(
			() -> shippedDir,
			command -> 0,
			"MetricsHub Enterprise",
			"MetricsHub"
		);

		launcher.launch(transaction(), staging.resolve("p.msi"), staging);

		final String wrapperContent = Files.readString(
			staging.resolve(WindowsScheduledTaskRunnerLauncher.LAUNCH_WRAPPER_NAME)
		);
		assertTrue(wrapperContent.contains("-Service \"MetricsHub Enterprise\""));
	}

	@Test
	void sameVersionOfferShouldRequestExplicitReinstall() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final Path staging = Files.createDirectories(tempDir.resolve("staging-hotfix"));
		final WindowsScheduledTaskRunnerLauncher launcher = new WindowsScheduledTaskRunnerLauncher(
			() -> shippedDir,
			command -> 0,
			"MetricsHub Community",
			"MetricsHub"
		);
		final UpgradeTransaction hotfix = UpgradeTransaction.builder()
			.upgradeId("win-2")
			.packageName("metricshub")
			.fromVersion("3.10.00")
			.toVersion("3.10.00")
			.sha256("cafebabe")
			.deploymentKind("MSI")
			.state(UpgradeState.INSTALLING)
			.installTimeoutSeconds(600)
			.build();

		launcher.launch(hotfix, staging.resolve("p.msi"), staging);

		final String wrapperContent = Files.readString(
			staging.resolve(WindowsScheduledTaskRunnerLauncher.LAUNCH_WRAPPER_NAME)
		);
		// A same-version hotfix must be applied with explicit reinstall semantics
		assertTrue(wrapperContent.contains("-Mode reinstall"));
		assertTrue(wrapperContent.contains("-InstallTimeoutSeconds 600"));
	}

	@Test
	void taskCreationFailureShouldFailTheLaunch() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final WindowsScheduledTaskRunnerLauncher launcher = new WindowsScheduledTaskRunnerLauncher(
			() -> shippedDir,
			command -> 1,
			"svc",
			"MetricsHub"
		);

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			launcher.launch(transaction(), tempDir.resolve("p.msi"), tempDir)
		);
		assertTrue(failure.getMessage().contains("create"));
	}

	@Test
	void taskStartFailureShouldFailTheLaunch() throws Exception {
		final Path shippedDir = shippedScriptDir();
		final List<List<String>> commands = new ArrayList<>();
		final WindowsScheduledTaskRunnerLauncher launcher = new WindowsScheduledTaskRunnerLauncher(
			() -> shippedDir,
			command -> {
				commands.add(command);
				return commands.size() == 1 ? 0 : 1;
			},
			"svc",
			"MetricsHub"
		);

		final UpgradeException failure = assertThrows(UpgradeException.class, () ->
			launcher.launch(transaction(), tempDir.resolve("p.msi"), tempDir)
		);
		assertTrue(failure.getMessage().contains("start"));
	}
}
