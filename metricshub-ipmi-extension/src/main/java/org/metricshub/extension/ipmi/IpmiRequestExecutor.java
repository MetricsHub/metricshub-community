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
import java.util.Arrays;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ArrayHelper;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.FruList;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientException;
import org.metricshub.ipmi.client.RawResponse;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SelEntryList;
import org.metricshub.ipmi.client.SelRequest;
import org.metricshub.ipmi.client.SensorList;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecordType;

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
	 * @throws IpmiClientException If the BMC refuses the login, does not reply in time or fails the request.
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
	 * Reads the chassis status without failing the request: the FRUs and the sensors are worth collecting from a BMC
	 * that refuses or does not answer Get Chassis Status.
	 *
	 * @param hostname The host name or IP address of the BMC
	 * @param bmc      The client of the request
	 * @return the chassis status, or {@code null} when the BMC did not give it
	 */
	private static ChassisStatus chassisStatusOrNull(final String hostname, final IpmiClient bmc) {
		try {
			return bmc.chassisStatus();
		} catch (IpmiClientException e) {
			log.warn(
				"Hostname {} - IPMI chassis status not read, the Chassis line is left out: {}",
				hostname,
				e.getMessage()
			);
			log.debug("Hostname {} - IPMI chassis status error:", hostname, e);
			return null;
		}
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
	 * @return A string containing the chassis status, the FRUs, the sensor states and the readings.
	 * @throws IpmiClientException If the BMC refuses the login, does not reply in time or fails the request.
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
			// One session for the three requests, and one walk of the SDR repository for the FRUs and the sensors
			final ChassisStatus chassisStatus = chassisStatusOrNull(hostname, bmc);
			final FruList frus = bmc.frus().execute();
			final SensorList sensors = bmc.sensors().execute();
			logProblems(hostname, frus, sensors);
			result = IpmiResultConverter.convertResult(chassisStatus, frus, sensors);
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

	/**
	 * The entries of one SEL read, with the sensors of the SDR repository when the sensors of the entries were to be
	 * named, {@code null} otherwise.
	 *
	 * @param entries The entries read
	 * @param sensors The sensors of the BMC, or {@code null}
	 */
	public record SelRead(SelEntryList entries, SensorList sensors) {}

	/**
	 * Reads the System Event Log entries added after {@code since}: the whole log when {@code since} is
	 * {@code null}, or when the log no longer holds it (it was cleared).
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @param since             The last entry of the previous read, or {@code null} for the whole log
	 * @param withSensors       Whether to read the sensors too, to name the sensors of the system events read
	 * @return the entries, with the sensors when asked for and at least one system event was read
	 * @throws IpmiClientException If the BMC refuses the login, does not reply in time or fails the request.
	 */
	@WithSpan("IPMI SEL")
	public SelRead executeIpmiSel(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration,
		SelEntry since,
		boolean withSensors
	) {
		final long startTime = System.currentTimeMillis();

		final SelRead result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			final SelRequest request = bmc.sel();
			if (since != null) {
				request.since(since);
			}
			final SelEntryList entries = request.execute();
			// ponytail: naming the sensors of the new events walks the whole SDR and reads every sensor; a
			// records-only walk needs an ipmi-java API
			final SensorList sensors =
				withSensors && entries.stream().anyMatch(entry -> entry.type() == SelRecordType.System)
					? bmc.sensors().execute()
					: null;
			result = new SelRead(entries, sensors);
		}

		log.debug(
			"Hostname {} - Read {} IPMI SEL entries{} in {} ms",
			hostname,
			result.entries().size(),
			since == null ? "" : " after record " + since.id(),
			System.currentTimeMillis() - startTime
		);

		return result;
	}

	/**
	 * Sends the raw command of an ipmiRaw source.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @param source            The source: its network function, command and data bytes
	 * @return {@code $completionCode;$data}, see {@link IpmiResultConverter#convertResult(RawResponse)}
	 * @throws IpmiClientException      If the BMC refuses the login, does not reply in time or fails the request.
	 * @throws IllegalArgumentException If the source does not hold valid numbers
	 */
	@WithSpan("IPMI Raw")
	public String executeIpmiRaw(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration,
		@NonNull IpmiRawSource source
	) {
		final int netFn = decode(source.getNetFn());
		final int command = decode(source.getCommand());
		final int[] data = dataBytes(source.getData());

		final long startTime = System.currentTimeMillis();

		final String result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			result = IpmiResultConverter.convertResult(bmc.raw(netFn, command).data(data).execute());
		}

		log.debug(
			"Hostname {} - IPMI raw request {} returned {} in {} ms",
			hostname,
			IpmiResultConverter.rawQuery(netFn, command, data),
			result,
			System.currentTimeMillis() - startTime
		);

		return result;
	}

	/**
	 * The request of an ipmiRaw source as {@code $netFn $command[ $data]} in uppercase hex bytes: the key of its
	 * recording.
	 *
	 * @param source The source
	 * @return the request bytes
	 * @throws IllegalArgumentException If the source does not hold valid numbers
	 */
	public static String rawQuery(@NonNull final IpmiRawSource source) {
		return IpmiResultConverter.rawQuery(
			decode(source.getNetFn()),
			decode(source.getCommand()),
			dataBytes(source.getData())
		);
	}

	/**
	 * Decodes a decimal or {@code 0x} hexadecimal number of an ipmiRaw source.
	 *
	 * @param value The value
	 * @return the number
	 * @throws IllegalArgumentException If the value is blank or not a number
	 */
	private static int decode(final String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("An ipmiRaw source needs a netFn and a command");
		}
		return Integer.decode(value.trim());
	}

	/**
	 * Splits the data bytes of an ipmiRaw source, separated with spaces.
	 *
	 * @param data The data bytes, possibly {@code null} or blank
	 * @return the bytes, empty when there are none
	 * @throws IllegalArgumentException If a byte is not a number
	 */
	private static int[] dataBytes(final String data) {
		if (data == null || data.isBlank()) {
			return new int[0];
		}
		return Arrays.stream(data.trim().split(" +")).mapToInt(Integer::decode).toArray();
	}
}
