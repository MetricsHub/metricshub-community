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
import org.metricshub.agent.config.UpgradeConfig;
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
	private final ServiceNameResolver serviceNameResolver;

	/**
	 * Creates the factory, resolving the runner-script directory relative to the installation
	 * tree.
	 */
	public RunnerLauncherFactory() {
		this(() -> ConfigHelper.getSubPath(RUNNER_DIRECTORY_NAME), new ServiceNameResolver());
	}

	/**
	 * Creates the factory with caller-provided collaborators (used by tests).
	 *
	 * @param runnerScriptDirectorySupplier supplies the directory holding the shipped runner scripts
	 * @param serviceNameResolver           resolves the service name of the running edition
	 */
	public RunnerLauncherFactory(
		final Supplier<Path> runnerScriptDirectorySupplier,
		final ServiceNameResolver serviceNameResolver
	) {
		this.runnerScriptDirectorySupplier = runnerScriptDirectorySupplier;
		this.serviceNameResolver = serviceNameResolver;
	}

	/**
	 * Returns the launcher matching the given deployment kind. The service the runner must stop and
	 * restart is resolved per edition: the configured {@code upgrade.serviceName} wins, otherwise
	 * it is discovered from the installed services.
	 *
	 * @param deploymentKind the detected deployment kind
	 * @param config         the upgrade configuration
	 * @return the matching runner launcher
	 */
	public RunnerLauncher forDeployment(final DeploymentKind deploymentKind, final UpgradeConfig config) {
		return switch (deploymentKind) {
			case DEB, RPM -> new LinuxSystemdRunnerLauncher(
				runnerScriptDirectorySupplier,
				serviceNameResolver.resolve(config.getServiceName())
			);
			case MSI -> new WindowsScheduledTaskRunnerLauncher(
				runnerScriptDirectorySupplier,
				serviceNameResolver.resolve(config.getServiceName()),
				config.getMsiSignatureSubjectContains()
			);
			default -> new UnsupportedRunnerLauncher();
		};
	}
}
