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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Marker files written by the detached upgrade runner in the staging directory. The runner is the
 * only writer; the agent reads the result during startup reconciliation — in particular for
 * same-version hotfix installations, where comparing versions cannot prove that the installer
 * actually ran.
 */
@Slf4j
public class RunnerMarkers {

	/**
	 * Name of the result marker file written by the runner
	 * ({@code INSTALL_OK} or {@code INSTALL_FAILED ...}).
	 */
	public static final String RESULT_FILE_NAME = "runner.result";

	/**
	 * First token of the result marker when the installation succeeded.
	 */
	public static final String INSTALL_OK = "INSTALL_OK";

	private RunnerMarkers() {}

	/**
	 * Reads the runner result marker.
	 *
	 * @param stagingDirectory the upgrade staging directory
	 * @return the trimmed marker content, when present and readable
	 */
	public static Optional<String> readResult(final Path stagingDirectory) {
		final Path resultFile = stagingDirectory.resolve(RESULT_FILE_NAME);
		if (!Files.isRegularFile(resultFile)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readString(resultFile, StandardCharsets.UTF_8).trim());
		} catch (IOException e) {
			log.warn("Cannot read the upgrade runner result marker {}: {}", resultFile, e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Indicates whether the runner reported a successful installation.
	 *
	 * @param stagingDirectory the upgrade staging directory
	 * @return {@code true} when the result marker starts with {@code INSTALL_OK}
	 */
	public static boolean installSucceeded(final Path stagingDirectory) {
		return readResult(stagingDirectory)
			.map(result -> result.toUpperCase(Locale.ROOT).startsWith(INSTALL_OK))
			.orElse(false);
	}

	/**
	 * Deletes the runner marker files after reconciliation, so a stale marker can never influence
	 * the next upgrade.
	 *
	 * @param stagingDirectory the upgrade staging directory
	 */
	public static void clear(final Path stagingDirectory) {
		try {
			Files.deleteIfExists(stagingDirectory.resolve(RESULT_FILE_NAME));
		} catch (IOException e) {
			log.warn("Cannot delete the upgrade runner result marker: {}", e.getMessage());
		}
	}
}
