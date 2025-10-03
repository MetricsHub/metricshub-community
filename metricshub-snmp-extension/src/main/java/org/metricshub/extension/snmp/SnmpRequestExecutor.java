package org.metricshub.extension.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension
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
import java.nio.file.Path;
import org.metricshub.snmp.client.ISnmpClient;
import org.metricshub.snmp.client.OfflineSnmpClient;
import org.metricshub.snmp.client.SnmpClient;

/**
 * The SnmpRequestExecutor class extends {@link AbstractSnmpRequestExecutor} and provides utility methods
 * for executing various SNMP requests on local or remote hosts.
 */
public class SnmpRequestExecutor extends AbstractSnmpRequestExecutor {

	@Override
	protected ISnmpClient createSnmpClient(ISnmpConfiguration protocol, String hostname, String emulationInputFilePath)
		throws IOException {
		final SnmpConfiguration snmpConfig = (SnmpConfiguration) protocol;

		// If an emulation input file path is provided, use the OfflineSnmpFileClient for testing purposes.
		if (emulationInputFilePath != null && !emulationInputFilePath.isBlank()) {
			return new OfflineSnmpClient(Path.of(emulationInputFilePath));
		}

		return new SnmpClient(
			hostname,
			snmpConfig.getPort(),
			snmpConfig.getIntVersion(),
			snmpConfig.getRetryIntervals(),
			String.valueOf(snmpConfig.getCommunity()),
			null,
			null,
			null,
			null,
			null,
			null,
			null
		);
	}
}
