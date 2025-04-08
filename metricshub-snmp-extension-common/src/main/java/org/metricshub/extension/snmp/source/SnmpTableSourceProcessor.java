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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * A utility class for processing SNMP table sources.
 * This class processes SNMP table sources, fetch data from SNMP devices using
 * the given configuration, and return it as a {@link SourceTable} object.
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
	 * If any errors occur during the fetch or processing, or if the provided configurations are invalid,
	 * an empty {@link SourceTable} is returned.
	 *
	 * @param snmpTableSource      The {@link SnmpTableSource} defining the SNMP OID and the SNMP columns to request.
	 * @param connectorId          The connector identifier used for logging purposes.
	 * @param telemetryManager     The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return a {@link SourceTable} containing the fetched SNMP table data, or an empty table if processing fails.
	 */
	public SourceTable process(
		final SnmpTableSource snmpTableSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (snmpTableSource == null) {
			log.error(
				"Hostname {} - SNMP Get Table Source cannot be null, the SNMP Get Table operation will return an empty result.",
				telemetryManager.getHostname()
			);
			return SourceTable.empty();
		}

		// run the Client in order to execute the snmpTable
		// receives a List structure
		final SourceTable sourceTable = new SourceTable();
		final String selectedColumns = snmpTableSource.getSelectColumns();

		if (selectedColumns.isBlank()) {
			return SourceTable.empty();
		}

		// The selectedColumns String is like "column1, column2, column3" and we want to split it into ["column1", "column2", "column3"]
		final String[] selectedColumnArray = selectedColumns.split("\\s*,\\s*");

		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			log.debug(
				"Hostname {} - The SNMP credentials are not configured. Returning an empty table for SNMP Get Table Source {}.",
				telemetryManager.getHostname(),
				snmpTableSource
			);
			return SourceTable.empty();
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			final List<List<String>> result = snmpRequestExecutor.executeSNMPTable(
				snmpTableSource.getOid(),
				selectedColumnArray,
				snmpConfiguration,
				hostname,
				true
			);

			sourceTable.setHeaders(Arrays.asList(selectedColumnArray));
			sourceTable.setTable(result);

			return sourceTable;
		} catch (Exception e) { // NOSONAR on interruption
			LoggingHelper.logSourceError(
				connectorId,
				snmpTableSource.getKey(),
				String.format("SNMP Table: %s", snmpTableSource.getOid()),
				hostname,
				e
			);

			return SourceTable.empty();
		}
	}
}
