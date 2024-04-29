package org.sentrysoftware.metricshub.extension.win.detection;

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

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.sentrysoftware.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.PslUtils;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.extension.win.WinCommandService;

/**
 * A class responsible for processing CommandLine criteria to evaluate command lines against specified criteria.
 * It provides a method to run Windows remote commands through WMI or WinRm , evaluate the results against expected outcomes,
 * and generate criterion test results accordingly.
 */
@RequiredArgsConstructor
public class WinCommandLineCriterionProcessor {

	@NonNull
	private WinCommandService winCommandService;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	/**
	 * Processes a {@link CommandLineCriterion} using the provided {@link TelemetryManager} to test
	 * command execution outcomes based on expected results. The method validates the criterion and,
	 * based on system properties, decides whether to proceed with command execution or not.
	 *
	 * @param commandLineCriterion The command line criterion to be evaluated.
	 * @param telemetryManager     Provides system configuration and properties for context.
	 * @return {@link CriterionTestResult} reflecting the outcome of the evaluation.
	 */
	public CriterionTestResult process(CommandLineCriterion commandLineCriterion, TelemetryManager telemetryManager) {
		if (commandLineCriterion == null) {
			return CriterionTestResult.error(commandLineCriterion, "Malformed CommandLine criterion.");
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

		if (Boolean.TRUE.equals(commandLineCriterion.getExecuteLocally())) {
			return CriterionTestResult.error(
				commandLineCriterion,
				"The CommandLine criterion cannot be executed locally through WMI. Skipping this test."
			);
		}

		final boolean isLocalhost = telemetryManager.getHostProperties().isLocalhost();
		final DeviceKind hostType = telemetryManager.getHostConfiguration().getHostType();

		if (isLocalhost || hostType != DeviceKind.WINDOWS) {
			return CriterionTestResult.error(
				commandLineCriterion,
				String.format(
					"Cannot process CommandLine criterion for %s host of type %s.",
					isLocalhost ? "local" : "remote",
					hostType
				)
			);
		}

		try {
			final OsCommandResult osCommandResult = winCommandService.runOsCommand(
				commandLineCriterion.getCommandLine(),
				telemetryManager.getHostname(),
				configurationRetriever.apply(telemetryManager)
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
