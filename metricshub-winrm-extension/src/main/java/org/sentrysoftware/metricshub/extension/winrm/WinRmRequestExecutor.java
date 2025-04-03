package org.sentrysoftware.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
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
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.winrm.WinRMHttpProtocolEnum;
import org.metricshub.winrm.WindowsRemoteCommandResult;
import org.metricshub.winrm.command.WinRMCommandExecutor;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;
import org.metricshub.winrm.wql.WinRMWqlExecutor;

/**
 * The WinRmRequestExecutor class provides utility methods for executing
 * various WinRm requests locally or on remote hosts.
 */
@Slf4j
public class WinRmRequestExecutor implements IWinRequestExecutor {

	/**
	 * Execute a WinRM query
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winConfiguration   WinRM Protocol configuration (credentials, timeout)
	 * @param query              The query to execute
	 * @param namespace          The namespace on which to execute the query
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@Override
	@WithSpan("WinRM")
	public List<List<String>> executeWmi(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final IWinConfiguration winConfiguration,
		@SpanAttribute("winrm.query") @NonNull final String query,
		@SpanAttribute("winrm.namespace") @NonNull final String namespace
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

			WinRMWqlExecutor result = WinRMWqlExecutor.executeWql(
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				namespace,
				query,
				timeout * 1000L,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			final List<List<String>> table = result.getRows();

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

		if (t instanceof WindowsRemoteException) {
			final String message = t.getMessage();
			return IWinRequestExecutor.isAcceptableWmiComError(message);
		} else if (t instanceof WqlQuerySyntaxException) {
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

			WindowsRemoteCommandResult result = WinRMCommandExecutor.execute(
				command,
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				null,
				timeout * 1000L,
				null,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			// If the command returns an error
			if (result.getStatusCode() != 0) {
				throw new ClientException(String.format("WinRM remote command failed on %s: %s", hostname, result.getStderr()));
			}

			final String resultStdout = result.getStdout();

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
