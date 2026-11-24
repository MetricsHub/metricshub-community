package org.metricshub.extension.wbem;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Wbem Extension
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.vcenter.VCenterClient;
import org.metricshub.wbem.client.WbemExecutor;
import org.metricshub.wbem.client.WbemQueryResult;
import org.metricshub.wbem.client.exceptions.WqlQuerySyntaxException;
import org.metricshub.wbem.javax.wbem.WBEMException;

/**
 * Provides functionality to execute WBEM queries, including handling vCenter authentication when needed.
 */
@Slf4j
public class WbemRequestExecutor {

	/**
	 * Invalid namespace Exception. Thrown when the specified namespace does not
	 * exist.
	 */
	public static final int CIM_ERR_INVALID_NAMESPACE = 3;

	/**
	 * Invalid class specified. For e.g. when one tries to add an instance for a
	 * class that does not exist. This error message uses one parameter, the
	 * invalid class name.
	 */
	public static final int CIM_ERR_INVALID_CLASS = 5;

	/**
	 * Element cannot be found. This error message uses one parameter, the
	 * element that cannot be found.
	 */
	public static final int CIM_ERR_NOT_FOUND = 6;

	/**
	 * The {@code operation_type} of the WBEM requests (CIM-XML queries and vCenter tickets) in the request metrics.
	 */
	public static final String WBEM_OPERATION_TYPE = "wbem";

	/**
	 * Determine if a vCenter server is configured and call the appropriate method to run the WBEM query.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace         WBEM namespace
	 * @param telemetryManager  The telemetry manager providing host properties and recording the request outcomes.
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WBEM")
	public List<List<String>> executeWbem(
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		@NonNull @SpanAttribute("wbem.config") final WbemConfiguration wbemConfig,
		@NonNull @SpanAttribute("wbem.query") final String query,
		@NonNull @SpanAttribute("wbem.namespace") final String namespace,
		@NonNull final TelemetryManager telemetryManager
	) throws ClientException {
		List<List<String>> result;
		// handle vCenter case
		if (wbemConfig.getVCenter() != null) {
			result = doVCenterQuery(hostname, wbemConfig, query, namespace, telemetryManager);
		} else {
			result = doWbemQuery(hostname, wbemConfig, query, namespace, telemetryManager.getRequestStats());
		}

		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (recordOutputDirectory != null && !recordOutputDirectory.isBlank() && result != null) {
			WbemRecorder.getInstance(recordOutputDirectory).record(query, namespace, result);
		}

		return result;
	}

	/**
	 * Perform a WBEM query using vCenter ticket authentication.
	 * <br>
	 *
	 * @param hostname         Hostname
	 * @param wbemConfig       WBEM Protocol configuration, incl. credentials
	 * @param query            WQL query to execute
	 * @param namespace        WBEM namespace
	 * @param telemetryManager The telemetry manager providing host properties and recording the request outcomes.
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private List<List<String>> doVCenterQuery(
		@NonNull final String hostname,
		@NonNull final WbemConfiguration wbemConfig,
		@NonNull final String query,
		@NonNull final String namespace,
		@NonNull final TelemetryManager telemetryManager
	) throws ClientException {
		final RequestStats requestStats = telemetryManager.getRequestStats();
		String ticket = telemetryManager.getHostProperties().getVCenterTicket();

		if (ticket == null) {
			ticket = refreshVCenterTicket(
				wbemConfig.getVCenter(),
				wbemConfig.getUsername(),
				wbemConfig.getPassword(),
				hostname,
				wbemConfig.getTimeout(),
				requestStats
			);
		}

		final WbemConfiguration vCenterWbemConfig = WbemConfiguration.builder()
			.username(ticket)
			.password(ticket.toCharArray())
			.namespace(wbemConfig.getNamespace())
			.port(wbemConfig.getPort())
			.protocol(wbemConfig.getProtocol())
			.timeout(wbemConfig.getTimeout())
			.build();

		try {
			return doWbemQuery(hostname, vCenterWbemConfig, query, namespace, requestStats);
		} catch (ClientException e) {
			if (isRefreshTicketNeeded(e.getCause())) {
				ticket = refreshVCenterTicket(
					wbemConfig.getVCenter(),
					wbemConfig.getUsername(),
					wbemConfig.getPassword(),
					hostname,
					wbemConfig.getTimeout(),
					requestStats
				);
				vCenterWbemConfig.setUsername(ticket);
				vCenterWbemConfig.setPassword(ticket.toCharArray());
				return doWbemQuery(hostname, vCenterWbemConfig, query, namespace, requestStats);
			} else {
				throw e;
			}
		} finally {
			telemetryManager.getHostProperties().setVCenterTicket(ticket);
		}
	}

	/**
	 * Perform a query to a vCenterServer in order to obtain an authentication ticket.
	 * <br>
	 *
	 * @param vCenter  vCenter server FQDN or IP
	 * @param username Username
	 * @param password Password
	 * @param hostname         Hostname
	 * @param timeout          Timeout
	 * @param requestStats     Records the outcome of the ticket request, or {@code null} to record nothing.
	 * @return A ticket String
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private String refreshVCenterTicket(
		@NonNull String vCenter,
		@NonNull String username,
		@NonNull char[] password,
		@NonNull String hostname,
		@NonNull Long timeout,
		final RequestStats requestStats
	) throws ClientException {
		VCenterClient.setDebug(() -> true, log::debug);
		try {
			final String ticket = requestTicket(vCenter, username, password, hostname, timeout, requestStats);
			if (ticket == null) {
				throw new ClientException("Cannot get the ticket through vCenter module");
			}
			return ticket;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.error("Hostname {} - vCenter ticket refresh query failed. Error message: {}", hostname, e.getMessage());
			log.debug("Hostname {} - vCenter ticket refresh query failed.", hostname, e);
			throw new ClientException("vCenter refresh ticket query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Requests a ticket from the vCenter server, bounded by the timeout, and records the outcome of the request: a
	 * {@code null} ticket is a failure. A malformed vCenter URL, built locally before anything is sent, records
	 * nothing.
	 *
	 * @param vCenter      vCenter server FQDN or IP
	 * @param username     Username
	 * @param password     Password
	 * @param hostname     The hostname of the ESX host to request a ticket for
	 * @param timeout      Timeout in seconds
	 * @param requestStats Records the outcome of the request, or {@code null} to record nothing.
	 * @return The ticket, or {@code null} when the vCenter server returned none
	 * @throws InterruptedException when interrupted while waiting for the ticket
	 * @throws ExecutionException   when the ticket request failed (details in cause)
	 * @throws TimeoutException     when the ticket request timed out
	 */
	private static String requestTicket(
		final String vCenter,
		final String username,
		final char[] password,
		final String hostname,
		final long timeout,
		final RequestStats requestStats
	) throws InterruptedException, ExecutionException, TimeoutException {
		final String ticket;
		try {
			ticket = ThreadHelper.execute(
				() -> VCenterClient.requestCertificate(vCenter, username, new String(password), hostname),
				timeout
			);
		} catch (Exception e) {
			if (!(e instanceof ExecutionException && e.getCause() instanceof MalformedURLException)) {
				recordError(requestStats, e);
			}
			throw e;
		}
		record(requestStats, ticket != null ? RequestOutcome.COMPLETED : RequestOutcome.FAILED);
		return ticket;
	}

