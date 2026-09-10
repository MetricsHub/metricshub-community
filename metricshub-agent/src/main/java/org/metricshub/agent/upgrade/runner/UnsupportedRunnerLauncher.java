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
 * Placeholder launcher used until the platform-specific detached runners ship
 * (EPIC-OpAMP-04): downloading and validating packages already works end-to-end, and the
 * installation step fails with an explicit message.
 */
public class UnsupportedRunnerLauncher implements RunnerLauncher {

	@Override
	public void launch(final UpgradeTransaction transaction, final Path stagedPackage, final Path stagingDirectory)
		throws Exception {
		throw new UnsupportedOperationException(
			"The detached upgrade runner is not available yet on this platform; the package was downloaded and validated at " +
				stagedPackage
		);
	}
}
