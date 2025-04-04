package org.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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
import java.util.Map;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.HttpRequest;

/**
 * This class is responsible for executing HTTP tests based on the provided
 * {@link HttpCriterion} and generating {@link CriterionTestResult} based on the
 * outcome of these tests. It utilizes an {@link HttpRequestExecutor} to perform
 * the actual HTTP requests.
 */
@Slf4j
@AllArgsConstructor
public class HttpCriterionProcessor {

	private HttpRequestExecutor httpRequestExecutor;

	private static final String HTTP_TEST_SUCCESS = "Hostname %s - HTTP test succeeded. Returned result: %s.";

	/**
	 * Process the given {@link HttpCriterion} through Client and return the {@link CriterionTestResult}
	 *
	 * @param httpCriterion    The HTTP criterion to process.
	 * @param connectorId      The Id of the connector
	 * @param telemetryManager The telemetry manager providing access to host configuration.
	 * @return New {@link CriterionTestResult} instance.
	 */
	public CriterionTestResult process(
		final HttpCriterion httpCriterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();

		if (hostConfiguration == null) {
			log.debug("There is no host configuration. Cannot process HTTP detection {}.", httpCriterion);
			return CriterionTestResult.empty();
		}

		// Retrieve the hostname from the HttpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(HttpConfiguration.class));

		if (httpCriterion == null) {
			log.error(
				"Hostname {} - Malformed SNMP Get criterion {}. Cannot process SNMP Get detection. Connector ID: {}.",
				hostname,
				httpCriterion,
				connectorId
			);
			return CriterionTestResult.empty();
		}

		final HttpConfiguration httpConfiguration = (HttpConfiguration) hostConfiguration
			.getConfigurations()
			.get(HttpConfiguration.class);

		if (httpConfiguration == null) {
			log.debug(
				"Hostname {} - The HTTP credentials are not configured for this host. Cannot process HTTP detection {}.",
				hostname,
				httpCriterion
			);
			return CriterionTestResult.empty();
		}

		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = telemetryManager.getEmbeddedFiles(connectorId);

		try {
			final String result = httpRequestExecutor.executeHttp(
				HttpRequest
					.builder()
					.hostname(hostname)
					.method(httpCriterion.getMethod().toString())
					.url(httpCriterion.getUrl())
					.path(httpCriterion.getPath())
					.header(httpCriterion.getHeader(), connectorEmbeddedFiles, connectorId, hostname)
					.body(httpCriterion.getBody(), connectorEmbeddedFiles, connectorId, hostname)
					.httpConfiguration(httpConfiguration)
					.resultContent(httpCriterion.getResultContent())
					.authenticationToken(httpCriterion.getAuthenticationToken())
					.build(),
				false,
				telemetryManager
			);

			return checkHttpResult(hostname, result, httpCriterion.getExpectedResult());
		} catch (Exception e) {
			return CriterionTestResult.error(httpCriterion, e);
		}
	}

	/**
	 * @param hostname       The hostname against which the HTTP test has been carried out.
	 * @param result         The actual result of the HTTP test.
	 * @param expectedResult The expected result of the HTTP test.
	 * @return A {@link CriterionTestResult} summarizing the outcome of the HTTP test.
	 */
	private CriterionTestResult checkHttpResult(final String hostname, final String result, final String expectedResult) {
		String message;
		boolean success = false;

		if (expectedResult == null) {
			if (result == null || result.isEmpty()) {
				message = String.format("Hostname %s - HTTP test failed - The HTTP test did not return any result.", hostname);
			} else {
				message = String.format(HTTP_TEST_SUCCESS, hostname, result);
				success = true;
			}
		} else {
			// We convert the PSL regex from the expected result into a Java regex to be able to compile and test it
			final Pattern pattern = Pattern.compile(PslUtils.psl2JavaRegex(expectedResult), Pattern.CASE_INSENSITIVE);
			if (result != null && pattern.matcher(result).find()) {
				message = String.format(HTTP_TEST_SUCCESS, hostname, result);
				success = true;
			} else {
				message =
					String.format(
						"Hostname %s - HTTP test failed - The result (%s) returned by the HTTP test did not match the expected result (%s).",
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
