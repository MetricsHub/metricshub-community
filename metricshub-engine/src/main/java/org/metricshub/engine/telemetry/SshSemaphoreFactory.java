package org.metricshub.engine.telemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * A factory class for managing SSH semaphores associated with hostnames.
 */
public class SshSemaphoreFactory {

	private static final int MAX_PERMITS = 8;

	private static final SshSemaphoreFactory INSTANCE = new SshSemaphoreFactory();

	private final Map<String, Semaphore> sshSemaphores = new HashMap<>();

	/**
	 * Gets the Semaphore associated with the hostname, creating it if it does not exist.
	 *
	 * @param hostname The hostname for which to retrieve the Semaphore.
	 * @return The Semaphore associated with the hostname.
	 */
	public synchronized Semaphore createOrGetSempahore(final String hostname) {
		sshSemaphores.putIfAbsent(hostname, new Semaphore(MAX_PERMITS));
		return sshSemaphores.get(hostname);
	}

	/**
	 * Gets the static instance containing all semaphores
	 *
	 * @return the static instance containing all semaphores
	 */
	public static SshSemaphoreFactory getInstance() {
		return INSTANCE;
	}

	/**
	 * Removes the host-semaphore key-value pair for the specified hostname.
	 *
	 * @param hostname The hostname for which to delete the Semaphore.
	 */
	public void deleteSemaphore(String hostname) {
		sshSemaphores.remove(hostname);
	}
}
