package org.metricshub.extension.win.detection;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;

/**
 * A class responsible for processing WMI criteria to evaluate WMI queries against specified criteria.
 * It provides methods to execute WMI queries, evaluates the results against expected outcomes,
 * and generates criterion test results accordingly. This service is intended to be used by any processor
 * which requires {@link WmiCriterion} evaluation.
 */
@RequiredArgsConstructor
@Slf4j
public class WmiDetectionService {

	@NonNull
	@Getter
	private IWinRequestExecutor winRequestExecutor;

	/**
	 * Perform the specified WMI detection test, on the specified Win protocol configuration.
	 * <br>
	 * Note: "Automatic" namespace is not supported in this method.
	 * <br>
	 *
	 * @param hostname               Host name
	 * @param winConfiguration       Win configuration (credentials, timeout)
	 * @param wmiCriterion           WMI detection properties (WQL, namespace, expected result)
	 * @param connectorId            Connector ID (used for logging purposes)
	 * @param logMode                Whether to log or not the error in case of failure
	 *                                (can be set to false when this method is used as part of namespace
	 *                                detection, to avoid polluting logs with expected failures)
	 * @param recordOutputDirectory  Directory where WMI responses are recorded, or {@code null} to skip recording
	 * @return {@link CriterionTestResult} which indicates if the check has succeeded or not.
	 */
	public CriterionTestResult performDetectionTest(
		final String hostname,
		@NonNull final IWinConfiguration winConfiguration,
		@NonNull final WmiCriterion wmiCriterion,
		@NonNull String connectorId,
		final boolean logMode,
		final String recordOutputDirectory
	) {
		// Make the WBEM query
		final List<List<String>> queryResult;
		try {
			queryResult =
				winRequestExecutor.executeWmi(
					hostname,
					winConfiguration,
					wmiCriterion.getQuery(),
					wmiCriterion.getNamespace(),
					recordOutputDirectory
				);
		} catch (Exception e) {
			if (logMode) {
				log.error(
					"Hostname {} - Error executing WMI criterion: {}. Exception message {}. Connector ID: {}.",
					hostname,
					wmiCriterion.getQuery(),
					e.getMessage(),
					connectorId
				);
				log.debug(
					"Hostname {} - An exception occurred while executing WMI criterion: {}. Connector ID: {}.",
					hostname,
					wmiCriterion.getQuery(),
					connectorId,
					e
				);
			}
			return CriterionTestResult.error(wmiCriterion, e);
		}

		// Serialize the result as a CSV
		String actualResult = SourceTable.tableToCsv(queryResult, TABLE_SEP, true);

		// Empty result? ==> failure
		if (actualResult == null || actualResult.isBlank()) {
			return CriterionTestResult.failure(wmiCriterion, "No result.");
		}

		// No expected result (and non-empty result)? ==> success
		if (wmiCriterion.getExpectedResult() == null || wmiCriterion.getExpectedResult().isBlank()) {
			return CriterionTestResult.success(wmiCriterion, actualResult);
		}

		// Search for the expected result
		final Matcher matcher = Pattern
			.compile(PslUtils.psl2JavaRegex(wmiCriterion.getExpectedResult()), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
			.matcher(actualResult);

		// If the expected result is found ==> success
		if (matcher.find()) {
			return CriterionTestResult.success(wmiCriterion, matcher.group());
		}

		// No match!
		return CriterionTestResult.failure(wmiCriterion, actualResult);
	}
}
