package org.metricshub.extension.oscommand;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.common.helpers.FilterResultHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Processes command-line data sources to extract and transform the output into a text {@link SourceResult}.
 * This class handles the execution of operating system commands provided through {@link CommandLineSource}, applies line and column filtering, and returns the selected lines as a text.
 */
@RequiredArgsConstructor
public class CommandLineSourceProcessor {

	@NonNull
	private final OsCommandService osCommandService;

	/**
	 * Processes an OS command defined in a {@link CommandLineSource}, executes it, and returns the selected lines as a text {@link SourceResult}.
	 *
	 * @param commandLineSource The command line source configuration containing the command, filtering, and selection settings.
	 * @param connectorId       Identifier for the connector instance processing this source.
	 * @param telemetryManager  Provides access to system and host configurations necessary for command execution.
	 * @return A {@link SourceResult} containing the processed and formatted output of the OS command, whatever its exit
	 *         code. A failure if the command cannot run or if the command line is invalid.
	 */
	public SourceResult process(
		final CommandLineSource commandLineSource,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		final String hostname = telemetryManager.getHostname();

		if (
			commandLineSource == null ||
				commandLineSource.getCommandLine() == null ||
				commandLineSource.getCommandLine().isEmpty()
		) {
			return SourceResult.failed("Malformed OS command source: no command line.");
		}

		final String request = String.format("OS command: %s", commandLineSource.getCommandLine());
		try {
			final OsCommandResult osCommandResult = osCommandService.runOsCommand(
				commandLineSource.getCommandLine(),
				telemetryManager,
				commandLineSource.getTimeout(),
				commandLineSource.getExecuteLocally(),
				telemetryManager.getHostProperties().isLocalhost(),
				telemetryManager.getEmbeddedFiles(connectorId)
			);
			if (osCommandResult.getResult() == null) {
				return SourceResult.failed(request + ": the command did not run.");
			}

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
			OsCommandService.restoreInterrupt(e);
			LoggingHelper.logSourceError(connectorId, commandLineSource.getKey(), request, hostname, e);

			return SourceResult.failed(request, e);
		}
	}
}
