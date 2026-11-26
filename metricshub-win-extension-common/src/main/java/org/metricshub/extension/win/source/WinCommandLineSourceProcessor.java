package org.metricshub.extension.win.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.util.List;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.common.helpers.FilterResultHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;

/**
 * A class responsible for processing CommandLine sources producing the {@link SourceResult}.
 * It provides a method to run Windows remote commands through WMI or WinRm, gets the response,
 * and generates the {@link SourceResult} using the command line response.
 */
@RequiredArgsConstructor
public class WinCommandLineSourceProcessor {

	@NonNull
	private WinCommandService winCommandService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes a command line defined in a {@link CommandLineSource}, executes it through Wmi or WinRM, and converts the output
	 * into a structured {@link SourceResult}.
	 *
	 * @param commandLineSource The command line source configuration containing the command, filtering, and selection settings.
	 * @param telemetryManager  Provides host configurations necessary for command execution.
	 * @return A {@link SourceResult} containing the processed and formatted output of the command line, whatever its
	 *         exit code. A failure if the command cannot run or if the command line is invalid.
	 */
	public SourceResult process(final CommandLineSource commandLineSource, final TelemetryManager telemetryManager) {
		// Retrieve the IWinConfiguration (WMI or WinRm)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String hostname = winConfiguration == null
			? telemetryManager.getHostname()
			: telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		if (commandLineSource == null || commandLineSource.getCommandLine().isEmpty()) {
			return SourceResult.failed("Malformed OS command source: no command line.");
		}

		final String request = String.format("OS command: %s", commandLineSource.getCommandLine());
		try {
			final OsCommandResult osCommandResult = winCommandService.runOsCommand(
				commandLineSource.getCommandLine(),
				hostname,
				winConfiguration,
				telemetryManager.getEmbeddedFiles(connectorId),
				telemetryManager.getRequestStats()
			);

			// transform to lines
			final List<String> resultLines = SourceResult.lineToList(osCommandResult.getResult(), NEW_LINE);

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

			return SourceResult.ofText(String.join(NEW_LINE, selectedColumnsLines));
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, commandLineSource.getKey(), request, hostname, e);

			return SourceResult.failed(request, e);
		}
	}
}