	/**
	 * Records the outcome of one WBEM request attempt.
	 *
	 * @param requestStats The request statistics of the resource, or {@code null} to record nothing.
	 * @param outcome      The outcome of the attempt.
	 */
	private static void record(final RequestStats requestStats, final RequestOutcome outcome) {
		if (requestStats != null) {
			requestStats.record(WBEM_OPERATION_TYPE, outcome);
		}
	}

	/**
	 * Records the outcome of a WBEM request attempt that threw, except a request cancelled before it started (an
	 * expired deadline of its job): nothing was sent.
	 *
	 * @param requestStats The request statistics of the resource, or {@code null} to record nothing.
	 * @param e            The exception thrown by the attempt.
	 */
	private static void recordError(final RequestStats requestStats, final Exception e) {
		if (!ThreadHelper.neverStarted(e)) {
			record(requestStats, RequestOutcome.of(e));
		}
	}

	/**
	 * Assess whether the exception (or any of its causes) is an access denied error saying that we must refresh the vCenter ticket.
	 * <br>
	 *
	 * @param t Exception to verify
	 * @return whether specified exception tells us that the ticket needs to be refreshed
	 */
	private static boolean isRefreshTicketNeeded(Throwable t) {
		if (t == null) {
			return false;
		}

		if (t instanceof WBEMException wbemException) {
			final int cimErrorType = wbemException.getID();
			return cimErrorType == WBEMException.CIM_ERR_ACCESS_DENIED;
		}

		// Now check recursively the cause
		return isRefreshTicketNeeded(t.getCause());
	}

