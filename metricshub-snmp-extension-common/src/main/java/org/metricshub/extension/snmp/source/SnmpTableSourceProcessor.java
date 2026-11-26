package org.metricshub.extension.snmp.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension Common
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

import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * A utility class for processing SNMP table sources.
 * This class processes SNMP table sources, fetch data from SNMP devices using
 * the given configuration, and return it as a {@link SourceResult} object.
 */
@Slf4j
@AllArgsConstructor
public class SnmpTableSourceProcessor {

	@NonNull
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@NonNull
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	/**
	 * Fetches data from an SNMP device based on the provided {@link SnmpTableSource} connector's directive,
	 * parses the data into a structured table format, and returns it.
	 * If the request fails, the agent answers with an error, or SNMP is not configured, the source fails.
	 *
	 * @param snmpTableSource      The {@link SnmpTableSource} defining the SNMP OID and the SNMP columns to request.
	 * @param connectorId          The connector identifier used for logging purposes.
	 * @param telemetryManager     The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return a {@link SourceResult} containing the fetched SNMP table data, or a failure.
	 */
	public SourceResult process(
		final SnmpTableSource snmpTableSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (snmpTableSource == null) {
			log.error(
				"Hostname {} - SNMP Get Table Source cannot be null, the SNMP Get Table operation will return an empty result.",
				telemetryManager.getHostname()
			);
			return SourceResult.empty();
		}

		final String selectedColumns = snmpTableSource.getSelectColumns();

		if (selectedColumns.isBlank()) {
			return SourceResult.empty();
		}

		// The selectedColumns String is like "column1, column2, column3" and we want to split it into ["column1", "column2", "column3"]
		final String[] selectedColumnArray = selectedColumns.split("\\s*,\\s*");

		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			return SourceResult.failed("SNMP Table: SNMP is not configured on the host.");
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			final List<List<String>> result = snmpRequestExecutor.executeSNMPTable(
				snmpTableSource.getOid(),
				selectedColumnArray,
				snmpConfiguration,
				hostname,
				true,
				telemetryManager.getRequestStats()
			);

			return SourceResult.ofTable(result);
		} catch (Exception e) {
			// NOSONAR on interruption
			final String request = String.format("SNMP Table: %s", snmpTableSource.getOid());
			LoggingHelper.logSourceError(connectorId, snmpTableSource.getKey(), request, hostname, e);

			return SourceResult.failed(request, e);
		}
	}
}
