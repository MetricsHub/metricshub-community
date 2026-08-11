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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

/**
 * Launches the detached upgrade runner on Windows as a one-shot Scheduled Task running as SYSTEM.
 * A scheduled task runs under the Task Scheduler service, outside the agent service's process
 * tree, so it survives NSSM terminating that tree when the "MetricsHub Community" service is
 * stopped by msiexec.
 */
@Slf4j
public class WindowsScheduledTaskRunnerLauncher implements RunnerLauncher {

	/**
	 * Default Windows service name of the MetricsHub agent.
	 */
	public static final String DEFAULT_SERVICE_NAME = "MetricsHub Community";

	/**
	 * Name of the one-shot scheduled task.
	 */
	static final String TASK_NAME = "MetricsHub Upgrade";

	/**
	 * Name of the shipped runner script and its staged copy.
	 */
	static final String SCRIPT_NAME = "metricshub-upgrade-runner.ps1";

	private final Supplier<Path> runnerScriptDirectorySupplier;
	private final CommandExecutor commandExecutor;
	private final String serviceName;
	private final String signatureSubjectContains;

	/**
	 * Creates the launcher with production wiring.
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner script
	 * @param signatureSubjectContains      the required substring of the MSI Authenticode signer
	 */
	public WindowsScheduledTaskRunnerLauncher(
		final Supplier<Path> runnerScriptDirectorySupplier,
		final String signatureSubjectContains
	) {
		this(runnerScriptDirectorySupplier, new ProcessCommandExecutor(), DEFAULT_SERVICE_NAME, signatureSubjectContains);
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

		final List<String> createCommand = buildCreateCommand(transaction, stagedPackage, stagingDirectory, stagedScript);
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
	 * Builds the {@code schtasks /Create} command registering the one-shot SYSTEM task that runs
	 * the PowerShell runner.
	 *
	 * @param transaction      the upgrade transaction (supplies the expected hash)
	 * @param stagedPackage    the staged MSI file
	 * @param stagingDirectory the staging directory
	 * @param stagedScript     the staged runner script
	 * @return the command and its arguments
	 */
	List<String> buildCreateCommand(
		final UpgradeTransaction transaction,
		final Path stagedPackage,
		final Path stagingDirectory,
		final Path stagedScript
	) {
		// The whole PowerShell invocation is one /TR argument; ProcessBuilder passes it as a single
		// argv element, so no manual quoting of the outer string is needed.
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
			"\"";

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
		command.add(powershell);
		return command;
	}
}
