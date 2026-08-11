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
		// The PowerShell invocation is a single argument carrying every runner parameter
		final String powershell = create.get(create.size() - 1);
		assertTrue(powershell.contains("-ExecutionPolicy Bypass"));
		assertTrue(powershell.contains("-Sha256 cafebabe"));
		assertTrue(powershell.contains("-Service \"MetricsHub Community\""));
		assertTrue(powershell.contains("-SignatureSubjectContains \"MetricsHub\""));
		assertTrue(powershell.contains(staging.resolve(WindowsScheduledTaskRunnerLauncher.SCRIPT_NAME).toString()));

		assertEquals(List.of("schtasks", "/Run", "/TN", WindowsScheduledTaskRunnerLauncher.TASK_NAME), commands.get(1));

		// The script was staged out of the install tree, which msiexec is about to replace
		assertTrue(Files.isRegularFile(staging.resolve(WindowsScheduledTaskRunnerLauncher.SCRIPT_NAME)));
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
