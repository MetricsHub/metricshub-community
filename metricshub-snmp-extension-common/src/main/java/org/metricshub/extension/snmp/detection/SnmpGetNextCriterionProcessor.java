package org.metricshub.extension.snmp.detection;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension Common
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

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;

/**
 * A class responsible for processing SNMP GetNext criteria to evaluate SNMP queries against specified criteria.
 * It provides methods to perform SNMP GetNext operations, evaluate the results against expected outcomes,
 * and generate criterion test results accordingly.
 */
@Slf4j
@AllArgsConstructor
public class SnmpGetNextCriterionProcessor {

	@NonNull
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@NonNull
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	/**
	 * Pattern used to verify the SNMP GetNext value
	 */
	private static final Pattern SNMP_GET_NEXT_VALUE_PATTERN = Pattern.compile("\\w+\\s+\\w+\\s+(.*)");

	/**
	 * Processes an SNMP Get criterion by executing an SNMP Get request and evaluating the result.
	 * The method retrieves SNMP configuration, executes the SNMP Get request for the specified OID,
	 * and checks the result against the expected value defined in the criterion. It then returns a
	 * {@link CriterionTestResult} indicating the success or failure of the criterion evaluation.
	 *
	 * @param snmpGetNextCriterion The criterion including the OID to query and the expected result.
	 * @param connectorId          The connector identifier used for logging.
	 * @param telemetryManager     The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return A {@link CriterionTestResult} representing the outcome of the criterion evaluation.
	 */
	public CriterionTestResult process(
		final SnmpGetNextCriterion snmpGetNextCriterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (snmpGetNextCriterion == null) {
			log.error(
				"Hostname {} - Malformed SNMP GetNext criterion {}. Cannot process SNMP GetNext detection. Connector ID: {}.",
				telemetryManager.getHostname(),
				snmpGetNextCriterion,
				connectorId
			);
			return CriterionTestResult.empty();
		}

		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			log.debug(
				"Hostname {} - The SNMP credentials are not configured. Cannot process SNMP GetNext criterion {}. Connector ID: {}.",
				telemetryManager.getHostname(),
				snmpGetNextCriterion,
				connectorId
			);
			return CriterionTestResult.empty();
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			final String result = snmpRequestExecutor.executeSNMPGetNext(
				snmpGetNextCriterion.getOid(),
				snmpConfiguration,
				hostname,
				false
			);

			final CriterionTestResult criterionTestResult = checkSNMPGetNextResult(
				hostname,
				snmpGetNextCriterion.getOid(),
				snmpGetNextCriterion.getExpectedResult(),
				result
			);

			criterionTestResult.setResult(result);

			return criterionTestResult;
		} catch (final Exception e) { // NOSONAR on interruption
			final String message = String.format(
				"Hostname %s - SNMP test failed - SNMP GetNext of %s was unsuccessful due to an exception. Message: %s. Connector ID: %s.",
				hostname,
				snmpGetNextCriterion.getOid(),
				e.getMessage(),
				connectorId
			);
			log.debug(message, e);
			return CriterionTestResult.builder().message(message).build();
		}
	}

	/**
	 * Simply check the value consistency and verify whether the returned OID is
	 * under the same tree of the requested OID.
	 *
	 * @param hostname The hostname.
	 * @param oid      The SNMP OID.
	 * @param result   The result of the SNMP GetNext operation.
	 * @return {@link CriterionTestResult} wrapping the message and the success status.
	 */
	static CriterionTestResult checkSNMPGetNextValue(final String hostname, final String oid, final String result) {
		String message;
		boolean success = false;
		if (result == null) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of %s was unsuccessful due to a null result.",
					hostname,
					oid
				);
		} else if (result.isBlank()) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of %s was unsuccessful due to an empty result.",
					hostname,
					oid
				);
		} else if (!result.startsWith(oid)) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of %s was successful but the returned OID is not under the same tree." +
					" Returned OID: %s.",
					hostname,
					oid,
					result.split("\\s")[0]
				);
		} else {
			message =
				String.format("Hostname %s - Successful SNMP GetNext of %s. Returned result: %s.", hostname, oid, result);
			success = true;
		}

		log.debug(message);

		return CriterionTestResult.builder().message(message).success(success).build();
	}

	/**
	 * Check if the result matches the expected value.
	 *
	 * @param hostname The hostname of the resource we currently try to monitor.
	 * @param oid       The SNMP OID.
	 * @param expected  The expected value.
	 * @param result    The result of the SNMP GetNext operation.
	 * @return {@link CriterionTestResult} wrapping the message and the success status.
	 */
	static CriterionTestResult checkSNMPGetNextExpectedValue(
		final String hostname,
		final String oid,
		final String expected,
		final String result
	) {
		String message;
		boolean success = true;
		if (result == null) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of %s was unsuccessful due to a null result.",
					hostname,
					oid
				);
			success = false;
		} else {
			final Matcher matcher = SNMP_GET_NEXT_VALUE_PATTERN.matcher(result);
			if (matcher.find()) {
				final String value = matcher.group(1);
				final Pattern pattern = Pattern.compile(
					PslUtils.psl2JavaRegex(expected),
					Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
				);
				if (!pattern.matcher(value).find()) {
					message =
						String.format(
							"Hostname %s - SNMP test failed - SNMP GetNext of %s was successful but the value of the returned OID did not match" +
							" with the expected result. ",
							hostname,
							oid
						);
					message += String.format("Expected value: %s - returned value %s.", expected, value);
					success = false;
				} else {
					message =
						String.format("Hostname %s - Successful SNMP GetNext of %s. Returned result: %s.", hostname, oid, result);
				}
			} else {
				message =
					String.format(
						"Hostname %s - SNMP test failed - SNMP GetNext of %s was successful but the value cannot be extracted. ",
						hostname,
						oid
					);
				message += String.format("Returned result: %s.", result);
				success = false;
			}
		}

		log.debug(message);

		return CriterionTestResult.builder().message(message).success(success).build();
	}

	/**
	 * Verify the value returned by SNMP GetNext query. Check the value consistency
	 * when the expected output is not defined. Otherwise check if the value matches
	 * the expected regex.
	 *
	 * @param hostname The hostname of the resource we currently try to monitor.
	 * @param oid      The SNMP OID.
	 * @param expected The expected value.
	 * @param result   The result of the SNMP GetNext operation.
	 * @return {@link CriterionTestResult} wrapping the success status and the message
	 */
	static CriterionTestResult checkSNMPGetNextResult(
		final String hostname,
		final String oid,
		final String expected,
		final String result
	) {
		if (expected == null) {
			return checkSNMPGetNextValue(hostname, oid, result);
		}

		return checkSNMPGetNextExpectedValue(hostname, oid, expected, result);
	}
}
