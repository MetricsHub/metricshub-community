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
	 * Creates an SNMP client based on the provided configuration and hostname.
	 *
	 * @param configuration The SNMP configuration containing connection details.
	 * @param hostname      The hostname or IP address of the SNMP-enabled device.
	 * @return The created {@link SnmpClient}.
	 * @throws IOException If an error occurs during the creation of the {@link SnmpClient}.
	 */
	protected abstract ISnmpClient createSnmpClient(ISnmpConfiguration configuration, String hostname) throws IOException;

	/**
	 * Execute SNMP GetNext request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP GETNEXT request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @param resourceHostname        The HostConfiguration hostname for stats tracking, or {@code null} to skip tracking.
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
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP GetNext request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = runSnmpGetNext(oid, configuration, hostname, logMode, resourceHostname);

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
	 * @param resourceHostname        The HostConfiguration hostname for stats tracking, or {@code null} to skip tracking.
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
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP Get request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = runSnmpGet(oid, configuration, hostname, logMode, resourceHostname);

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
	 * @param resourceHostname        The HostConfiguration hostname for stats tracking, or {@code null} to skip tracking.
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
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() ->
			log.trace("Executing SNMP Table request:\n- OID: {}\n- Columns: {}\n", oid, Arrays.toString(selectColumnArray))
		);

		final long startTime = System.currentTimeMillis();

		final List<List<String>> result = runSnmpTable(
			oid,
			selectColumnArray,
			configuration,
			hostname,
			logMode,
			resourceHostname
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
	 * Executes a generic SNMP request operation.
	 *
	 * <p>This method centralizes common concerns for all SNMP operations:
	 * client creation, timeout-aware execution, optional stats tracking, error logging, and
	 * deterministic resource cleanup.</p>
	 *
	 * @param <T> The type of result to return.
	 * @param operation The operation to execute against the SNMP client.
	 * @param protocol The SNMP configuration containing connection details.
	 * @param hostname The hostname or IP address of the SNMP-enabled device.
	 * @param logMode Flag indicating whether to log warnings in case of errors.
	 * @param resourceHostname The HostConfiguration hostname for stats tracking, or {@code null} to skip tracking.
	 * @param requestName The request name used in warning/debug logs (e.g. {@code GET}, {@code TABLE}).
	 * @param oid The SNMP Object Identifier (OID) for the request.
	 * @return The result of the SNMP request, which can be a single value, a table, or {@code null} if an error occurs.
	 * @throws InterruptedException If the thread executing this method is interrupted.
	 * @throws ExecutionException  If an exception occurs during the execution of the SNMP request.
	 * @throws TimeoutException    If the SNMP request times out.
	 */
	private <T> T runSnmpRequest(
		final SnmpOperation<T> operation,
		final ISnmpConfiguration protocol,
		final String hostname,
		final boolean logMode,
		final String resourceHostname,
		final String requestName,
		final String oid
	) throws InterruptedException, ExecutionException, TimeoutException {
		final ISnmpClient[] clientHolder = new ISnmpClient[1];

		final java.util.concurrent.Callable<T> callable = () -> {
			final ISnmpClient snmpClient = createSnmpClient(protocol, hostname);
			clientHolder[0] = snmpClient;

			try {
				return operation.apply(snmpClient);
			} catch (Exception e) {
				if (logMode) {
					log.warn(
						"Hostname {} - Error detected when running SNMP {} Query OID: {}. Error message: {}.",
						hostname,
						requestName,
						oid,
						e.getMessage()
					);
				}
				return null;
			}
		};

		try {
			return resourceHostname != null
				? ThreadHelper.execute(callable, protocol.getTimeout(), resourceHostname, "snmp")
				: ThreadHelper.execute(callable, protocol.getTimeout());
		} finally {
			final ISnmpClient snmpClient = clientHolder[0];
			if (snmpClient != null) {
				try {
					snmpClient.freeResources();
				} catch (Exception e) {
					log.debug(
						"Hostname {} - Error while freeing SNMP client resources for {} query OID: {}. Error: {}.",
						hostname,
						requestName,
						oid,
						e.getMessage()
					);
				}
			}
		}
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

	/**
	 * Execute SNMP Walk request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP WALK request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @param resourceHostname        The HostConfiguration hostname for stats tracking, or {@code null} to skip tracking.
	 * @return The SNMP response as a String value.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException  If an exception occurs during execution.
	 * @throws TimeoutException    If the execution times out.
	 */
	@WithSpan("SNMP Walk")
	public String executeSNMPWalk(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final ISnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode,
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(() -> log.trace("Executing SNMP Walk request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = runSnmpWalk(oid, configuration, hostname, logMode, resourceHostname);

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

	/**
	 * Executes an SNMP {@code GET} operation.
	 *
	 * @param oid The OID to query.
	 * @param protocol SNMP configuration.
	 * @param hostname Target host.
	 * @param logMode Whether warnings should be logged on request failure.
	 * @param resourceHostname Hostname used for optional stats tracking.
	 * @return The SNMP value, or {@code null} when request execution fails.
	 * @throws InterruptedException If execution is interrupted.
	 * @throws ExecutionException If execution fails.
	 * @throws TimeoutException If execution times out.
	 */
	protected String runSnmpGet(
		final String oid,
		final ISnmpConfiguration protocol,
		final String hostname,
		final boolean logMode,
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		return runSnmpRequest(
			client -> client.get(oid),
			protocol,
			hostname,
			logMode,
			resourceHostname,
			SnmpGetRequest.GET.name(),
			oid
		);
	}

	/**
	 * Executes an SNMP {@code GETNEXT} operation.
	 *
	 * @param oid The OID to query.
	 * @param protocol SNMP configuration.
	 * @param hostname Target host.
	 * @param logMode Whether warnings should be logged on request failure.
	 * @param resourceHostname Hostname used for optional stats tracking.
	 * @return The SNMP value, or {@code null} when request execution fails.
	 * @throws InterruptedException If execution is interrupted.
	 * @throws ExecutionException If execution fails.
	 * @throws TimeoutException If execution times out.
	 */
	protected String runSnmpGetNext(
		final String oid,
		final ISnmpConfiguration protocol,
		final String hostname,
		final boolean logMode,
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		return runSnmpRequest(
			client -> client.getNext(oid),
			protocol,
			hostname,
			logMode,
			resourceHostname,
			SnmpGetRequest.GETNEXT.name(),
			oid
		);
	}

	/**
	 * Executes an SNMP {@code TABLE} operation.
	 *
	 * @param oid The table OID to query.
	 * @param selectColumnArray Requested table columns.
	 * @param protocol SNMP configuration.
	 * @param hostname Target host.
	 * @param logMode Whether warnings should be logged on request failure.
	 * @param resourceHostname Hostname used for optional stats tracking.
	 * @return The SNMP table rows, or {@code null} when request execution fails.
	 * @throws InterruptedException If execution is interrupted.
	 * @throws ExecutionException If execution fails.
	 * @throws TimeoutException If execution times out.
	 */
	protected List<List<String>> runSnmpTable(
		final String oid,
		final String[] selectColumnArray,
		final ISnmpConfiguration protocol,
		final String hostname,
		final boolean logMode,
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		return runSnmpRequest(
			client -> client.table(oid, selectColumnArray),
			protocol,
			hostname,
			logMode,
			resourceHostname,
			SnmpGetRequest.TABLE.name(),
			oid
		);
	}

	/**
	 * Executes an SNMP {@code WALK} operation.
	 *
	 * @param oid The root OID to walk.
	 * @param protocol SNMP configuration.
	 * @param hostname Target host.
	 * @param logMode Whether warnings should be logged on request failure.
	 * @param resourceHostname Hostname used for optional stats tracking.
	 * @return The SNMP walk output, or {@code null} when request execution fails.
	 * @throws InterruptedException If execution is interrupted.
	 * @throws ExecutionException If execution fails.
	 * @throws TimeoutException If execution times out.
	 */
	protected String runSnmpWalk(
		final String oid,
		final ISnmpConfiguration protocol,
		final String hostname,
		final boolean logMode,
		final String resourceHostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		return runSnmpRequest(
			client -> client.walk(oid),
			protocol,
			hostname,
			logMode,
			resourceHostname,
			SnmpGetRequest.WALK.name(),
			oid
		);
	}

	/**
	 * Functional contract describing a single SNMP operation to run using a provided client.
	 *
	 * @param <T> operation result type
	 */
	@FunctionalInterface
	private interface SnmpOperation<T> {
		/**
		 * Executes the operation with the given SNMP client.
		 *
		 * @param client active SNMP client
		 * @return operation result
		 * @throws Exception if execution fails
		 */
		T apply(ISnmpClient client) throws Exception;
	}
}
