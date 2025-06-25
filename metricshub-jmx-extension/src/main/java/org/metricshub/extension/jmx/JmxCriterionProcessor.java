package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Reads zero or more attributes from one MBean and compares each against an optional expected result.
 */
@Slf4j
@RequiredArgsConstructor
public class JmxCriterionProcessor {

	protected static final String JMX_TEST_SUCCESS = "Hostname %s - JMX test succeeded. Returned result: %s.";

	@NonNull
	private JmxRequestExecutor jmxRequestExecutor;

	/**
	 * Processes a JMX criterion by executing an JMX query.
	 *
	 * @param jmxCriterion     The criterion including the JMX query.
	 * @param connectorId      The ID of the connector used for the JMX request.
	 * @param telemetryManager The telemetry manager providing access to host configuration
	 * @return {@link CriterionTestResult} instance.
	 */
	public CriterionTestResult process(
		final JmxCriterion jmxCriterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (jmxCriterion == null) {
			return CriterionTestResult.error(
				jmxCriterion,
				"Malformed criterion. Cannot perform detection. Connector ID: " + connectorId
			);
		}

		final var jmxConfiguration = (JmxConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(JmxConfiguration.class);

		if (jmxConfiguration == null) {
			return CriterionTestResult.error(jmxCriterion, "The JMX credentials are not configured for this host.");
		}

		try {
			final List<List<String>> queryResult = jmxRequestExecutor.fetchMBean(
				jmxConfiguration,
				jmxCriterion.getObjectName(),
				jmxCriterion.getAttributes(),
				List.of()
			);

			// Serialize the result as a CSV
			final String result = SourceTable.tableToCsv(queryResult, TABLE_SEP, true);

			return checkJmxResult(jmxConfiguration.getHostname(), result, jmxCriterion.getExpectedResult());
		} catch (Exception e) {
			return CriterionTestResult.error(jmxCriterion, e);
		}
	}

	/**
	 * Checks the result of an JMX test against the expected result.
	 *
	 * @param hostname       The hostname against which the JMX test has been carried out.
	 * @param result         The actual result of the JMX test.
	 * @param expectedResult The expected result of the JMX test.
	 * @return A {@link CriterionTestResult} summarizing the outcome of the JMX test.
	 */
	private CriterionTestResult checkJmxResult(final String hostname, final String result, final String expectedResult) {
		String message;
		var success = false;

		if (expectedResult == null) {
			if (result == null || result.isEmpty()) {
				message = String.format("Hostname %s - JMX test failed - The JMX test did not return any result.", hostname);
			} else {
				message = String.format(JMX_TEST_SUCCESS, hostname, result);
				success = true;
			}
		} else {
			// We convert the PSL regex from the expected result into a Java regex to be able to compile and test it
			final var pattern = Pattern.compile(PslUtils.psl2JavaRegex(expectedResult), Pattern.CASE_INSENSITIVE);
			if (result != null && pattern.matcher(result).find()) {
				message = String.format(JMX_TEST_SUCCESS, hostname, result);
				success = true;
			} else {
				message =
					String.format(
						"Hostname %s - JMX test failed - The result (%s) returned by the JMX test did not match the expected result (%s).",
						hostname,
						result,
						expectedResult
					);
				message += String.format("Expected value: %s - returned value %s.", expectedResult, result);
			}
		}

		log.debug(message);

		return CriterionTestResult.builder().result(result).message(message).success(success).build();
	}
}
