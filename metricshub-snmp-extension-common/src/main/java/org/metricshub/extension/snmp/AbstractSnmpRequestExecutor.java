package org.metricshub.extension.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension Common
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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.snmp.client.ISnmpClient;
import org.metricshub.snmp.client.SnmpClient;

/**
 * Abstract class for executing SNMP (Simple Network Management Protocol) requests
 * on a specified host.
 */
@Slf4j
public abstract class AbstractSnmpRequestExecutor {

	/**
	 * Create an SNMPClient based on the provided configuration and hostname.
	 *
	 * @param configuration The SNMP configuration containing connection details.
	 * @param hostname      The hostname or IP address of the SNMP-enabled device.
	 * @param emulationInputDirectory Snmp emulation input file
	 * @throws RuntimeException If an {@link IOException} is thrown during the creation of the {@link SnmpClient}
	 * @return The created SnmpClient {@link SnmpClient}.
	 * @throws IOException If an error occurs during the creation of the {@link SnmpClient}.
	 */
	protected abstract ISnmpClient createSnmpClient(
		ISnmpConfiguration configuration,
		String hostname,
		String emulationInputDirectory
	) throws IOException;

	/**
	 * Execute SNMP GetNext request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP GETNEXT request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @param emulationInputDirectory Snmp emulation input directory
	 * @return The SNMP response as a String value.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException  If an exception occurs during execution.
	 * @throws TimeoutException    If the execution times out.
	 */
	@WithSpan("SNMP Get Next")
	public String executeSNMPGetNext(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final ISnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode,
		final String emulationInputDirectory
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP GetNext request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = executeSnmpGetRequest(
			SnmpGetRequest.GETNEXT,
			oid,
			configuration,
			hostname,
			null,
			logMode,
			emulationInputDirectory
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace(
				"Executed SNMP GetNext request:\n- OID: {}\n- Result: {}\n- response-time: {}\n",
				oid,
				result,
				responseTime
			)
		);

		return result;
	}

	/**
	 * Execute SNMP Get request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP GET request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @param emulationInputDirectory Snmp emulation input directory
	 * @return The SNMP response as a String value.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException  If an exception occurs during execution.
	 * @throws TimeoutException    If the execution times out.
	 */
	@WithSpan("SNMP Get")
	public String executeSNMPGet(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final ISnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode,
		final String emulationInputDirectory
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP Get request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = executeSnmpGetRequest(
			SnmpGetRequest.GET,
			oid,
			configuration,
			hostname,
			null,
			logMode,
			emulationInputDirectory
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace("Executed SNMP Get request:\n- OID: {}\n- Result: {}\n- response-time: {}\n", oid, result, responseTime)
		);

		return result;
	}

	/**
	 * Execute SNMP Table
	 *
	 * @param oid               The SNMP Object Identifier (OID) representing the table.
	 * @param selectColumnArray An array of column names to select from the SNMP table.
	 * @param configuration     The SNMP configuration containing connection details.
	 * @param hostname          The hostname or IP address of the SNMP-enabled device.
	 * @param logMode           Flag indicating whether to log warnings in case of errors.
	 * @param emulationInputDirectory Snmp emulation input directory
	 * @return A list of rows, where each row is a list of string cells representing the SNMP table.
	 * @throws InterruptedException If the thread executing this method is interrupted.
	 * @throws ExecutionException  If an exception occurs during the execution of the SNMP request.
	 * @throws TimeoutException    If the SNMP request times out.
	 */
	@WithSpan("SNMP Get Table")
	public List<List<String>> executeSNMPTable(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.columns") String[] selectColumnArray,
		@NonNull @SpanAttribute("snmp.config") final ISnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode,
		final String emulationInputDirectory
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() ->
			log.trace("Executing SNMP Table request:\n- OID: {}\n- Columns: {}\n", oid, Arrays.toString(selectColumnArray))
		);

		final long startTime = System.currentTimeMillis();

