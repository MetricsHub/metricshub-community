package org.metricshub.extension.wmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WMI Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import static org.metricshub.extension.wmi.WmiExtension.IDENTIFIER;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.NetworkHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiRecorder;
import org.metricshub.wmi.WmiHelper;
import org.metricshub.wmi.WmiStringConverter;
import org.metricshub.wmi.exceptions.WmiComException;
import org.metricshub.wmi.remotecommand.WinRemoteCommandExecutor;
import org.metricshub.wmi.wbem.WmiWbemServices;

/**
 * The WmiRequestExecutor class provides utility methods for executing
 * various WMI requests locally or on remote hosts.
 */
@Slf4j
public class WmiRequestExecutor implements IWinRequestExecutor {

	/**
	 * WMI invalid class error message.
	 */
	static final String WBEM_E_INVALID_CLASS = "WBEM_E_INVALID_CLASS";
	/**
	 * WMI invalid namespace error message.
	 */
	static final String WBEM_E_INVALID_NAMESPACE = "WBEM_E_INVALID_NAMESPACE";
	/**
	 * WMI error message when the WMI repository is corrupted.
	 */
	static final String WBEM_E_NOT_FOUND = "WBEM_E_NOT_FOUND";

	/**
	 * The HRESULTs of a COM call that timed out: WBEM_E_TIMED_OUT, WBEM_E_PROVIDER_TIMED_OUT, RPC_E_TIMEOUT and
	 * HRESULT_FROM_WIN32(ERROR_TIMEOUT).
	 */
	private static final Set<Integer> TIMEOUT_HRESULTS = Set.of(0x80041069, 0x80041088, 0x8001011F, 0x800705B4);

	/**
	 * Maximum number of causes visited when classifying a failure, a guard against cause chains that loop.
	 */
	private static final int MAX_CAUSE_DEPTH = 32;

