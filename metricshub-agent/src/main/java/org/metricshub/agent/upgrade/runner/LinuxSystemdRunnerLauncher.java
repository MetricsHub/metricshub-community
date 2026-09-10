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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.upgrade.UpgradeException;
import org.metricshub.agent.upgrade.VersionHelper;
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

/**
 * Launches the detached upgrade runner on Linux as a systemd transient one-shot unit
 * ({@code systemd-run --unit=metricshub-upgrade-&lt;id&gt; --collect}). The transient unit lives in
 * its own cgroup, so it survives the agent service being stopped mid-installation.
 */
@Slf4j
public class LinuxSystemdRunnerLauncher implements RunnerLauncher {

	/**
	 * Name of the shipped runner script and its staged copy.
	 */
	static final String SCRIPT_NAME = "metricshub-upgrade-runner.sh";

	private final Supplier<Path> runnerScriptDirectorySupplier;
	private final CommandExecutor commandExecutor;
	private final String serviceUnit;
	private final Supplier<Boolean> systemdAvailable;
	private final Supplier<Boolean> runningAsRoot;

	/**
	 * Creates the launcher with production wiring.
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner script
	 * @param serviceUnit                   the systemd unit of the running edition, resolved by
	 *                                      {@link ServiceNameResolver}
	 */
	public LinuxSystemdRunnerLauncher(final Supplier<Path> runnerScriptDirectorySupplier, final String serviceUnit) {
		this(
			runnerScriptDirectorySupplier,
			new ProcessCommandExecutor(),
			serviceUnit,
			() -> Files.exists(Path.of("/run/systemd/system")),
			() -> "0".equals(System.getProperty("metricshub.test.uid", String.valueOf(nativeUid())))
		);
	}

	/**
	 * Creates the launcher with caller-provided collaborators (used by tests).
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner script
	 * @param commandExecutor               runs the {@code systemd-run} launcher command
	 * @param serviceUnit                   the systemd service unit of the agent
	 * @param systemdAvailable              whether systemd is available
	 * @param runningAsRoot                 whether the agent runs as root
	 */
	LinuxSystemdRunnerLauncher(
		final Supplier<Path> runnerScriptDirectorySupplier,
		final CommandExecutor commandExecutor,
		final String serviceUnit,
		final Supplier<Boolean> systemdAvailable,
		final Supplier<Boolean> runningAsRoot
	) {
		this.runnerScriptDirectorySupplier = runnerScriptDirectorySupplier;
		this.commandExecutor = commandExecutor;
		this.serviceUnit = serviceUnit;
		this.systemdAvailable = systemdAvailable;
		this.runningAsRoot = runningAsRoot;
	}

	@Override
	public void launch(final UpgradeTransaction transaction, final Path stagedPackage, final Path stagingDirectory)
		throws Exception {
		if (!Boolean.TRUE.equals(systemdAvailable.get())) {
			throw new UpgradeException("Automatic upgrade requires systemd, which is not available on this host");
		}
		if (!Boolean.TRUE.equals(runningAsRoot.get())) {
			throw new UpgradeException("Automatic upgrade requires the MetricsHub agent to run as root");
		}

		final Path stagedScript = RunnerScripts.stageScript(
			runnerScriptDirectorySupplier.get().resolve(SCRIPT_NAME),
			stagingDirectory.resolve(SCRIPT_NAME)
		);

		final List<String> command = buildCommand(transaction, stagedPackage, stagingDirectory, stagedScript);
		log.info("Launching the detached upgrade runner: {}", String.join(" ", command));
		final int exitCode = commandExecutor.run(command);
		if (exitCode != 0) {
			throw new UpgradeException("systemd-run failed to launch the upgrade runner (exit code " + exitCode + ")");
		}
	}

	/**
	 * Builds the {@code systemd-run} command line launching the runner script as a transient
	 * one-shot unit.
	 *
	 * @param transaction      the upgrade transaction (supplies the unit id and package type)
	 * @param stagedPackage    the staged package file
	 * @param stagingDirectory the staging directory
	 * @param stagedScript     the staged runner script
	 * @return the command and its arguments
	 * @throws UpgradeException when the deployment kind is not a Linux package
	 */
	List<String> buildCommand(
		final UpgradeTransaction transaction,
		final Path stagedPackage,
		final Path stagingDirectory,
		final Path stagedScript
	) throws UpgradeException {
		final String packageType = packageType(transaction);
		final List<String> command = new ArrayList<>();
		command.add("systemd-run");
		command.add("--unit=metricshub-upgrade-" + transaction.getUpgradeId());
		command.add("--collect");
		// Return once the unit is queued: without --no-block, systemd-run waits for the oneshot
		// start job to complete — i.e. until the runner exits — so the runner would stop the agent
		// while launch() is still blocked (and the executor's timeout would fail the upgrade while
		// the unit keeps running)
		command.add("--no-block");
		command.add("--property=Type=oneshot");
		// Without an explicit timeout the transient unit inherits the manager's
		// DefaultTimeoutStartSec (commonly 90s) and systemd would kill the runner mid-installation,
		// after it already stopped the agent
		command.add("--property=TimeoutStartSec=" + installTimeoutSeconds(transaction));
		command.add("/bin/sh");
		command.add(stagedScript.toAbsolutePath().toString());
		command.add("--package");
		command.add(stagedPackage.toAbsolutePath().toString());
		command.add("--sha256");
		command.add(transaction.getSha256());
		command.add("--type");
		command.add(packageType);
		command.add("--service");
		command.add(serviceUnit);
		command.add("--staging");
		command.add(stagingDirectory.toAbsolutePath().toString());
		command.add("--mode");
		command.add(VersionHelper.installMode(transaction.getFromVersion(), transaction.getToVersion()));
		return command;
	}

	/**
	 * Returns the installation timeout to apply to the transient unit, falling back to the
	 * configured default when the transaction carries none.
	 *
	 * @param transaction the upgrade transaction
	 * @return the timeout in seconds
	 */
	private static long installTimeoutSeconds(final UpgradeTransaction transaction) {
		final long timeout = transaction.getInstallTimeoutSeconds();
		return timeout > 0 ? timeout : UpgradeConfig.DEFAULT_INSTALL_TIMEOUT;
	}

	/**
	 * Maps the transaction deployment kind to the runner's {@code --type} argument.
	 *
	 * @param transaction the upgrade transaction
	 * @return {@code deb} or {@code rpm}
	 * @throws UpgradeException when the deployment kind is not a Linux package
	 */
	private static String packageType(final UpgradeTransaction transaction) throws UpgradeException {
		final String kind =
			transaction.getDeploymentKind() == null ? "" : transaction.getDeploymentKind().toLowerCase(Locale.ROOT);
		if (!Set.of("deb", "rpm").contains(kind)) {
			throw new UpgradeException("The systemd upgrade runner only supports deb and rpm packages, not " + kind);
		}
		return kind;
	}

	/**
	 * Returns the effective user id, or -1 when it cannot be determined (e.g. non-POSIX).
	 *
	 * @return the effective user id, or -1
	 */
	private static long nativeUid() {
		try {
			return (long) Class.forName("com.sun.security.auth.module.UnixSystem")
				.getMethod("getUid")
				.invoke(Class.forName("com.sun.security.auth.module.UnixSystem").getDeclaredConstructor().newInstance());
		} catch (Exception e) {
			return -1;
		}
	}
}
