package org.metricshub.cli.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.extension.ExtensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CliExtensionManager {

	private static final ExtensionManager EXTENSION_MANAGER = ConfigHelper.loadExtensionManager();

	/**
	 * Get the extension manager singleton instance.
	 *
	 * @return the {@link ExtensionManager} instance.
	 */
	public static ExtensionManager getExtensionManagerSingleton() {
		return EXTENSION_MANAGER;
	}
}
