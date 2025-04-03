package org.metricshub.extension.win.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing IPMI sources producing the {@link SourceTable} result.
 * It provides a method to run WMI or WinRm requests to get hardware information, gets the response,
 * and generates the {@link SourceTable} using the response.
 */
@RequiredArgsConstructor
@Slf4j
public class WinIpmiSourceProcessor {

	@NonNull
	private IWinRequestExecutor winRequestExecutor;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes IPMI sources by executing a series of WMI queries to gather system and sensor information
	 * from a Windows host using the provided {@link TelemetryManager}. It uses the configuration to connect and
	 * execute queries in specific namespaces, and compiles the results into a {@link SourceTable}.
	 *
	 * @param ipmiSource       The source configuration for IPMI queries.
	 * @param telemetryManager The telemetry manager providing host configuration and Windows protocol settings.
	 * @return A {@link SourceTable} containing the aggregated results of the WMI queries, or an empty table
	 *         if configuration is missing or queries fail.
	 */
	public SourceTable process(final IpmiSource ipmiSource, final TelemetryManager telemetryManager) {
		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		if (winConfiguration == null) {
			log.warn(
				"Hostname {} - The Windows protocol credentials are not configured. Cannot process Windows IPMI source.",
				telemetryManager.getHostname()
			);
			return SourceTable.empty();
		}

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		final String sourceKey = ipmiSource.getKey();
		final String nameSpaceRootCimv2 = "root/cimv2";
		final String nameSpaceRootHardware = "root/hardware";

		final List<List<String>> wmiCollection1 = executeIpmiWmiRequest(
			hostname,
			winConfiguration,
			"SELECT IdentifyingNumber,Name,Vendor FROM Win32_ComputerSystemProduct",
			nameSpaceRootCimv2,
			sourceKey
		);

		final List<List<String>> wmiCollection2 = executeIpmiWmiRequest(
			hostname,
			winConfiguration,
			"SELECT BaseUnits,CurrentReading,Description,LowerThresholdCritical,LowerThresholdNonCritical,SensorType,UnitModifier,UpperThresholdCritical,UpperThresholdNonCritical" +
			" FROM NumericSensor",
			nameSpaceRootHardware,
			sourceKey
		);

		final List<List<String>> wmiCollection3 = executeIpmiWmiRequest(
			hostname,
			winConfiguration,
			"SELECT CurrentState,Description FROM Sensor",
			nameSpaceRootHardware,
			sourceKey
		);

		return SourceTable
			.builder()
			.table(IpmiThroughWmiHelper.ipmiTranslateFromWmi(wmiCollection1, wmiCollection2, wmiCollection3))
			.build();
	}

	/**
	 * Call the client executor to execute a WMI request.
	 *
	 * @param hostname         The host against the query will be run.
	 * @param winConfiguration The information used to connect to the host and perform the query.
	 * @param wmiQuery         The query that will be executed.
	 * @param namespace        The namespace in which the query will be executed.
	 * @param sourceKey        The key of the source.
	 * @return The result of the execution of the query.
	 */
	private List<List<String>> executeIpmiWmiRequest(
		final String hostname,
		final IWinConfiguration winConfiguration,
		final String wmiQuery,
		final String namespace,
		final String sourceKey
	) {
		log.info("Hostname {} - Executing IPMI Query for source [{}]:\nWMI Query: {}:\n", hostname, sourceKey, wmiQuery);

		List<List<String>> result;

		try {
			result = winRequestExecutor.executeWmi(hostname, winConfiguration, wmiQuery, namespace);
		} catch (Exception exception) {
			LoggingHelper.logSourceError(
				connectorId,
				sourceKey,
				String.format(
					"IPMI WMI query=%s, Hostname=%s, Username=%s, Timeout=%d, Namespace=%s",
					wmiQuery,
					hostname,
					winConfiguration.getUsername(),
					winConfiguration.getTimeout(),
					namespace
				),
				hostname,
				exception
			);

			result = Collections.emptyList();
		}

		log.info(
			"Hostname {} - IPMI query for [{}] result:\n{}\n",
			hostname,
			sourceKey,
			TextTableHelper.generateTextTable(result)
		);

		return result;
	}
}
