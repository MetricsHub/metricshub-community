package org.metricshub.agent.upgrade.runner;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.VersionHelper;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

/**
 * Launches the detached upgrade runner on Windows as a one-shot Scheduled Task running as SYSTEM.
 * A scheduled task runs under the Task Scheduler service, outside the agent service's process
 * tree, so it survives NSSM terminating that tree when the MetricsHub service is stopped by
 * msiexec.
 */
@Slf4j
public class WindowsScheduledTaskRunnerLauncher implements RunnerLauncher {

	/**
	 * Name of the one-shot scheduled task.
	 */
	static final String TASK_NAME = "MetricsHub Upgrade";

	/**
	 * Name of the shipped runner script and its staged copy.
	 */
	static final String SCRIPT_NAME = "metricshub-upgrade-runner.ps1";

	/**
	 * Name of the generated wrapper the scheduled task actually runs. {@code schtasks /Create}
	 * limits its {@code /TR} value to 262 characters, which the full PowerShell invocation exceeds,
	 * so the parameters live in this wrapper instead.
	 */
	static final String LAUNCH_WRAPPER_NAME = "metricshub-upgrade-launch.cmd";

	private final Supplier<Path> runnerScriptDirectorySupplier;
	private final CommandExecutor commandExecutor;
	private final String serviceName;
	private final String signatureSubjectContains;

	/**
	 * Creates the launcher with production wiring.
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner script
	 * @param serviceName                   the Windows service name of the running edition, resolved
	 *                                      by {@link ServiceNameResolver}
	 * @param signatureSubjectContains      the required substring of the MSI Authenticode signer
	 */
	public WindowsScheduledTaskRunnerLauncher(
		final Supplier<Path> runnerScriptDirectorySupplier,
		final String serviceName,
		final String signatureSubjectContains
	) {
		this(runnerScriptDirectorySupplier, new ProcessCommandExecutor(), serviceName, signatureSubjectContains);
	}

	/**
	 * Creates the launcher with caller-provided collaborators (used by tests).
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner script
	 * @param commandExecutor               runs the {@code schtasks} launcher commands
	 * @param serviceName                   the Windows service name of the agent
	 * @param signatureSubjectContains      the required substring of the MSI Authenticode signer
	 */
	WindowsScheduledTaskRunnerLauncher(
		final Supplier<Path> runnerScriptDirectorySupplier,
		final CommandExecutor commandExecutor,
		final String serviceName,
		final String signatureSubjectContains
	) {
		this.runnerScriptDirectorySupplier = runnerScriptDirectorySupplier;
		this.commandExecutor = commandExecutor;
		this.serviceName = serviceName;
		this.signatureSubjectContains = signatureSubjectContains;
	}

	@Override
	public void launch(final UpgradeTransaction transaction, final Path stagedPackage, final Path stagingDirectory)
		throws Exception {
		final Path stagedScript = RunnerScripts.stageScript(
			runnerScriptDirectorySupplier.get().resolve(SCRIPT_NAME),
			stagingDirectory.resolve(SCRIPT_NAME)
		);

		final Path wrapper = writeLaunchWrapper(transaction, stagedPackage, stagingDirectory, stagedScript);
		final List<String> createCommand = buildCreateCommand(wrapper);
		log.info("Registering the detached upgrade scheduled task.");
		final int createExit = commandExecutor.run(createCommand);
		if (createExit != 0) {
			throw new UpgradeException("schtasks failed to create the upgrade task (exit code " + createExit + ")");
		}

		final int runExit = commandExecutor.run(List.of("schtasks", "/Run", "/TN", TASK_NAME));
		if (runExit != 0) {
			throw new UpgradeException("schtasks failed to start the upgrade task (exit code " + runExit + ")");
		}
	}

	/**
	 * Writes the wrapper script the scheduled task runs. Every runner parameter lives here rather
	 * than in the {@code /TR} value, which {@code schtasks} limits to 262 characters — the full
	 * PowerShell invocation (script, package and staging paths plus a 64-character hash) exceeds
	 * that on ordinary installations.
	 *
	 * @param transaction      the upgrade transaction (supplies the expected hash)
	 * @param stagedPackage    the staged MSI file
	 * @param stagingDirectory the staging directory
	 * @param stagedScript     the staged runner script
	 * @return the wrapper path
	 * @throws IOException when the wrapper cannot be written
	 */
	Path writeLaunchWrapper(
		final UpgradeTransaction transaction,
		final Path stagedPackage,
		final Path stagingDirectory,
		final Path stagedScript
	) throws IOException {
		final String powershell =
			"powershell -NoProfile -ExecutionPolicy Bypass -File \"" +
			stagedScript.toAbsolutePath() +
			"\" -Package \"" +
			stagedPackage.toAbsolutePath() +
			"\" -Sha256 " +
			transaction.getSha256() +
			" -Service \"" +
			serviceName +
			"\" -Staging \"" +
			stagingDirectory.toAbsolutePath() +
			"\" -SignatureSubjectContains \"" +
			signatureSubjectContains +
			"\" -Mode " +
			VersionHelper.installMode(transaction.getFromVersion(), transaction.getToVersion()) +
			" -InstallTimeoutSeconds " +
			installTimeoutSeconds(transaction);

		final Path wrapper = stagingDirectory.resolve(LAUNCH_WRAPPER_NAME);
		Files.createDirectories(stagingDirectory);
		Files.writeString(wrapper, "@echo off\r\n" + powershell + "\r\n", StandardCharsets.US_ASCII);
		return wrapper;
	}

	/**
	 * Returns the installation timeout to hand to the runner, falling back to the configured
	 * default when the transaction carries none.
	 *
	 * @param transaction the upgrade transaction
	 * @return the timeout in seconds
	 */
	private static long installTimeoutSeconds(final UpgradeTransaction transaction) {
		final long timeout = transaction.getInstallTimeoutSeconds();
		return timeout > 0 ? timeout : UpgradeConfig.DEFAULT_INSTALL_TIMEOUT;
	}

	/**
	 * Builds the {@code schtasks /Create} command registering the one-shot SYSTEM task that runs
	 * the wrapper.
	 *
	 * @param wrapper the generated wrapper script
	 * @return the command and its arguments
	 */
	List<String> buildCreateCommand(final Path wrapper) {
		final List<String> command = new ArrayList<>();
		command.add("schtasks");
		command.add("/Create");
		command.add("/TN");
		command.add(TASK_NAME);
		command.add("/F");
		command.add("/RU");
		command.add("SYSTEM");
		command.add("/RL");
		command.add("HIGHEST");
		command.add("/SC");
		command.add("ONCE");
		command.add("/ST");
		command.add("00:00");
		command.add("/TR");
		// Quoted so a path containing spaces stays a single token for schtasks
		command.add("\"" + wrapper.toAbsolutePath() + "\"");
		return command;
	}
}
