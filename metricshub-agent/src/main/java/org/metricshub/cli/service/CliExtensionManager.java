package org.metricshub.cli.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.extension.ExtensionManager;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CliExtensionManager {

	private static final ExtensionManager EXTENSION_MANAGER = ConfigHelper.loadExtensionManager();

	/**
	 * How long the CLI exit waits for the extensions to release their resources.
	 */
	private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

	static {
		// The CLIs end with System.exit(): release what the extensions keep open between operations
		// (e.g. pooled WinRM clients and their remote shells) instead of leaving it on the hosts. Bounded,
		// so that a host that stopped answering cannot hold the exit (or a Ctrl+C) for minutes.
		Runtime.getRuntime().addShutdownHook(
			new Thread(() -> {
				try {
					Thread.startVirtualThread(EXTENSION_MANAGER::close).join(CLOSE_TIMEOUT);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			})
		);
	}

	/**
	 * Get the extension manager singleton instance.
	 *
	 * @return the {@link ExtensionManager} instance.
	 */
	public static ExtensionManager getExtensionManagerSingleton() {
		return EXTENSION_MANAGER;
	}
}
