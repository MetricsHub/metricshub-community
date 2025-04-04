package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ipmi Extension
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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ArrayHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;

@Slf4j
public class IpmiRequestExecutor {

	/**
	 * Runs IPMI detection to determine the Chassis power state.
	 *
	 * @param hostname          The host name or IP address to query.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @return A string value, e.g., "System power state is up."
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException   If the execution encounters an exception.
	 * @throws TimeoutException     If the operation times out.
	 */
	@WithSpan("IPMI Chassis Status")
	public String executeIpmiDetection(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing IPMI detection request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + "- Timeout: {} s\n", // NOSONAR
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout()
			)
		);

		final long startTime = System.currentTimeMillis();

		final String result = IpmiClient.getChassisStatusAsStringResult(
			buildIpmiConfiguration(hostname, ipmiConfiguration)
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace(
				"Executed IPMI detection request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout(),
				result,
				responseTime
			)
		);

		return result;
	}

	/**
	 * Build IPMI configuration
	 *
	 * @param hostname          The host we wish to set in the {@link IpmiConfiguration}
	 * @param ipmiConfiguration MetricsHub {@link IpmiConfiguration} instance including all the required fields to perform IPMI requests
	 * @return new instance of {@link IpmiClientConfiguration}
	 */
	private static IpmiClientConfiguration buildIpmiConfiguration(
		@NonNull String hostname,
		@NonNull IpmiConfiguration ipmiConfiguration
	) {
		String username = ipmiConfiguration.getUsername();
		char[] password = ipmiConfiguration.getPassword();
		Long timeout = ipmiConfiguration.getTimeout();

		return new IpmiClientConfiguration(
			hostname,
			username,
			password,
			ArrayHelper.hexToByteArray(ipmiConfiguration.getBmcKey()),
			ipmiConfiguration.isSkipAuth(),
			timeout,
			0L // Turn off keep-alive messages sent to the remote host
		);
	}

	/**
	 * Executes an IPMI Over-LAN request to retrieve information about all sensors.
	 *
	 * @param hostname          The host for which the {@link IpmiConfiguration} is set.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance containing the required fields for IPMI requests.
	 * @return A string containing information about FRUs, sensor states, and readings.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException   If the execution encounters an exception.
	 * @throws TimeoutException     If the operation times out.
	 */
	@WithSpan("IPMI Sensors")
	public String executeIpmiGetSensors(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() ->
			log.trace(
				"Executing IPMI FRUs and Sensors request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout()
			)
		);

		final long startTime = System.currentTimeMillis();

		String result = IpmiClient.getFrusAndSensorsAsStringResult(buildIpmiConfiguration(hostname, ipmiConfiguration));

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace(
				"Executed IPMI FRUs and Sensors request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout(),
				result,
				responseTime
			)
		);

		return result;
	}
}
