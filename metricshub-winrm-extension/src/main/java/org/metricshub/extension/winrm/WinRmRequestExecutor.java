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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.common.helpers.FileHelper.PathPattern;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WinFileOperations;
import org.metricshub.extension.win.WmiRecorder;
import org.metricshub.winrm.AuthScheme;
import org.metricshub.winrm.CommandRequest;
import org.metricshub.winrm.CommandResult;
import org.metricshub.winrm.RemoteFileInfo;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.WinRMHttpProtocolEnum;
import org.metricshub.winrm.WqlRequest;
import org.metricshub.winrm.WqlResult;
import org.metricshub.winrm.WqlRow;
import org.metricshub.winrm.exceptions.WinRMClientException;
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
				authentications.stream().map(WinRmRequestExecutor::toAuthScheme).toArray(AuthScheme[]::new)
			);
		}

		if (winRmConfiguration.isTrustAllCertificates()) {
			builder.trustAllCertificates();
		}

		// A round trip is retried only when the connection could not be established and
		// authenticated (TCP connect, DNS, TLS handshake), so a command never runs twice, and a
		// transient network failure no longer costs a whole poll.
		builder.retries(1, Duration.ofSeconds(5));

		return builder.build();
	}

	/**
	 * Map a configured authentication scheme to the winrm-java scheme.
	 *
	 * @param authentication the configured scheme
	 * @return the winrm-java scheme
	 */
	static AuthScheme toAuthScheme(final AuthenticationEnum authentication) {
		return switch (authentication) {
			case KERBEROS -> AuthScheme.KERBEROS;
			case BASIC -> AuthScheme.BASIC;
			default -> AuthScheme.NTLM;
		};
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
			return executeRemoteWinRmCommand(hostname, winRmConfiguration, command, embeddedFiles);
		}

		throw new IllegalStateException("Windows commands can be executed only in WMI and WinRM protocols.");
	}

	/**
	 * Execute a WinRM remote command. The embedded files are copied to the host through the WinRM
	 * connection first, and their local paths in the command line are rewritten to the remote copies.
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param command            The command to execute
	 * @param embeddedFiles      The local files referenced by the command, to copy to the host; may be null or empty
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("Remote Command WinRM")
	public static String executeRemoteWinRmCommand(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.command") @NonNull final String command,
		@SpanAttribute("winrm.embedded_files") final List<String> embeddedFiles
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
				CommandRequest request = client.command(command);
				if (embeddedFiles != null && !embeddedFiles.isEmpty()) {
					request = request.upload(embeddedFiles.stream().map(Path::of).toArray(Path[]::new));
				}
				result = request.execute();
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

	/**
	 * Native remote file access through winrm-java: one authenticated connection serves every
	 * size, read and listing of a file source poll.
	 */
	@Override
	public Optional<WinFileOperations> openFileOperations(
		final String hostname,
		final IWinConfiguration winConfiguration
	) {
		if (winConfiguration instanceof WinRmConfiguration winRmConfiguration) {
			return Optional.of(new WinRmFileOperations(newClient(hostname, winRmConfiguration), hostname));
		}
		return Optional.empty();
	}

	/**
	 * Read a whole file on the remote host as UTF-8 text (capped at winrm-java's default of 64 MiB).
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param path               The absolute path of the file on the host
	 * @return The content of the file
	 * @throws ClientException when the file cannot be read (details in cause)
	 */
	@WithSpan("Read File WinRM")
	public String readRemoteFile(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.path") @NonNull final String path
	) throws ClientException {
		try (WinRMClient client = newClient(hostname, winRmConfiguration)) {
			return client.file(path).readText(StandardCharsets.UTF_8);
		} catch (WinRMClientException e) {
			log.error("Hostname {} - WinRM file read failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM file read of %s failed on %s.", path, hostname), e);
		}
	}

	/**
	 * List the files matching a path pattern on the remote host, with the syntax of a file
	 * source's {@code paths} ({@code *} and {@code ?} wildcards in any segment, a trailing
	 * backslash for all the files of a directory), as a text table.
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param pathPattern        The path pattern, e.g. {@code C:\logs\*.log}
	 * @return A text table with the path, size and last modification time of each matching file
	 * @throws ClientException when the pattern is invalid or the listing fails (details in cause)
	 */
	@WithSpan("List Files WinRM")
	public String listRemoteFiles(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.path_pattern") @NonNull final String pathPattern
	) throws ClientException {
		final PathPattern pattern = FileHelper.parsePathPattern(pathPattern, DeviceKind.WINDOWS);
		if (pattern == null) {
			throw new ClientException(String.format("Invalid Windows file path pattern: %s", pathPattern));
		}
		try (
			WinRmFileOperations fileOperations = new WinRmFileOperations(newClient(hostname, winRmConfiguration), hostname)
		) {
			final List<List<String>> rows = new ArrayList<>();
			for (final RemoteFileInfo file : fileOperations.list(pattern)) {
				rows.add(List.of(file.path(), String.valueOf(file.size()), file.lastModified().toString()));
			}
			return TextTableHelper.generateTextTable(new String[] { "Path", "Size", "LastModified" }, rows);
		}
	}
}
