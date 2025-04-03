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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.AUTOMATIC_NAMESPACE;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.WMI_DEFAULT_NAMESPACE;

import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.ConnectorNamespace;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing WMI sources producing the {@link SourceTable} result.
 * It provides a method to run WMI queries through WMI or WinRm, gets the WMI response,
 * and generates the {@link SourceTable} using the WMI response.
 */
@Slf4j
@RequiredArgsConstructor
public class WmiSourceProcessor {

	@NonNull
	private IWinRequestExecutor winRequestExecutor;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes a WMI source by executing its associated query using the configuration and hostname retrieved
	 * from a given {@link TelemetryManager}. This method handles potential errors such as malformed WMI source data
	 * or missing configuration and logs appropriate warnings or errors accordingly.
	 *
	 * @param wmiSource        The WMI source containing the query to be executed.
	 * @param telemetryManager The telemetry manager providing host configuration used to get
	 *                         the {@link IWinConfiguration} implementation.
	 * @return A {@link SourceTable} containing the results of the executed query or an empty table in case of an error.
	 */
	public SourceTable process(final WmiSource wmiSource, final TelemetryManager telemetryManager) {
		if (wmiSource == null) {
			log.warn(
				"Hostname {} - Malformed WMI source {}. Returning an empty table.",
				telemetryManager.getHostname(),
				wmiSource
			);
			return SourceTable.empty();
		}

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		if (winConfiguration == null) {
			log.debug(
				"Hostname {} - Neither WMI nor WinRM credentials are configured for this host. Returning an empty table for WMI source {}.",
				telemetryManager.getHostname(),
				wmiSource.getKey()
			);
			return SourceTable.empty();
		}

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		// Get the namespace
		final String namespace = getNamespace(wmiSource, telemetryManager);

		if (namespace == null) {
			log.error(
				"Hostname {} - Failed to retrieve the WMI namespace to run the WMI source {}. Returning an empty table.",
				hostname,
				wmiSource.getKey()
			);
			return SourceTable.empty();
		}

		try {
			final List<List<String>> table = winRequestExecutor.executeWmi(
				hostname,
				winConfiguration,
				wmiSource.getQuery(),
				namespace
			);

			return SourceTable.builder().table(table).build();
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				wmiSource.getKey(),
				String.format(
					"WMI query=%s, Username=%s, Timeout=%d, Namespace=%s",
					wmiSource.getQuery(),
					winConfiguration.getUsername(),
					winConfiguration.getTimeout(),
					namespace
				),
				hostname,
				e
			);

			return SourceTable.empty();
		}
	}

	/**
	 * Get the namespace to use for the execution of the given {@link WmiSource} instance
	 *
	 * @param wmiSource {@link WmiSource} instance from which we want to extract the namespace. Expected "automatic", null or <em>any
	 *                  string</em>
	 * @param telemetryManager The telemetry manager providing {@link HostProperties} instance
	 *                         where the {@link ConnectorNamespace} instance wraps
	 *                         the value of the automatic namespace.
	 * @return {@link String} value defining the WMI namespace.
	 */
	String getNamespace(final WmiSource wmiSource, final TelemetryManager telemetryManager) {
		final String sourceNamespace = wmiSource.getNamespace();

		if (sourceNamespace == null) {
			return WMI_DEFAULT_NAMESPACE;
		}

		if (AUTOMATIC_NAMESPACE.equalsIgnoreCase(sourceNamespace)) {
			// The namespace should be detected correctly in the detection strategy phase
			return telemetryManager.getHostProperties().getConnectorNamespace(connectorId).getAutomaticWmiNamespace();
		}

		return sourceNamespace;
	}
}