	/**
	 * Perform a WBEM query.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
	 * @param requestStats Records the outcome of the request, or {@code null} to record nothing.
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	public List<List<String>> doWbemQuery(
		final String hostname,
		final WbemConfiguration wbemConfig,
		final String query,
		final String namespace,
		final RequestStats requestStats
	) throws ClientException {
		try {
			String urlSpec = String.format("%s://%s:%d", wbemConfig.getProtocol().toString(), hostname, wbemConfig.getPort());

			final URL url = new URI(urlSpec).toURL();

			LoggingHelper.trace(
				() -> log.trace(
					"Executing WBEM request:\n- Hostname: {}\n- Port: {}\n- Protocol: {}\n- URL: {}\n" + // NOSONAR
						"- Username: {}\n- Query: {}\n- Namespace: {}\n- Timeout: {} s\n",
					hostname,
					wbemConfig.getPort(),
					wbemConfig.getProtocol().toString(),
					url,
					wbemConfig.getUsername(),
					query,
					namespace,
					wbemConfig.getTimeout()
				)
			);

			final long startTime = System.currentTimeMillis();

			WbemQueryResult wbemQueryResult = executeWql(url, wbemConfig, query, namespace, requestStats);

			final long responseTime = System.currentTimeMillis() - startTime;

			List<List<String>> result = wbemQueryResult.getValues();

			LoggingHelper.trace(
				() -> log.trace(
					"Executed WBEM request:\n- Hostname: {}\n- Port: {}\n- Protocol: {}\n- URL: {}\n" + // NOSONAR
						"- Username: {}\n- Query: {}\n- Namespace: {}\n- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					wbemConfig.getPort(),
					wbemConfig.getProtocol().toString(),
					url,
					wbemConfig.getUsername(),
					query,
					namespace,
					wbemConfig.getTimeout(),
					TextTableHelper.generateTextTable(wbemQueryResult.getProperties(), result),
					responseTime
				)
			);

			return result;
		} catch (Exception e) {
			// NOSONAR an exception is already thrown
			throw new ClientException("WBEM query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Executes the WQL query with {@link WbemExecutor} and records the outcome of the request, except when
	 * {@link WbemExecutor} rejects it before sending anything.
	 *
	 * @param url          The URL of the CIM server
	 * @param wbemConfig   WBEM Protocol configuration, incl. credentials
	 * @param query        WQL query to execute
	 * @param namespace    WBEM namespace
	 * @param requestStats Records the outcome of the request, or {@code null} to record nothing.
	 * @return The result of the query
	 * @throws Exception when the query fails, as thrown by {@link WbemExecutor}
	 */
	private static WbemQueryResult executeWql(
		final URL url,
		final WbemConfiguration wbemConfig,
		final String query,
		final String namespace,
		final RequestStats requestStats
	) throws Exception {
		// A caller already interrupted (an expired deadline of its job) must not send a query it will not wait for:
		// WbemExecutor submits the query to its worker before it waits, and does not cancel it on interruption
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("Interrupted before the WBEM query was sent");
		}
		final WbemQueryResult wbemQueryResult;
		try {
			wbemQueryResult = WbemExecutor.executeWql(
				url,
				namespace,
				wbemConfig.getUsername(),
				wbemConfig.getPassword(),
				query,
				wbemConfig.getTimeout().intValue() * 1000,
				null
			);
		} catch (Exception e) {
			if (!isRejectedBeforeSending(e, wbemConfig)) {
				record(requestStats, RequestOutcome.of(e));
			}
			throw e;
		}
		record(requestStats, RequestOutcome.COMPLETED);
		return wbemQueryResult;
	}

	/**
	 * Whether {@link WbemExecutor} rejected the query before sending anything: it parses the WQL query
	 * ({@link WqlQuerySyntaxException}) and checks that the username and password are not {@code null}
	 * ({@link IllegalArgumentException}) before it connects.
	 *
	 * @param e          The exception thrown by {@link WbemExecutor}
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @return {@code true} when nothing was sent
	 */
	private static boolean isRejectedBeforeSending(final Exception e, final WbemConfiguration wbemConfig) {
		return (e instanceof WqlQuerySyntaxException ||
			(e instanceof IllegalArgumentException &&
				(wbemConfig.getUsername() == null || wbemConfig.getPassword() == null)));
	}

	/**
	 * Assess whether an exception (or any of its causes) is simply an error saying that the
	 * requested namespace of class doesn't exist, which is considered okay.
	 * <br>
	 *
	 * @param t Exception to verify
	 * @return whether specified exception is acceptable while performing namespace detection
	 */
	public boolean isAcceptableException(Throwable t) {
		if (t == null) {
			return false;
		}

		if (t instanceof WBEMException wbemException) {
			final int cimErrorType = wbemException.getID();
			return isAcceptableWbemError(cimErrorType);
		} else if (t instanceof WqlQuerySyntaxException) {
			return true;
		}

		// Now check recursively the cause
		return isAcceptableException(t.getCause());
	}

	/**
	 * Whether this error id is an acceptable WBEM error.
	 *
	 * @param errorId integer value representing the id of the WBEM exception
	 * @return boolean value
	 */
	private boolean isAcceptableWbemError(final int errorId) {
		return (errorId == WBEMException.CIM_ERR_INVALID_NAMESPACE ||
			errorId == WBEMException.CIM_ERR_INVALID_CLASS ||
			errorId == WBEMException.CIM_ERR_NOT_FOUND);
	}
}
