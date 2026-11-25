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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * A utility class for processing SNMP Get sources.
 * This class processes SNMP GET sources, fetch data from SNMP devices using
 * the given configuration, and return the value as a text {@link SourceTable}, or as a table of one cell with
 * <code>output: table</code>.
 */
@Slf4j
@AllArgsConstructor
public class SnmpGetSourceProcessor {

	@NonNull
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@NonNull
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	/**
	 * Fetches the value of the OID of the provided {@link SnmpGetSource} from an SNMP device and returns it whole, a
	 * <code>;</code> or a line break in the value does not split it: as a text, or with <code>output: table</code> as a
	 * table of one row of one cell.
	 * If any errors occur during the fetch, or if the provided configurations are invalid, an empty text is returned,
	 * or an empty table (no row) with <code>output: table</code>.
	 *
	 * @param snmpGetSource    The {@link SnmpGetSource} defining the SNMP OID.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return a {@link SourceTable} holding the value, or an empty text or table if processing fails.
	 */
	public SourceTable process(
		final SnmpGetSource snmpGetSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (snmpGetSource == null) {
			log.error(
				"Hostname {} - SNMP Get Source cannot be null, the SNMP Get operation will return an empty result.",
				telemetryManager.getHostname()
			);
			return SourceTable.ofText(null);
		}
		final boolean table = snmpGetSource.getOutputContentType() == ContentType.TABLE;
		final SourceTable noResult = table ? SourceTable.empty() : SourceTable.ofText(null);

		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			log.debug(
				"Hostname {} - The SNMP credentials are not configured. Returning an empty result for SNMP Get Source {}.",
				telemetryManager.getHostname(),
				snmpGetSource
			);
			return noResult;
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			final String result = snmpRequestExecutor.executeSNMPGet(
				snmpGetSource.getOid(),
				snmpConfiguration,
				hostname,
				true,
				telemetryManager.getRequestStats()
			);

			if (!table) {
				// A null value is an empty text
				return SourceTable.ofText(result);
			}

			if (result != null) {
				return SourceTable.ofTable(
					Stream.of(
						Stream.of(result).collect(Collectors.toList()) // NOSONAR
					).collect(Collectors.toList()) // NOSONAR
				);
			}
		} catch (Exception e) {
			// NOSONAR on interruption
			LoggingHelper.logSourceError(
				connectorId,
				snmpGetSource.getKey(),
				String.format("SNMP Get: %s.", snmpGetSource.getOid()),
				hostname,
				e
			);
		}

		return noResult;
	}
}
