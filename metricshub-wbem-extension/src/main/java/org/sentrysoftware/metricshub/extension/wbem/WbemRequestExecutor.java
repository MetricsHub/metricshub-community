package org.sentrysoftware.metricshub.extension.wbem;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Wbem Extension
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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.net.URI;
import java.net.URL;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.ThreadHelper;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.vcenter.VCenterClient;
import org.metricshub.wbem.client.WbemExecutor;
import org.metricshub.wbem.client.WbemQueryResult;
import org.metricshub.wbem.javax.wbem.WBEMException;

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
	 * Determine if a vCenter server is configured and call the appropriate method to run the WBEM query.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
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
		// handle vCenter case
		if (wbemConfig.getVCenter() != null) {
			return doVCenterQuery(hostname, wbemConfig, query, namespace, telemetryManager);
		} else {
			return doWbemQuery(hostname, wbemConfig, query, namespace);
		}
	}

	/**
	 * Perform a WBEM query using vCenter ticket authentication.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
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
		String ticket = telemetryManager.getHostProperties().getVCenterTicket();

		if (ticket == null) {
			ticket =
				refreshVCenterTicket(
					wbemConfig.getVCenter(),
					wbemConfig.getUsername(),
					wbemConfig.getPassword(),
					hostname,
					wbemConfig.getTimeout()
				);
		}

		final WbemConfiguration vCenterWbemConfig = WbemConfiguration
			.builder()
			.username(ticket)
			.password(ticket.toCharArray())
			.namespace(wbemConfig.getNamespace())
			.port(wbemConfig.getPort())
			.protocol(wbemConfig.getProtocol())
			.timeout(wbemConfig.getTimeout())
			.build();

		try {
			return doWbemQuery(hostname, vCenterWbemConfig, query, namespace);
		} catch (ClientException e) {
			if (isRefreshTicketNeeded(e.getCause())) {
				ticket =
					refreshVCenterTicket(
						wbemConfig.getVCenter(),
						wbemConfig.getUsername(),
						wbemConfig.getPassword(),
						hostname,
						wbemConfig.getTimeout()
					);
				vCenterWbemConfig.setUsername(ticket);
				vCenterWbemConfig.setPassword(ticket.toCharArray());
				return doWbemQuery(hostname, vCenterWbemConfig, query, namespace);
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
	 * @param hostname Hostname
	 * @param timeout  Timeout
	 * @return A ticket String
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private String refreshVCenterTicket(
		@NonNull String vCenter,
		@NonNull String username,
		@NonNull char[] password,
		@NonNull String hostname,
		@NonNull Long timeout
	) throws ClientException {
		VCenterClient.setDebug(() -> true, log::debug);
		try {
			String ticket = ThreadHelper.execute(
				() -> VCenterClient.requestCertificate(vCenter, username, new String(password), hostname),
				timeout
			);
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
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	public List<List<String>> doWbemQuery(
		final String hostname,
		final WbemConfiguration wbemConfig,
		final String query,
		final String namespace
	) throws ClientException {
		try {
			String urlSpec = String.format("%s://%s:%d", wbemConfig.getProtocol().toString(), hostname, wbemConfig.getPort());

			final URL url = new URI(urlSpec).toURL();

			LoggingHelper.trace(() ->
				log.trace(
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

			WbemQueryResult wbemQueryResult = WbemExecutor.executeWql(
				url,
				namespace,
				wbemConfig.getUsername(),
				wbemConfig.getPassword(),
				query,
				wbemConfig.getTimeout().intValue() * 1000,
				null
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			List<List<String>> result = wbemQueryResult.getValues();

			LoggingHelper.trace(() ->
				log.trace(
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
		} catch (Exception e) { // NOSONAR an exception is already thrown
			throw new ClientException("WBEM query failed on " + hostname + ".", e);
		}
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
		} else if (
			// CHECKSTYLE:OFF
			t instanceof org.metricshub.wbem.client.exceptions.WqlQuerySyntaxException
			// CHECKSTYLE:ON
		) {
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
		// CHECKSTYLE:OFF
		return (
			errorId == WBEMException.CIM_ERR_INVALID_NAMESPACE ||
			errorId == WBEMException.CIM_ERR_INVALID_CLASS ||
			errorId == WBEMException.CIM_ERR_NOT_FOUND
		);
		// CHECKSTYLE:ON
	}
}
