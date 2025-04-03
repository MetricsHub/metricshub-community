package org.metricshub.extension.wmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WMI Extension
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.wmi.WmiHelper;
import org.metricshub.wmi.WmiStringConverter;
import org.metricshub.wmi.exceptions.WmiComException;
import org.metricshub.wmi.remotecommand.WinRemoteCommandExecutor;
import org.metricshub.wmi.wbem.WmiWbemServices;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.NetworkHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;

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
	 * Execute a WMI query
	 *
	 * @param hostname  The hostname of the device where the WMI service is running (<code>null</code> for localhost)
	 * @param wmiConfig WMI Protocol configuration (credentials, timeout)
	 * @param wbemQuery The WQL to execute
	 * @param namespace The WBEM namespace where all the classes reside
	 * @return A list of rows, where each row is represented as a list of strings.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WMI")
	@Override
	public List<List<String>> executeWmi(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("wmi.config") @NonNull final IWinConfiguration wmiConfig,
		@SpanAttribute("wmi.query") @NonNull final String wbemQuery,
		@SpanAttribute("wmi.namespace") @NonNull final String namespace
	) throws ClientException {
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

		LoggingHelper.trace(() ->
			log.trace(
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

		// Go!
		try (WmiWbemServices wbemServices = WmiWbemServices.getInstance(networkResource, username, password)) {
			final long startTime = System.currentTimeMillis();

			// Execute the WQL and get the result
			final List<Map<String, Object>> result = wbemServices.executeWql(wbemQuery, wmiConfig.getTimeout() * 1000);

			final long responseTime = System.currentTimeMillis() - startTime;

			// Extract the exact property names (case sensitive), in the right order
			final List<String> properties = WmiHelper.extractPropertiesFromResult(result, wbemQuery);

			// Build the table
			List<List<String>> resultTable = buildWmiTable(result, properties);

			LoggingHelper.trace(() ->
				log.trace(
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

			return resultTable;
		} catch (Exception e) {
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
		@SpanAttribute("wmi.local_files") final List<String> localFiles
	) throws ClientException {
		try {
			LoggingHelper.trace(() ->
				log.trace(
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

			String resultStdout = result.getStdout();

			final long responseTime = System.currentTimeMillis() - startTime;

			LoggingHelper.trace(() ->
				log.trace(
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
			throw new ClientException((Exception) e.getCause());
		}
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
		List<String> embeddedFiles
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
			embeddedFiles
		);
	}
}
