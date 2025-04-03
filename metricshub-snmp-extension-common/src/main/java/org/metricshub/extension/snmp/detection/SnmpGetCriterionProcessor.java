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
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.PslUtils;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * A class responsible for processing SNMP Get criteria to evaluate SNMP queries against specified criteria.
 * It provides methods to perform SNMP Get operations, evaluate the results against expected outcomes,
 * and generate criterion test results accordingly.
 */
@Slf4j
@AllArgsConstructor
public class SnmpGetCriterionProcessor {

	@NonNull
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@NonNull
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	/**
	 * Processes an SNMP Get criterion by executing an SNMP Get request and evaluating the result.
	 * The method retrieves SNMP configuration, executes the SNMP Get request for the specified OID,
	 * and checks the result against the expected value defined in the criterion. It then returns a
	 * {@link CriterionTestResult} indicating the success or failure of the criterion evaluation.
	 *
	 * @param snmpGetCriterion The criterion including the OID to query and the expected result.
	 * @param connectorId      The connector identifier used for logging.
	 * @param telemetryManager The telemetry manager providing access to host configuration and SNMP credentials.
	 * @return A {@link CriterionTestResult} representing the outcome of the criterion evaluation.
	 */
	public CriterionTestResult process(
		final SnmpGetCriterion snmpGetCriterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (snmpGetCriterion == null) {
			log.error(
				"Hostname {} - Malformed SNMP Get criterion {}. Cannot process SNMP Get detection. Connector ID: {}.",
				telemetryManager.getHostname(),
				snmpGetCriterion,
				connectorId
			);
			return CriterionTestResult.empty();
		}

		// Find the configured protocol (Snmp or SnmpV3)
		final ISnmpConfiguration snmpConfiguration = configurationRetriever.apply(telemetryManager);

		if (snmpConfiguration == null) {
			log.debug(
				"Hostname {} - The SNMP credentials are not configured. Cannot process SNMP Get criterion {}. Connector ID: {}.",
				telemetryManager.getHostname(),
				snmpGetCriterion,
				connectorId
			);
			return CriterionTestResult.empty();
		}

		// Retrieve the hostname from the ISnmpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(snmpConfiguration.getClass()));

		try {
			final String result = snmpRequestExecutor.executeSNMPGet(
				snmpGetCriterion.getOid(),
				snmpConfiguration,
				hostname,
				false
			);

			final CriterionTestResult criterionTestResult = checkSNMPGetResult(
				hostname,
				snmpGetCriterion.getOid(),
				snmpGetCriterion.getExpectedResult(),
				result
			);

			criterionTestResult.setResult(result);

			return criterionTestResult;
		} catch (final Exception e) { // NOSONAR on interruption
			final String message = String.format(
				"Hostname %s - SNMP test failed - SNMP Get of %s was unsuccessful due to an exception. Message: %s. Connector ID: %s.",
				hostname,
				snmpGetCriterion.getOid(),
				e.getMessage(),
				connectorId
			);
			log.debug(message, e);
			return CriterionTestResult.builder().message(message).build();
		}
	}

	/**
	 * Simply check the value consistency and verify whether the returned value is
	 * not null or empty.
	 *
	 * @param hostname The hostname of the resource we currently try to monitor.
	 * @param oid      The SNMP OID.
	 * @param result   The result of the SNMP Get operation.
	 * @return {@link CriterionTestResult} wrapping the message and the success status.
	 */
	static CriterionTestResult checkSNMPGetValue(final String hostname, final String oid, final String result) {
		String message;
		boolean success = false;
		if (result == null) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get of %s was unsuccessful due to a null result",
					hostname,
					oid
				);
		} else if (result.isBlank()) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get of %s was unsuccessful due to an empty result.",
					hostname,
					oid
				);
		} else {
			message = String.format("Hostname %s - Successful SNMP Get of %s. Returned result: %s.", hostname, oid, result);
			success = true;
		}

		log.debug(message);

		return CriterionTestResult.builder().message(message).success(success).build();
	}

	/**
	 * Verify the value returned by SNMP Get query. Check the value consistency when
	 * the expected output is not defined. Otherwise check if the value matches the
	 * expected regex.
	 *
	 * @param hostname The hostname of the resource we currently try to monitor.
	 * @param oid      The SNMP OID.
	 * @param expected The expected value.
	 * @param result   The result of the SNMP Get operation.
	 * @return {@link CriterionTestResult} wrapping the success status and the message.
	 */
	static CriterionTestResult checkSNMPGetResult(
		final String hostname,
		final String oid,
		final String expected,
		final String result
	) {
		if (expected == null) {
			return checkSNMPGetValue(hostname, oid, result);
		}
		return checkSNMPGetExpectedValue(hostname, oid, expected, result);
	}

	/**
	 * Check if the result matches the expected value.
	 *
	 * @param hostname The hostname of the resource we currently try to monitor.
	 * @param oid      The SNMP OID.
	 * @param expected The expected value.
	 * @param result   The result of the SNMP Get operation.
	 * @return {@link CriterionTestResult} wrapping the message and the success status.
	 */
	static CriterionTestResult checkSNMPGetExpectedValue(
		final String hostname,
		final String oid,
		final String expected,
		final String result
	) {
		String message;
		boolean success = false;

		final Pattern pattern = Pattern.compile(
			PslUtils.psl2JavaRegex(expected),
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
		);
		if (result == null || !pattern.matcher(result).find()) {
			message =
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get of %s was successful but the value of the returned OID did not match with the" +
					" expected result. ",
					hostname,
					oid
				);
			message += String.format("Expected value: %s - returned value %s.", expected, result);
		} else {
			message = String.format("Hostname %s - Successful SNMP Get of %s. Returned result: %s", hostname, oid, result);
			success = true;
		}

		log.debug(message);

		return CriterionTestResult.builder().message(message).success(success).build();
	}
}
