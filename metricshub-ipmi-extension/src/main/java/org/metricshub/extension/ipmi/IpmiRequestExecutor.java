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
import org.metricshub.ipmi.client.BmcCapabilities;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.FruList;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientException;
import org.metricshub.ipmi.client.RawResponse;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SelEntryList;
import org.metricshub.ipmi.client.SelRequest;
import org.metricshub.ipmi.client.SensorList;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
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
		return newClient(hostname, ipmiConfiguration, PrivilegeLevel.User);
	}

	/**
	 * Creates the IPMI client of one request at the given privilege level: Operator for the chassis actions.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration MetricsHub {@link IpmiConfiguration} instance including all the required fields to perform IPMI requests
	 * @param privilege         The privilege level of the session
	 * @return a new {@link IpmiClient}, to close once the request is done
	 */
	IpmiClient newClient(
		@NonNull final String hostname,
		@NonNull final IpmiConfiguration ipmiConfiguration,
		@NonNull final PrivilegeLevel privilege
	) {
		final IpmiClient.Builder builder = IpmiClient.builder(hostname)
			.privilege(privilege)
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
	 * Reads the sensors to name those of the SEL entries without failing the request: the entries are worth
	 * returning without their sensor names when the SDR repository cannot be walked.
	 *
	 * @param hostname The host name or IP address of the BMC
	 * @param bmc      The client of the request
	 * @return the sensors, or {@code null} when the BMC did not give them
	 */
	private static SensorList sensorsOrNull(final String hostname, final IpmiClient bmc) {
		try {
			return bmc.sensors().execute();
		} catch (IpmiClientException e) {
			log.warn(
				"Hostname {} - IPMI sensors not read, the SEL entries are returned without sensor names: {}",
				hostname,
				e.getMessage()
			);
			log.debug("Hostname {} - IPMI sensors error:", hostname, e);
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
	 * The network function of the storage commands (IPMI 2.0 Table 5-1)
	 */
	private static final int STORAGE_NETFN = 0x0A;

	/**
	 * Get SEL Entry (IPMI 2.0 Table 31-2)
	 */
	private static final int GET_SEL_ENTRY = 0x43;

	/**
	 * Get SEL Entry request for the last entry of the log: no reservation, record ID FFFFh, whole record
	 */
	private static final int[] LAST_SEL_ENTRY_REQUEST = { 0x00, 0x00, 0xFF, 0xFF, 0x00, 0xFF };

	/**
	 * Completion code CBh, "requested sensor, data, or record not present": the answer of an empty log
	 */
	private static final int RECORD_NOT_PRESENT = 0xCB;

	/**
	 * Probes the BMC without credentials: Get Channel Authentication Capabilities and Get Channel Cipher Suites,
	 * outside any session.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @return what the BMC supports, as text
	 * @throws IpmiClientException If the BMC does not answer or does not support IPMI 2.0.
	 */
	@WithSpan("IPMI Probe")
	public String executeIpmiProbe(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) {
		final long startTime = System.currentTimeMillis();

		final BmcCapabilities capabilities;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			capabilities = bmc.capabilities();
		}

		log.debug("Hostname {} - IPMI probe in {} ms: {}", hostname, System.currentTimeMillis() - startTime, capabilities);

		return capabilities.toString();
	}

	/**
	 * The cursor of an ipmiSel source: the last entry returned, or the record ID and the bytes of the last entry when
	 * the first poll located the end of the log without reading it. Either way the next poll tells a log cleared and
	 * filled again up to the same ID from a log that only grew: ipmi-java compares the entry, the extension the bytes.
	 *
	 * @param entry    The last entry returned, or {@code null} before the first entry is returned
	 * @param recordId The record ID of the last entry, 0 when the log was empty
	 * @param record   The bytes of the last entry as the first poll read them, {@code null} with an entry or an
	 *                 empty log
	 */
	public record SelCursor(SelEntry entry, int recordId, byte[] record) {
		/**
		 * @param entry The last entry returned
		 * @return the cursor at that entry
		 */
		public static SelCursor of(final SelEntry entry) {
			return new SelCursor(entry, entry.id(), null);
		}
	}

	/**
	 * Locates the end of the System Event Log without reading the log: Get SEL Entry of record FFFFh, the last one.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @return the cursor at the last entry: its record ID and bytes, record ID 0 when the log is empty
	 * @throws IpmiClientException If the BMC refuses the login, does not reply in time or fails the request.
	 */
	@WithSpan("IPMI SEL End")
	public SelCursor executeIpmiSelEnd(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) {
		final RawResponse response;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			response = bmc.raw(STORAGE_NETFN, GET_SEL_ENTRY).data(LAST_SEL_ENTRY_REQUEST).execute();
		}

		// Next record ID (2 bytes), then the record, which starts with its ID
		final byte[] data = response.data();
		if (response.rawCompletionCode() == RECORD_NOT_PRESENT || (response.rawCompletionCode() == 0 && data.length < 4)) {
			log.debug("Hostname {} - The IPMI SEL has no last entry: {}", hostname, response);
			return new SelCursor(null, 0, null);
		}
		if (response.rawCompletionCode() != 0) {
			// A busy BMC or a refused command is not an empty log: the next poll tries again
			throw new IpmiClientException("Get SEL Entry of the last record failed: " + response);
		}
		final int lastId = (data[2] & 0xFF) | ((data[3] & 0xFF) << 8);
		log.debug("Hostname {} - The last IPMI SEL entry is record {}", hostname, lastId);
		return new SelCursor(null, lastId, Arrays.copyOfRange(data, 2, data.length));
	}

	/**
	 * Whether the log still holds, byte for byte, the last entry a first poll located. ipmi-java makes the same
	 * check for a cursor made of an entry: a log cleared and filled again up to the same ID is read from its first
	 * entry.
	 *
	 * @param bmc   The client of the request
	 * @param since The cursor of the first poll
	 * @return whether the record of the cursor is still in the log, unchanged
	 * @throws IpmiClientException If the BMC fails the request otherwise than with "record not present": a busy BMC
	 *                             is not a cleared log, the next poll tries again
	 */
	private static boolean holds(final IpmiClient bmc, final SelCursor since) {
		final RawResponse response = bmc
			.raw(STORAGE_NETFN, GET_SEL_ENTRY)
			.data(0x00, 0x00, since.recordId() & 0xFF, since.recordId() >> 8, 0x00, 0xFF)
			.execute();
		if (response.rawCompletionCode() != 0 && response.rawCompletionCode() != RECORD_NOT_PRESENT) {
			throw new IpmiClientException("Get SEL Entry of record " + since.recordId() + " failed: " + response);
		}
		final byte[] data = response.data();
		final byte[] record = since.record();
		final boolean held =
			response.rawCompletionCode() == 0 &&
			record != null &&
			data.length >= 2 + record.length &&
			Arrays.equals(data, 2, 2 + record.length, record, 0, record.length);
		if (!held) {
			log.debug(
				"The IPMI SEL no longer holds record {} as first read, the whole log is read: {}",
				since.recordId(),
				response
			);
		}
		return held;
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
	 * Reads the System Event Log entries added after the cursor: the whole log when the cursor is {@code null} or
	 * at an empty log, or when the log no longer holds the cursor (it was cleared).
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @param since             The cursor of the previous read, or {@code null} for the whole log
	 * @param withSensors       Whether to read the sensors too, to name the sensors of the system events read
	 * @return the entries, with the sensors when asked for and at least one system event was read
	 * @throws IpmiClientException If the BMC refuses the login, does not reply in time or fails the request.
	 */
	@WithSpan("IPMI SEL")
	public SelRead executeIpmiSel(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration,
		SelCursor since,
		boolean withSensors
	) {
		final long startTime = System.currentTimeMillis();

		final SelRead result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration)) {
			final SelRequest request = bmc.sel();
			if (since != null && since.entry() != null) {
				request.since(since.entry());
			} else if (since != null && since.recordId() > 0 && holds(bmc, since)) {
				request.since(since.recordId());
			}
			final SelEntryList entries = request.execute();
			// ponytail: naming the sensors of the new events walks the whole SDR and reads every sensor; a
			// records-only walk needs an ipmi-java API
			final SensorList sensors =
				withSensors && entries.stream().anyMatch(entry -> entry.type() == SelRecordType.System)
					? sensorsOrNull(hostname, bmc)
					: null;
			result = new SelRead(entries, sensors);
		}

		log.debug(
			"Hostname {} - Read {} IPMI SEL entries{} in {} ms",
			hostname,
			result.entries().size(),
			since == null ? "" : " after record " + since.recordId(),
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
		// At the highest level of the account: a raw command may need Operator or Administrator
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration, PrivilegeLevel.MaximumAvailable)) {
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
	 * Decodes a decimal or {@code 0x} hexadecimal number of an ipmiRaw source. A leading zero does not make the
	 * number octal: {@code 010} is 10.
	 *
	 * @param value The value
	 * @return the number
	 * @throws IllegalArgumentException If the value is blank or not a number
	 */
	private static int decode(final String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("An ipmiRaw source needs a netFn and a command");
		}
		final String number = value.trim();
		return number.startsWith("0x") || number.startsWith("0X")
			? Integer.parseInt(number.substring(2), 16)
			: Integer.parseInt(number, 10);
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
		return Arrays.stream(data.trim().split(" +")).mapToInt(IpmiRequestExecutor::decode).toArray();
	}

	/**
	 * Sends a power command to the chassis (Chassis Control) and reads the power state afterwards. Needs the
	 * Operator privilege: the session is opened at the highest level of the user.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @param command           The power command
	 * @return the power state read after the command, e.g. "System power state is up"
	 * @throws IpmiClientException   If the BMC refuses the login or the command, or does not reply in time.
	 * @throws IllegalStateException For a power cycle of a system that is off
	 */
	@WithSpan("IPMI Chassis Control")
	public String executeIpmiChassisControl(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration,
		@SpanAttribute("ipmi.command") @NonNull PowerCommand command
	) {
		log.info("Hostname {} - Sending the IPMI chassis control command {}", hostname, command);
		final String result;
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration, PrivilegeLevel.Operator)) {
			bmc.chassisControl(command);
			result = IpmiResultConverter.convertResult(bmc.chassisStatus());
		}
		log.info("Hostname {} - IPMI chassis control command {} sent: {}", hostname, command, result);
		return result;
	}

	/**
	 * Turns the chassis identify (typically a blinking LED) on for the given number of seconds, on until turned
	 * off, or off. Needs the Operator privilege.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @param seconds           How long the identify stays on, 1 to 255; 0 turns it off; ignored when {@code forever}
	 * @param forever           Whether the identify stays on until turned off (not every BMC implements it)
	 * @return what was done, e.g. "Chassis identify on for 15 s"
	 * @throws IpmiClientException      If the BMC refuses the login or the command, or does not reply in time.
	 * @throws IllegalArgumentException When {@code seconds} is not between 0 and 255
	 */
	@WithSpan("IPMI Chassis Identify")
	public String executeIpmiChassisIdentify(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration,
		@SpanAttribute("ipmi.seconds") int seconds,
		@SpanAttribute("ipmi.forever") boolean forever
	) {
		final String result = forever
			? "Chassis identify on until turned off"
			: seconds == 0
				? "Chassis identify off"
				: "Chassis identify on for " + seconds + " s";
		log.info("Hostname {} - IPMI chassis identify: {}", hostname, result);
		try (IpmiClient bmc = newClient(hostname, ipmiConfiguration, PrivilegeLevel.Operator)) {
			if (forever) {
				bmc.chassisIdentifyForever();
			} else {
				bmc.chassisIdentify(seconds);
			}
		}
		return result;
	}
}
