package org.metricshub.cli.util;

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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SnmpCliHelper {

	/**
	 * Saves the SNMP result to files in the specified directory with the filename based on the OID.
	 * @param result the SNMP result to save
	 * @param directory	 the directory to save the result files
	 * @param out the PrintWriter to log messages
	 * @param oid the OID used to name the result file and perform the SNMP walk
	 */
	public static void saveSnmpResultToFile(
		final String result,
		final Path directory,
		final PrintWriter out,
		final String oid
	) {
		final var outPath = directory.resolve(oid + ".walk");
		try {
			// Ensure parent directory exists
			final Path parentDir = outPath.getParent();
			if (parentDir != null && Files.notExists(parentDir)) {
				Files.createDirectories(parentDir);
			}

			// Write the SNMP result to file (create or truncate)
			Files.writeString(
				outPath,
				result,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING
			);

			out.printf("Result saved to %s%n", outPath.toAbsolutePath());
		} catch (IOException ioe) {
			throw new UncheckedIOException("Failed to write SNMP result to file", ioe);
		}
	}
}
