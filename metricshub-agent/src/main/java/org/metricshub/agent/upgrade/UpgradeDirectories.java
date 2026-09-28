package org.metricshub.agent.upgrade;

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
import java.nio.file.Paths;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Resolves the upgrade staging directory, which must survive the package upgrade itself:
 * {@code %ProgramData%/MetricsHub/upgrade} on Windows and {@code <install>/lib/upgrade} on other
 * platforms (unowned files under the installation tree are preserved by dpkg/rpm across
 * upgrades).
 */
public class UpgradeDirectories {

	/**
	 * Name of the upgrade staging directory.
	 */
	public static final String UPGRADE_DIRECTORY_NAME = "upgrade";

	private UpgradeDirectories() {}

	/**
	 * Resolves the upgrade staging directory without creating it.
	 *
	 * @return the staging directory path
	 */
	public static Path resolveStagingDirectory() {
		if (LocalOsHandler.isWindows()) {
			return ConfigHelper.getProgramDataPath()
				.map(programData -> Paths.get(programData, AgentConstants.PRODUCT_WIN_DIR_NAME, UPGRADE_DIRECTORY_NAME))
				.orElseGet(() -> ConfigHelper.getSubPath(UPGRADE_DIRECTORY_NAME));
		}
		return ConfigHelper.getSubPath(UPGRADE_DIRECTORY_NAME);
	}
}