	/**
	 * Execute a WMI query
	 *
	 * @param hostname              The hostname of the device where the WMI service is running (<code>null</code> for localhost)
	 * @param wmiConfig             WMI Protocol configuration (credentials, timeout)
	 * @param wbemQuery             The WQL to execute
	 * @param namespace             The WBEM namespace where all the classes reside
	 * @param recordOutputDirectory The directory for recording the query result, or {@code null} to skip recording.
	 * @param requestStats          The request statistics of the monitored resource, where the outcome of the
	 *                              query is counted, or {@code null} to count nothing.
	 * @return A list of rows, where each row is represented as a list of strings.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WMI")
	@Override
	public List<List<String>> executeWmi(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("wmi.config") @NonNull final IWinConfiguration wmiConfig,
		@SpanAttribute("wmi.query") @NonNull final String wbemQuery,
		@SpanAttribute("wmi.namespace") @NonNull final String namespace,
		final String recordOutputDirectory,
		final RequestStats requestStats
	) throws ClientException {
		// A thread already interrupted (an expired deadline of its job) connects to nothing and counts nothing
		if (Thread.currentThread().isInterrupted()) {
			throw new ClientException("Interrupted before the WMI query was sent", new InterruptedException());
		}
		final String username = wmiConfig.getUsername();
		char[] password = wmiConfig.getPassword();
		// If the username is not provided, null will be used instead of the provided password.
		if (username == null) {
			password = null;
			log.debug("Hostname {}. Username not provided.", hostname);
		}

		// Where to connect to?
		// Local: namespace
		// Remote: hostname\namespace
		final String networkResource = NetworkHelper.isLocalhost(hostname)
			? namespace
			: String.format("\\\\%s\\%s", hostname, namespace);

		LoggingHelper.trace(
			() -> log.trace(
				"Executing WMI request:\n- Hostname: {}\n- Network-resource: {}\n- Username: {}\n- Query: {}\n" + // NOSONAR
					"- Namespace: {}\n- Timeout: {} s\n",
				hostname,
				networkResource,
				username,
				wbemQuery,
				namespace,
				wmiConfig.getTimeout()
			)
		);

		// Go! One attempt is the connection and the query: it is counted once, when the query has returned
		boolean answered = false;
		try (WmiWbemServices wbemServices = WmiWbemServices.getInstance(networkResource, username, password)) {
			final long startTime = System.currentTimeMillis();

			// Execute the WQL and get the result
			final List<Map<String, Object>> result = wbemServices.executeWql(wbemQuery, wmiConfig.getTimeout() * 1000);
			answered = true;
			IWinRequestExecutor.record(requestStats, IDENTIFIER, RequestOutcome.COMPLETED);

			final long responseTime = System.currentTimeMillis() - startTime;

			// Extract the exact property names (case sensitive), in the right order
			final List<String> properties = WmiHelper.extractPropertiesFromResult(result, wbemQuery);

			// Build the table
			List<List<String>> resultTable = buildWmiTable(result, properties);

			LoggingHelper.trace(
				() -> log.trace(
					"Executed WMI request:\n- Hostname: {}\n- Network-resource: {}\n- Username: {}\n- Query: {}\n" + // NOSONAR
						"- Namespace: {}\n- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					networkResource,
					username,
					wbemQuery,
					namespace,
					wmiConfig.getTimeout(),
					TextTableHelper.generateTextTable(properties, resultTable),
					responseTime
				)
			);

			if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
				WmiRecorder.getInstance(recordOutputDirectory).record(wbemQuery, namespace, resultTable);
			}

			return resultTable;
		} catch (Exception e) {
			if (!answered) {
				recordFailure(requestStats, e);
			}
			throw new ClientException("WMI query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Execute a command on a remote Windows system through Client and return an object with
	 * the output of the command.
	 *
	 * @param command    The command to execute. (Mandatory)
	 * @param hostname   The host to connect to.  (Mandatory)
	 * @param username   The username.
	 * @param password   The password.
	 * @param timeout    Timeout in seconds
	 * @param localFiles The local files list
	 * @param requestStats The request statistics of the monitored resource, where the outcome of the command is
	 *                     counted, or {@code null} to count nothing.
	 * @return The output of the executed command.
	 * @throws ClientException For any problem encountered.
	 */
	@WithSpan("Remote Command WMI")
	public String executeWmiRemoteCommand(
		@SpanAttribute("wmi.command") final String command,
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("wmi.username") final String username,
		final char[] password,
		@SpanAttribute("wmi.timeout") final int timeout,
		@SpanAttribute("wmi.local_files") final List<String> localFiles,
		final RequestStats requestStats
	) throws ClientException {
		// A thread already interrupted (an expired deadline of its job) runs nothing and counts nothing
		if (Thread.currentThread().isInterrupted()) {
			throw new ClientException("Interrupted before the WMI command was sent", new InterruptedException());
		}
		boolean answered = false;
		try {
			LoggingHelper.trace(
				() -> log.trace(
					"Executing WMI remote command:\n- Command: {}\n- Hostname: {}\n- Username: {}\n" + // NOSONAR
						"- Timeout: {} s\n- Local-files: {}\n",
					command,
					hostname,
					username,
					timeout,
					localFiles
				)
			);

			final long startTime = System.currentTimeMillis();

			final WinRemoteCommandExecutor result = WinRemoteCommandExecutor.execute(
				command,
				hostname,
				username,
				password,
				null,
				timeout * 1000L,
				localFiles,
				true
			);
			answered = true;
			IWinRequestExecutor.record(requestStats, IDENTIFIER, RequestOutcome.COMPLETED);

			String resultStdout = result.getStdout();

			final long responseTime = System.currentTimeMillis() - startTime;

			LoggingHelper.trace(
				() -> log.trace(
					"Executed WMI remote command:\n- Command: {}\n- Hostname: {}\n- Username: {}\n" + // NOSONAR
						"- Timeout: {} s\n- Local-files: {}\n- Result:\n{}\n- response-time: {}\n",
					command,
					hostname,
					username,
					timeout,
					localFiles,
					resultStdout,
					responseTime
				)
			);

			return resultStdout;
		} catch (Exception e) {
			if (!answered) {
				recordFailure(requestStats, e);
			}
			// The cause of the wmi-java wrapper, as before; the exception itself when it has none (a timeout)
			throw new ClientException(e.getCause() instanceof Exception cause ? cause : e);
		}
	}

