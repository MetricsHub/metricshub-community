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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Single-upgrade guard: an in-memory flag backed by a lock file in the staging directory, so a
 * second upgrade cannot start while one is being prepared or installed — even across an agent
 * restart while the detached installer is running.
 */
@Slf4j
public class UpgradeLock {

	/**
	 * Name of the lock file created in the staging directory.
	 */
	public static final String LOCK_FILE_NAME = "upgrade.lock";

	private final Path lockFile;
	private final AtomicBoolean held = new AtomicBoolean(false);

	/**
	 * Creates the lock in the given staging directory.
	 *
	 * @param stagingDirectory the upgrade staging directory
	 */
	public UpgradeLock(final Path stagingDirectory) {
		this.lockFile = stagingDirectory.resolve(LOCK_FILE_NAME);
	}

	/**
	 * Attempts to acquire the lock.
	 *
	 * @return {@code true} when the lock was acquired
	 */
	public boolean tryAcquire() {
		if (!held.compareAndSet(false, true)) {
			return false;
		}
		try {
			Files.createDirectories(lockFile.getParent());
			Files.createFile(lockFile);
			return true;
		} catch (IOException e) {
			held.set(false);
			log.warn("Cannot acquire the upgrade lock {}: {}", lockFile, e.getMessage());
			return false;
		}
	}

	/**
	 * Releases the lock (in memory and on disk).
	 */
	public void release() {
		held.set(false);
		try {
			Files.deleteIfExists(lockFile);
		} catch (IOException e) {
			log.warn("Cannot release the upgrade lock {}: {}", lockFile, e.getMessage());
		}
	}

	/**
	 * Releases a lock file left behind by a previous process (startup reconciliation).
	 */
	public void releaseStale() {
		release();
	}
}
