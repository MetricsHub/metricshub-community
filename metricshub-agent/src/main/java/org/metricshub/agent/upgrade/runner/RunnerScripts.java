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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.metricshub.agent.upgrade.UpgradeException;

/**
 * Copies the detached runner script from the (package-owned) installation tree into the staging
 * directory before launching it. Running the script from a copy is required on Windows, where the
 * MSI would otherwise fail to replace a file locked by the executing runner, and is harmless on
 * Linux.
 */
public class RunnerScripts {

	private RunnerScripts() {}

	/**
	 * Copies the shipped runner script to the staging directory, restricting its permissions to
	 * the owner on POSIX file systems.
	 *
	 * @param shippedScript the runner script shipped in the installation tree
	 * @param stagedScript  the destination path in the staging directory
	 * @return the staged script path
	 * @throws UpgradeException when the script is missing or cannot be staged
	 */
	public static Path stageScript(final Path shippedScript, final Path stagedScript) throws UpgradeException {
		if (!Files.isRegularFile(shippedScript)) {
			throw new UpgradeException("The upgrade runner script is missing: " + shippedScript);
		}
		try {
			Files.createDirectories(stagedScript.getParent());
			Files.copy(shippedScript, stagedScript, StandardCopyOption.REPLACE_EXISTING);
			restrictToOwner(stagedScript);
			return stagedScript;
		} catch (IOException e) {
			throw new UpgradeException("Cannot stage the upgrade runner script: " + e.getMessage(), e);
		}
	}

	/**
	 * Restricts a file to owner read/write/execute on POSIX file systems; a no-op elsewhere.
	 *
	 * @param file the file to restrict
	 * @throws IOException when the permissions cannot be set
	 */
	private static void restrictToOwner(final Path file) throws IOException {
		if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
			Files.setPosixFilePermissions(
				file,
				Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
			);
		}
	}
}