		List<List<String>> result = executeSnmpGetRequest(
			SnmpGetRequest.TABLE,
			oid,
			configuration,
			hostname,
			selectColumnArray,
			logMode,
			emulationInputDirectory
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace(
				"Executed SNMP Table request:\n- OID: {}\n- Columns: {}\n- Result:\n{}\n- response-time: {}\n",
				oid,
				Arrays.toString(selectColumnArray),
				TextTableHelper.generateTextTable(selectColumnArray, result),
				responseTime
			)
		);

		return result;
	}

	/**
	 * Execute SNMP request.
	 *
	 * @param request           The type of SNMP request (GET, GETNEXT, TABLE).
	 * @param oid               The SNMP Object Identifier (OID) for the request.
	 * @param protocol     The SNMP configuration containing connection details.
	 * @param hostname          The hostname or IP address of the SNMP-enabled device.
	 * @param selectColumnArray An array of column names for TABLE requests.
	 * @param logMode           Flag indicating whether to log warnings in case of errors.
	 * @param emulationInputDirectory Snmp emulation input directory
	 * @param <T>               The type of result to return.
	 * @return The result of the SNMP request, which can be a single value, a table, or null if an error occurs.
	 * @throws InterruptedException If the thread executing this method is interrupted.
	 * @throws ExecutionException  If an exception occurs during the execution of the SNMP request.
	 * @throws TimeoutException    If the SNMP request times out.
	 */
	@SuppressWarnings("unchecked")
	protected <T> T executeSnmpGetRequest(
		final SnmpGetRequest request,
		final String oid,
		final ISnmpConfiguration protocol,
		final String hostname,
		final String[] selectColumnArray,
		final boolean logMode,
		final String emulationInputDirectory
	) throws InterruptedException, ExecutionException, TimeoutException {
		return (T) ThreadHelper.execute(
			() -> {
				final ISnmpClient snmpClient = createSnmpClient(protocol, hostname, emulationInputDirectory);
				try {
					switch (request) {
						case GET:
							return snmpClient.get(oid);
						case GETNEXT:
							return snmpClient.getNext(oid);
						case TABLE:
							return snmpClient.table(oid, selectColumnArray);
						case WALK:
							return snmpClient.walk(oid);
						default:
							throw new IllegalArgumentException("Not implemented.");
					}
				} catch (Exception e) {
					if (logMode) {
						log.warn(
							"Hostname {} - Error detected when running SNMP {} Query OID: {}. Error message: {}.",
							hostname,
							request,
							oid,
							e.getMessage()
						);
					}
					return null;
				} finally {
					snmpClient.freeResources();
				}
			},
			protocol.getTimeout()
		);
	}

	/**
	 * Enum representing different types of SNMP requests.
	 * These requests are used to specify the type of SNMP operation
	 * when interacting with SNMP agents.
	 */
	public enum SnmpGetRequest {
		/**
		 * Represents an SNMP GET request.
		 * Used to retrieve the value of a single SNMP object.
		 */
		GET,
		/**
		 * Represents an SNMP GETNEXT request.
		 * Used to retrieve the value of the next SNMP object.
		 */
		GETNEXT,
		/**
		 * Represents an SNMP TABLE request.
		 * Used to retrieve a table of SNMP objects.
		 */
		TABLE,
		/**
		 * Represents an SNMP WALK request.
		 * Used to traverse and retrieve a sequence of SNMP objects starting from a given OID.
		 */
		WALK
	}

	@WithSpan("SNMP Walk")
	public String executeSNMPWalk(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final ISnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode,
		final String emulationInputDirectory
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP Walk request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = executeSnmpGetRequest(
			SnmpGetRequest.WALK,
			oid,
			configuration,
			hostname,
			null,
			logMode,
			emulationInputDirectory
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		LoggingHelper.trace(() ->
			log.trace(
				"Executed SNMP Walk request:\n- OID: {}\n- Result: {}\n- response-time: {}\n",
				oid,
				result,
				responseTime
			)
		);

		return result;
	}
}
