package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.sentrysoftware.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.PslUtils;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Processes command-line criteria to evaluate operating system command queries against specified expectations.
 * This class facilitates the execution of local or remote OS commands, evaluates the results, and generates
 * criterion test results based on the expected outcomes.
 */
@RequiredArgsConstructor
public class CommandLineCriterionProcessor {

	@NonNull
	private String connectorId;

	/**
	 * Processes a given {@link CommandLineCriterion}, executes the corresponding OS command, and evaluates the
	 * command's output against the expected result.
	 *
	 * @param commandLineCriterion The {@link CommandLineCriterion} to process.
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @return {@link CriterionTestResult} instance.
	 */
	public CriterionTestResult process(CommandLineCriterion commandLineCriterion, TelemetryManager telemetryManager) {
		if (commandLineCriterion == null) {
			return CriterionTestResult.error(commandLineCriterion, "Malformed OSCommand criterion.");
		}

		if (
			commandLineCriterion.getCommandLine().isEmpty() ||
			commandLineCriterion.getExpectedResult() == null ||
			commandLineCriterion.getExpectedResult().isEmpty()
		) {
			return CriterionTestResult.success(
				commandLineCriterion,
				"CommandLine or ExpectedResult are empty. Skipping this test."
			);
		}

		try {
			final OsCommandResult osCommandResult = OsCommandService.runOsCommand(
				commandLineCriterion.getCommandLine(),
				telemetryManager,
				commandLineCriterion.getTimeout(),
				commandLineCriterion.getExecuteLocally(),
				telemetryManager.getHostProperties().isLocalhost(),
				telemetryManager.getEmbeddedFiles(connectorId)
			);

			final CommandLineCriterion osCommandNoPassword = CommandLineCriterion
				.builder()
				.commandLine(osCommandResult.getNoPasswordCommand())
				.executeLocally(commandLineCriterion.getExecuteLocally())
				.timeout(commandLineCriterion.getTimeout())
				.expectedResult(commandLineCriterion.getExpectedResult())
				.build();

			final Matcher matcher = Pattern
				.compile(
					PslUtils.psl2JavaRegex(commandLineCriterion.getExpectedResult()),
					Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
				)
				.matcher(osCommandResult.getResult());

			return matcher.find()
				? CriterionTestResult.success(osCommandNoPassword, osCommandResult.getResult())
				: CriterionTestResult.failure(osCommandNoPassword, osCommandResult.getResult());
		} catch (NoCredentialProvidedException noCredentialProvidedException) {
			return CriterionTestResult.error(commandLineCriterion, noCredentialProvidedException.getMessage());
		} catch (Exception exception) { // NOSONAR on interruption
			return CriterionTestResult.error(commandLineCriterion, exception);
		}
	}
}
