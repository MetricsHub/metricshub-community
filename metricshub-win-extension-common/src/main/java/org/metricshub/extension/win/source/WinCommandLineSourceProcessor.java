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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.FilterResultHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;

/**
 * A class responsible for processing CommandLine sources producing the {@link SourceTable} result.
 * It provides a method to run Windows remote commands through WMI or WinRm, gets the response,
 * and generates the {@link SourceTable} using the command line response.
 */
@RequiredArgsConstructor
@Slf4j
public class WinCommandLineSourceProcessor {

	@NonNull
	private WinCommandService winCommandService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes a command line defined in a {@link CommandLineSource}, executes it through Wmi or WinRM, and converts the output
	 * into a structured {@link SourceTable}.
	 *
	 * @param commandLineSource The command line source configuration containing the command, filtering, and selection settings.
	 * @param telemetryManager  Provides host configurations necessary for command execution.
	 * @return A {@link SourceTable} containing the processed and formatted output of the command line.
	 *         Returns an empty table if an error occurs or if the command line is invalid.
	 */
	public SourceTable process(final CommandLineSource commandLineSource, final TelemetryManager telemetryManager) {
		// Retrieve the IWinConfiguration (WMI or WinRm)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = winConfiguration == null
			? telemetryManager.getHostname()
			: telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		if (commandLineSource == null || commandLineSource.getCommandLine().isEmpty()) {
			log.error("Hostname {} - Malformed OS command source.", hostname);
			return SourceTable.empty();
		}

		try {
			final OsCommandResult osCommandResult = winCommandService.runOsCommand(
				commandLineSource.getCommandLine(),
				hostname,
				winConfiguration,
				telemetryManager.getEmbeddedFiles(connectorId)
			);

			// transform to lines
			final List<String> resultLines = SourceTable.lineToList(osCommandResult.getResult(), NEW_LINE);

			final List<String> filteredLines = FilterResultHelper.filterLines(
				resultLines,
				commandLineSource.getBeginAtLineNumber(),
				commandLineSource.getEndAtLineNumber(),
				commandLineSource.getExclude(),
				commandLineSource.getKeep()
			);

			final List<String> selectedColumnsLines = FilterResultHelper.selectedColumns(
				filteredLines,
				commandLineSource.getSeparators(),
				commandLineSource.getSelectColumns()
			);

			return SourceTable
				.builder()
				.rawData(selectedColumnsLines.stream().collect(Collectors.joining(NEW_LINE)))
				.table(
					selectedColumnsLines
						.stream()
						.map(line -> Stream.of(line.split(TABLE_SEP)).collect(Collectors.toList()))
						.collect(Collectors.toList())
				)
				.build();
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				commandLineSource.getKey(),
				String.format("OS command: %s.", commandLineSource.getCommandLine()),
				hostname,
				e
			);

			return SourceTable.empty();
		}
	}
}