	/**
	 * Count a request that failed, unless it failed on a local argument check before anything was sent.
	 *
	 * @param requestStats The request statistics of the monitored resource, or {@code null} to count nothing
	 * @param e            The exception thrown by the request
	 */
	static void recordFailure(final RequestStats requestStats, final Exception e) {
		// wmi-java checks its arguments (and rejects credentials on a local namespace) before any COM call
		if (!(e instanceof IllegalArgumentException)) {
			IWinRequestExecutor.record(requestStats, IDENTIFIER, outcomeOf(e));
		}
	}

	/**
	 * Classify the exception of a WMI request: a timeout when wmi-java reports one (a {@code TimeoutException}
	 * on WBEM_S_TIMEDOUT), when the request is interrupted, or when a COM call failed with a timeout HRESULT;
	 * a failure otherwise. The errors wmi-java reports with a message only, without the {@link COMException}
	 * that carries the HRESULT, are failures.
	 *
	 * @param e The exception thrown by the request
	 * @return {@link RequestOutcome#TIMEOUT} or {@link RequestOutcome#FAILED}
	 */
	static RequestOutcome outcomeOf(final Throwable e) {
		Throwable current = e;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (current instanceof COMException comException && isTimeout(comException.getHresult())) {
				return RequestOutcome.TIMEOUT;
			}
			current = current.getCause();
		}
		return RequestOutcome.of(e);
	}

	/**
	 * Whether the HRESULT of a failed COM call reports a timeout.
	 *
	 * @param hResult The HRESULT, possibly {@code null}
	 * @return {@code true} for one of the {@link #TIMEOUT_HRESULTS}
	 */
	private static boolean isTimeout(final HRESULT hResult) {
		return hResult != null && TIMEOUT_HRESULTS.contains(hResult.intValue());
	}

	/**
	 * Convert the given result to a {@link List} of {@link List} table
	 *
	 * @param result     The result we want to process
	 * @param properties The ordered properties
	 * @return {@link List} of {@link List} table
	 */
	List<List<String>> buildWmiTable(final List<Map<String, Object>> result, final List<String> properties) {
		final List<List<String>> table = new ArrayList<>();
		final WmiStringConverter stringConverter = new WmiStringConverter();

		// Transform the result to a list of list
		result.forEach(row -> {
			final List<String> line = new ArrayList<>();

			// loop over the right order
			properties.forEach(property -> line.add(stringConverter.convert(row.get(property))));

			// We have a line?
			if (!line.isEmpty()) {
				table.add(line);
			}
		});
		return table;
	}

	/**
	 * Assess whether an exception (or any of its causes) is simply an error saying that the
	 * requested namespace of class doesn't exist, which is considered okay.
	 * <br>
	 *
	 * @param t Throwable to verify
	 * @return whether specified exception is acceptable while performing namespace detection
	 */
	@Override
	public boolean isAcceptableException(Throwable t) {
		if (t == null) {
			return false;
		}

		if (t instanceof WmiComException) {
			final String message = t.getMessage();
			return IWinRequestExecutor.isAcceptableWmiComError(message);
		} else if (t instanceof org.metricshub.wmi.exceptions.WqlQuerySyntaxException) {
			return true;
		}

		// Now check recursively the cause
		return isAcceptableException(t.getCause());
	}

	@Override
	public String executeWinRemoteCommand(
		String hostname,
		IWinConfiguration winConfiguration,
		String command,
		List<String> embeddedFiles,
		RequestStats requestStats
	) throws ClientException {
		final String username = winConfiguration.getUsername();
		char[] password = winConfiguration.getPassword();
		// If the username is not provided, null will be used instead of the provided password.
		if (username == null) {
			password = null;
			log.debug("Hostname {}. Username not provided.", hostname);
		}

		return executeWmiRemoteCommand(
			command,
			hostname,
			username,
			password,
			winConfiguration.getTimeout().intValue(),
			embeddedFiles,
			requestStats
		);
	}
}
