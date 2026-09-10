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
import org.metricshub.agent.upgrade.transaction.UpgradeTransaction;

/**
 * Launches the detached upgrade runner that stops the agent service, installs the staged package
 * and restarts the service. The runner must survive the agent process being stopped:
 * platform-specific implementations use a systemd transient unit on Linux and a one-shot
 * scheduled task on Windows.
 */
public interface RunnerLauncher {
	/**
	 * Launches the detached upgrade runner. Returns once the runner process is successfully
	 * started; the installation itself completes after the agent has been stopped.
	 *
	 * @param transaction      the persisted upgrade transaction
	 * @param stagedPackage    the validated, staged package file
	 * @param stagingDirectory the upgrade staging directory (runner logs and markers live there)
	 * @throws Exception when the runner cannot be launched
	 */
	void launch(UpgradeTransaction transaction, Path stagedPackage, Path stagingDirectory) throws Exception;
}
