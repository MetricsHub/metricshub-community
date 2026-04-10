package org.metricshub.extension.emulation.snmp;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.metricshub.snmp.client.ISnmpClient;
import org.metricshub.snmp.client.OfflineSnmpClient;

/**
 * An SNMP request executor that creates an {@link OfflineSnmpClient} backed by
 * {@code .walk} files located in the emulation input directory's {@code snmp/}
 * subdirectory. This enables file-based SNMP emulation without making real
 * network calls.
 *
 * <p>The walk files are the standard SNMP walk dump format produced by
 * {@code snmpcli} and {@code snmpv3cli}, where each line contains:
 * <pre>
 * OID\tTYPE\tVALUE
 * </pre>
 *
 * <p>The executor reads the emulation input directory from its constructor and
 * ignores the provided {@link ISnmpConfiguration} for client creation, since the
 * offline client reads responses directly from the walk files.
 */
@Slf4j
@RequiredArgsConstructor
public class EmulationSnmpRequestExecutor extends AbstractSnmpRequestExecutor {

	private static final String SNMP_SUBDIR = "snmp";

	private final String emulationInputDirectory;

	@Override
	protected ISnmpClient createSnmpClient(final ISnmpConfiguration configuration, final String hostname)
		throws IOException {
		if (emulationInputDirectory == null || emulationInputDirectory.isBlank()) {
			throw new IOException("Emulation input directory is not configured.");
		}

		final Path snmpDir = Path.of(emulationInputDirectory, SNMP_SUBDIR);

		if (!Files.isDirectory(snmpDir)) {
			throw new IOException("SNMP emulation directory not found: " + snmpDir);
		}

		return new OfflineSnmpClient(snmpDir);
	}
}
