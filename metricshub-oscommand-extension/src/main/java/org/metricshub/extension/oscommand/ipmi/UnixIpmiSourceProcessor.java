package org.metricshub.extension.oscommand.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.OsCommandConfiguration;
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;

/**
 * Processes IPMI source information for Unix systems, leveraging telemetry configurations to execute and retrieve data
 * from IPMI tool commands. It handles both local and remote command executions based on system configuration.
 */
@Slf4j
@AllArgsConstructor
public class UnixIpmiSourceProcessor {

	/**
	 * Executes IPMI commands to collect data from Unix systems and constructs a {@link SourceTable} with the results.
	 * Handles command execution both locally and remotely, depending on the system configuration.
	 *
	 * @param sourceKey The key identifier for the IPMI source being processed.
	 * @param connectorId Identifier for the connector instance processing this source.
	 * @param telemetryManager Provides context and configuration for accessing system properties and executing commands.
	 * @return A {@link SourceTable} containing processed IPMI data. Returns an empty table if errors occur or if no data is available.
	 */
	public SourceTable processUnixIpmiSource(
		final String sourceKey,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final SshConfiguration sshConfiguration = (SshConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		final OsCommandConfiguration osCommandConfiguration = (OsCommandConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(OsCommandConfiguration.class);

		// Retrieve the hostname from the configurations, otherwise from telemetryManager.
		final String hostname = telemetryManager.getHostname(List.of(SshConfiguration.class, OsCommandConfiguration.class));

		// get the ipmiTool command to execute
		String ipmitoolCommand = telemetryManager.getHostProperties().getIpmitoolCommand();
		if (ipmitoolCommand == null || ipmitoolCommand.isEmpty()) {
			final String message = String.format(
				"Hostname %s - IPMI tool command cannot be found. Returning an empty result.",
				hostname
			);
			log.error(message);
			return SourceTable.empty();
		}

		final boolean isLocalHost = telemetryManager.getHostProperties().isLocalhost();

		final long defaultTimeout = osCommandConfiguration != null
			? osCommandConfiguration.getTimeout()
			: OsCommandConfiguration.DEFAULT_TIMEOUT;

		// fru command
		String fruCommand = ipmitoolCommand + " fru";
		String fruResult;
		try {
			if (isLocalHost) {
				fruResult = OsCommandService.runLocalCommand(fruCommand, defaultTimeout, null);
			} else if (sshConfiguration != null) {
				fruResult =
					OsCommandService.runSshCommand(
						fruCommand,
						hostname,
						sshConfiguration,
						defaultTimeout,
						null,
						null,
						telemetryManager.getHostConfiguration().getHostType()
					);
			} else {
				log.warn("Hostname {} - Could not process UNIX IPMI Source. SSH protocol credentials are missing.", hostname);
				return SourceTable.empty();
			}

			log.debug("Hostname {} - IPMI OS command: {}:\n{}", hostname, fruCommand, fruResult);
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				sourceKey,
				String.format("IPMI OS command: %s.", fruCommand),
				hostname,
				e
			);

			Thread.currentThread().interrupt();

			return SourceTable.empty();
		}

		// "-v sdr elist all"
		String sdrCommand = ipmitoolCommand + " -v sdr elist all";
		String sensorResult;
		try {
			if (isLocalHost) {
				sensorResult = OsCommandService.runLocalCommand(sdrCommand, defaultTimeout, null);
			} else {
				sensorResult =
					OsCommandService.runSshCommand(
						sdrCommand,
						hostname,
						sshConfiguration,
						defaultTimeout,
						null,
						null,
						telemetryManager.getHostConfiguration().getHostType()
					);
			}
			log.debug("Hostname {} - IPMI OS command: {}:\n{}", hostname, sdrCommand, sensorResult);
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				sourceKey,
				String.format("IPMI OS command: %s.", sdrCommand),
				hostname,
				e
			);

			Thread.currentThread().interrupt();

			return SourceTable.empty();
		}

		return SourceTable.builder().table(IpmiHelper.ipmiTranslateFromIpmitool(fruResult, sensorResult)).build();
	}
}
