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
import java.time.Duration;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ArrayHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.ipmi.client.FruList;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.SensorList;

/**
 * The IpmiRequestExecutor class is responsible for executing IPMI (Intelligent Platform Management Interface) requests to retrieve information about the chassis power state and sensors of a host.
 */
@Slf4j
public class IpmiRequestExecutor {

	/**
	 * Runs IPMI detection to determine the Chassis power state.
	 *
	 * @param hostname          The host name or IP address to query.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @return A string value, e.g., "System power state is up."
	 * @throws org.metricshub.ipmi.client.IpmiClientException If the BMC refuses the login, does not reply in time or
	 *                                                         fails the request.
	 */
	@WithSpan("IPMI Chassis Status")
	public String executeIpmiDetection(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) {
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

		final String result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			result = IpmiResultConverter.convertResult(bmc.chassisStatus());
		}

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
	 * Creates the IPMI client of one request. The {@link IpmiConfiguration#isSkipAuth() skipAuth} option uses cipher
	 * suite 3 without asking the BMC which suites it offers, as ipmi-java 1.x did.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration MetricsHub {@link IpmiConfiguration} instance including all the required fields to perform IPMI requests
	 * @return a new {@link IpmiClient}, to close once the request is done
	 */
	IpmiClient newClient(@NonNull final String hostname, @NonNull final IpmiConfiguration ipmiConfiguration) {
		final IpmiClient.Builder builder = IpmiClient.builder(hostname)
			// A null user name is the anonymous login
			.credentials(
				Objects.requireNonNullElse(ipmiConfiguration.getUsername(), ""),
				Objects.requireNonNullElse(ipmiConfiguration.getPassword(), new char[0])
			)
			.bmcKey(ArrayHelper.hexToByteArray(ipmiConfiguration.getBmcKey()))
			.timeout(Duration.ofSeconds(ipmiConfiguration.getTimeout()))
			// The client lives for one request: no keep-alive messages
			.keepAlive(Duration.ZERO);
		if (ipmiConfiguration.isSkipAuth()) {
			builder.cipherSuite(3);
		}
		return builder.build();
	}

	/**
	 * Logs what ipmi-java skipped or could not read without failing the request: ipmi-java does not log.
	 *
	 * @param hostname The host name or IP address of the BMC
	 * @param frus     The FRUs read from the BMC
	 * @param sensors  The sensors read from the BMC
	 */
	private static void logProblems(final String hostname, final FruList frus, final SensorList sensors) {
		frus.skippedRecords().forEach(skipped -> log.debug("Hostname {} - IPMI FRUs: Skipped {}", hostname, skipped));
		sensors.skippedRecords().forEach(skipped -> log.debug("Hostname {} - IPMI sensors: Skipped {}", hostname, skipped));
		frus.forEach(fru ->
			fru.problems().forEach(problem -> log.debug("Hostname {} - IPMI FRU {}: {}", hostname, fru.name(), problem))
		);
		sensors.forEach(sensor ->
			sensor
				.problem()
				.ifPresent(problem -> log.debug("Hostname {} - IPMI sensor {}: {}", hostname, sensor.name(), problem))
		);
	}

	/**
	 * Executes an IPMI Over-LAN request to retrieve information about all sensors.
	 *
	 * @param hostname          The host for which the {@link IpmiConfiguration} is set.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance containing the required fields for IPMI requests.
	 * @return A string containing information about FRUs, sensor states, and readings.
	 * @throws org.metricshub.ipmi.client.IpmiClientException If the BMC refuses the login, does not reply in time or
	 *                                                         fails the request.
	 */
	@WithSpan("IPMI Sensors")
	public String executeIpmiGetSensors(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) {
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

		final String result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			// One session, and one walk of the SDR repository for both
			final FruList frus = bmc.frus().execute();
			final SensorList sensors = bmc.sensors().execute();
			logProblems(hostname, frus, sensors);
			result = IpmiResultConverter.convertResult(frus, sensors);
		}

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
