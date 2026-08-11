package org.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
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
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiRecorder;
import org.metricshub.winrm.AuthScheme;
import org.metricshub.winrm.CommandResult;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.WinRMHttpProtocolEnum;
import org.metricshub.winrm.WqlRequest;
import org.metricshub.winrm.WqlResult;
import org.metricshub.winrm.WqlRow;
import org.metricshub.winrm.exceptions.WinRMFaultException;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;
import org.metricshub.winrm.exceptions.WqlSyntaxException;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

/**
 * The WinRmRequestExecutor class provides utility methods for executing
 * various WinRm requests locally or on remote hosts.
 */
@Slf4j
public class WinRmRequestExecutor implements IWinRequestExecutor {

	/**
	 * Build the {@link WinRMClient} that carries out a request on the given host: transport, port,
	 * credentials, authentication schemes, timeout and TLS trust policy all come from the WinRM
	 * configuration. Nothing is connected yet: the first operation authenticates and, when several
	 * operations run on the same client, they share that single authenticated connection.
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout, ...)
	 * @return A new client, to be closed once the operation is over
	 */
	static WinRMClient newClient(final String hostname, final WinRmConfiguration winRmConfiguration) {
		final WinRMClient.Builder builder = WinRMClient.builder(hostname)
			.credentials(winRmConfiguration.getUsername(), winRmConfiguration.getPassword())
			.timeout(Duration.ofSeconds(winRmConfiguration.getTimeout()));

		if (TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())) {
			builder.http();
		} else {
			builder.https();
		}

		final Integer port = winRmConfiguration.getPort();
		if (port != null) {
			builder.port(port);
		}

		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		if (authentications != null && !authentications.isEmpty()) {
			builder.authentication(
				authentications
					.stream()
					.map(authentication ->
						AuthenticationEnum.KERBEROS.equals(authentication) ? AuthScheme.KERBEROS : AuthScheme.NTLM
					)
					.toArray(AuthScheme[]::new)
			);
		}

		if (winRmConfiguration.isTrustAllCertificates()) {
			builder.trustAllCertificates();
		}

		return builder.build();
	}

	/**
	 * Execute a WinRM query
	 *
	 * @param hostname              The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winConfiguration      WinRM Protocol configuration (credentials, timeout)
	 * @param query                 The query to execute
	 * @param namespace             The namespace on which to execute the query
	 * @param recordOutputDirectory The directory for recording the query result, or {@code null} to skip recording.
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@Override
	@WithSpan("WinRM")
	public List<List<String>> executeWmi(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final IWinConfiguration winConfiguration,
		@SpanAttribute("winrm.query") @NonNull final String query,
		@SpanAttribute("winrm.namespace") @NonNull final String namespace,
		final String recordOutputDirectory
	) throws ClientException {
		if (!(winConfiguration instanceof WinRmConfiguration winRmConfiguration)) {
			throw new ClientException("Invalid WinRmConfiguration on " + hostname);
		}
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		LoggingHelper.trace(() ->
			log.trace(
				"Executing WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n",
				hostname,
				username,
				query,
				httpProtocol,
				port,
				authentications,
				timeout,
				namespace
			)
		);

		// launching the request
		try {
			final long startTime = System.currentTimeMillis();

			final WqlResult result;
			try (WinRMClient client = newClient(hostname, winRmConfiguration)) {
				final WqlRequest request = client.wql(query);
				if (!namespace.isBlank()) {
					request.namespace(namespace);
				}
				result = request.execute();
			}

			final long responseTime = System.currentTimeMillis() - startTime;

			// The engine's compute steps mutate the result in place (add columns, transform rows,
			// ...): build mutable lists, with the columns in the order the query declares them.
			final List<String> columns = result.columns();
			final List<List<String>> table = new ArrayList<>(result.size());
			for (final WqlRow row : result) {
				final List<String> values = new ArrayList<>(columns.size());
				for (final String column : columns) {
					values.add(row.string(column));
				}
				table.add(values);
			}

			LoggingHelper.trace(() ->
				log.trace(
					"Executed WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
						"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					query,
					httpProtocol,
					port,
					authentications,
					timeout,
					namespace,
					TextTableHelper.generateTextTable(table),
					responseTime
				)
			);

			if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
				WmiRecorder.getInstance(recordOutputDirectory).record(query, namespace, table);
			}

			return table;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM WQL request failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM WQL request failed on %s.", hostname), e);
		}
	}

	@Override
	public boolean isAcceptableException(Throwable t) {
		if (t == null) {
			return false;
		}

		if (t instanceof WinRMFaultException winRmFaultException) {
			// The provider-level detail is where WMI reports its WBEM_E_* mnemonics; the fault message
			// repeats it, and is the only place it shows up on a fault carrying no detail element.
			final String faultDetail = winRmFaultException.getFaultDetail();
			return IWinRequestExecutor.isAcceptableWmiComError(faultDetail == null ? t.getMessage() : faultDetail);
		} else if (t instanceof WindowsRemoteException) {
			final String message = t.getMessage();
			return IWinRequestExecutor.isAcceptableWmiComError(message);
		} else if (t instanceof WqlQuerySyntaxException || t instanceof WqlSyntaxException) {
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
		if (winConfiguration instanceof WinRmConfiguration winRmConfiguration) {
			return executeRemoteWinRmCommand(hostname, winRmConfiguration, command);
		}

		throw new IllegalStateException("Windows commands can be executed only in WMI and WinRM protocols.");
	}

	/**
	 * Execute a WinRM remote command
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param command            The command to execute
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("Remote Command WinRM")
	public static String executeRemoteWinRmCommand(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.command") @NonNull final String command
	) throws ClientException {
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		LoggingHelper.trace(() ->
			log.trace(
				"Executing WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n",
				hostname,
				username,
				command,
				httpProtocol,
				port,
				authentications,
				timeout
			)
		);

		// launching the command
		try {
			final long startTime = System.currentTimeMillis();

			final CommandResult result;
			try (WinRMClient client = newClient(hostname, winRmConfiguration)) {
				result = client.command(command).execute();
			}

			final long responseTime = System.currentTimeMillis() - startTime;

			// If the command returns an error
			if (result.exitCode() != 0) {
				throw new ClientException(String.format("WinRM remote command failed on %s: %s", hostname, result.stderr()));
			}

			final String resultStdout = result.stdout();

			LoggingHelper.trace(() ->
				log.trace(
					"Executed WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
						"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					command,
					httpProtocol,
					port,
					authentications,
					timeout,
					resultStdout,
					responseTime
				)
			);

			return resultStdout;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM remote command failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM remote command failed on %s.", hostname), e);
		}
	}
}
