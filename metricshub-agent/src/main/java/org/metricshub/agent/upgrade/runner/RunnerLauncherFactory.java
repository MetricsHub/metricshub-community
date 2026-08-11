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
import java.util.function.Supplier;
import org.metricshub.agent.helper.ConfigHelper;

/**
 * Selects the detached runner launcher matching the deployment kind. Package-manager deployments
 * get the platform launcher (systemd on Linux, scheduled task on Windows); archive and container
 * deployments get the {@link UnsupportedRunnerLauncher} (they never advertise the OpAMP
 * {@code AcceptsPackages} capability, so this is a defensive fallback).
 */
public class RunnerLauncherFactory {

	/**
	 * Directory holding the shipped runner scripts, installed through the jpackage
	 * {@code --app-content} option.
	 */
	static final String RUNNER_DIRECTORY_NAME = "upgrade-runner";

	private final Supplier<Path> runnerScriptDirectorySupplier;

	/**
	 * Creates the factory, resolving the runner-script directory relative to the installation
	 * tree.
	 */
	public RunnerLauncherFactory() {
		this(() -> ConfigHelper.getSubPath(RUNNER_DIRECTORY_NAME));
	}

	/**
	 * Creates the factory with a caller-provided runner-script directory supplier (used by tests).
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner scripts
	 */
	public RunnerLauncherFactory(final Supplier<Path> runnerScriptDirectorySupplier) {
		this.runnerScriptDirectorySupplier = runnerScriptDirectorySupplier;
	}

	/**
	 * Returns the launcher matching the given deployment kind.
	 *
	 * @param deploymentKind              the detected deployment kind
	 * @param msiSignatureSubjectContains the required substring of the MSI Authenticode signer
	 *                                    (Windows only)
	 * @return the matching runner launcher
	 */
	public RunnerLauncher forDeployment(final DeploymentKind deploymentKind, final String msiSignatureSubjectContains) {
		return switch (deploymentKind) {
			case DEB, RPM -> new LinuxSystemdRunnerLauncher(runnerScriptDirectorySupplier);
			case MSI -> new WindowsScheduledTaskRunnerLauncher(runnerScriptDirectorySupplier, msiSignatureSubjectContains);
			default -> new UnsupportedRunnerLauncher();
		};
	}
}
