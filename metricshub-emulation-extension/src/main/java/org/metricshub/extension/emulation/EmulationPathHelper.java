package org.metricshub.extension.emulation;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for secure path resolution within the emulation framework.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmulationPathHelper {

	/**
	 * Resolves a response filename against a base directory and validates that the
	 * resulting path stays within the base directory (preventing path traversal attacks).
	 *
	 * @param baseDir          The base emulation directory (e.g., the protocol-specific subdirectory).
	 * @param responseFileName The response filename read from {@code image.yaml}.
	 * @return The validated, normalized {@link Path}, or {@code null} if the path escapes the base directory.
	 */
	public static Path resolveSecurely(final Path baseDir, final String responseFileName) {
		final Path resolved = baseDir.resolve(responseFileName).normalize();
		if (!resolved.startsWith(baseDir.normalize())) {
			log.error("Path traversal detected in response filename: {}", responseFileName);
			return null;
		}
		return resolved;
	}
}
