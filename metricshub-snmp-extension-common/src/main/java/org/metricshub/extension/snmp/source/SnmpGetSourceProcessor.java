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
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * A utility class for processing SNMP Get sources.
 * This class processes SNMP GET sources, fetch data from SNMP devices using
 * the given configuration, and return the value as a text {@link SourceTable}.
 */
@Slf4j
@AllArgsConstructor
public class SnmpGetSourceProcessor {

	@NonNull
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@NonNull
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	/**
	 * Fetches the value of the OID of the provided {@link SnmpGetSource} from an SNMP device and returns it as a
	 * text, whole: a <code>;</code> or a line break in the value does not split it.
	 * If any errors occur during the fetch, or if the provided configurations are invalid, an empty text is
	 * returned.
	 *
	 * @param snmpGetSource    The {@link SnmpGetSource} defining the SNMP OID.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return a text {@link SourceTable} holding the value, or an empty text if processing fails.
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
		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			log.debug(
				"Hostname {} - The SNMP credentials are not configured. Returning an empty text for SNMP Get Source {}.",
				telemetryManager.getHostname(),
				snmpGetSource
			);
			return SourceTable.ofText(null);
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			// A null value is an empty text
			return SourceTable.ofText(
				snmpRequestExecutor.executeSNMPGet(
					snmpGetSource.getOid(),
					snmpConfiguration,
					hostname,
					true,
					telemetryManager.getHostname()
				)
			);
		} catch (Exception e) {
			// NOSONAR on interruption
			LoggingHelper.logSourceError(
				connectorId,
				snmpGetSource.getKey(),
				String.format("SNMP Get: %s.", snmpGetSource.getOid()),
				hostname,
				e
			);
			return SourceTable.ofText(null);
		}
	}
}
