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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
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
 * <p>
 * Requests reuse pooled {@link WinRMClient} instances, so the sources of a collect cycle share a few
 * authenticated connections and remote shells instead of opening one per request.
 */
@Slf4j
public class WinRmRequestExecutor implements IWinRequestExecutor {

	/**
	 * How long a client stays idle in the pool before it is closed. The requests of a collect cycle
	 * run back to back and share their clients, while the gap between two cycles (2 minutes by
	 * default) is longer, so each cycle starts with fresh clients: no remote shell stays open on the
	 * host between cycles, and no client outlives the 120 seconds after which HTTP.sys drops an idle
	 * connection anyway.
	 */
	static final Duration IDLE_TIMEOUT = Duration.ofSeconds(15);

	/**
	 * Idle clients per host, configuration and kind, most recently used first. A client is a serial
	 * channel, so each concurrent request borrows its own: the pool grows to the host's concurrency,
	 * and the clients of a burst that are no longer needed sink to the end and expire. A changed
	 * configuration (new credentials, ...) is another key, so it never reuses an old client.
	 */
	private final Map<ClientKey, Deque<IdleClient>> idleClients = new HashMap<>();

	private final BiFunction<String, WinRmConfiguration, WinRMClient> clientFactory;

	private final Duration idleTimeout;

	/**
	 * What a pooled client runs. The kinds are pooled apart because a client keeps the remote shell of
	 * its first command until it is closed: WQL clients then never own a shell, and the shells open on
	 * a host never outnumber its concurrent commands.
	 */
	enum ClientKind {
		/** WQL queries, which need no remote shell. */
		WQL,
		/** Commands, which run in the remote shell the client keeps. */
		COMMAND
	}

	private record ClientKey(String hostname, WinRmConfiguration configuration, ClientKind kind) {}

	private record IdleClient(WinRMClient client, long idleSince) {}

	/**
	 * Creates an executor whose clients are closed after {@link #IDLE_TIMEOUT} of inactivity.
	 */
	public WinRmRequestExecutor() {
		this(WinRmRequestExecutor::newClient, IDLE_TIMEOUT);
	}

	/**
	 * Creates an executor with the given client factory and idle timeout.
	 *
	 * @param clientFactory Builds the client of a host from its WinRM configuration
	 * @param idleTimeout   How long a client stays idle in the pool before it is closed
	 */
	WinRmRequestExecutor(
		final BiFunction<String, WinRmConfiguration, WinRMClient> clientFactory,
		final Duration idleTimeout
	) {
		this.clientFactory = clientFactory;
		this.idleTimeout = idleTimeout;
	}

	/**
	 * Run an operation on a client of the host: an idle one of that kind from the pool when there is
	 * one, a new one otherwise. After a success, the client goes back to the pool. After a failure, it
	 * is closed instead: closing hard-closes a connection that a timed-out operation may still be
	 * blocked on and, when no operation holds the connection any more, deletes the client's remote
	 * shell. A shell whose command is still blocked is left to the host, which reaps it after its shell
	 * IdleTimeout, as it did before clients were pooled.
	 *
	 * @param <T>                The type of the operation's result
	 * @param hostname           The hostname of the device where the WinRM service is running
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout, ...)
	 * @param kind               What the operation runs, see {@link ClientKind}
	 * @param operation          The operation to run on the client
	 * @return The result of the operation
	 */
	<T> T withClient(
		final String hostname,
		final WinRmConfiguration winRmConfiguration,
		final ClientKind kind,
		final Function<WinRMClient, T> operation
	) {
		final ClientKey key = new ClientKey(hostname, winRmConfiguration, kind);
		WinRMClient client = borrow(key);
		if (client == null) {
			client = clientFactory.apply(hostname, winRmConfiguration);
		}

		boolean succeeded = false;
		try {
			final T result = operation.apply(client);
			succeeded = true;
			return result;
		} finally {
			if (succeeded) {
				release(key, client);
			} else {
				client.close();
			}
		}
	}

	/**
	 * Take the most recently used idle client of the given key out of the pool.
	 *
	 * @param key The host, configuration and kind
	 * @return The client, or {@code null} when none is idle
	 */
	private WinRMClient borrow(final ClientKey key) {
		synchronized (idleClients) {
			final Deque<IdleClient> idle = idleClients.get(key);
			final IdleClient idleClient = idle == null ? null : idle.poll();
			return idleClient == null ? null : idleClient.client();
		}
	}

	/**
	 * Put a client back into the pool, and check the pool again once the idle timeout has elapsed.
	 *
	 * @param key    The host, configuration and kind
	 * @param client The client, idle from now on
	 */
	private void release(final ClientKey key, final WinRMClient client) {
		final Deque<IdleClient> idle;
		synchronized (idleClients) {
			idle = idleClients.computeIfAbsent(key, k -> new ArrayDeque<>());
			idle.push(new IdleClient(client, System.nanoTime()));
		}
		// A short-lived thread rather than a JVM-wide scheduler: a long-lived thread started here would
		// keep this extension's class loader (its context class loader) after a reload closes it
		Thread.startVirtualThread(() -> {
			try {
				Thread.sleep(idleTimeout);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			closeExpired(key, idle);
		});
	}

	/**
	 * Close the clients of the given pool entry that have been idle for the idle timeout. Every
	 * release schedules this check, so each client is checked when its own idle timeout elapses.
	 *
	 * @param key  The host, configuration and kind
	 * @param idle The idle clients of that key, removed from the pool once empty
	 */
	private void closeExpired(final ClientKey key, final Deque<IdleClient> idle) {
		final long idleTimeoutNanos = idleTimeout.toNanos();
		final List<WinRMClient> expired = new ArrayList<>();
		synchronized (idleClients) {
			// Most recently used first: the longest idle clients are at the end
			while (!idle.isEmpty() && System.nanoTime() - idle.peekLast().idleSince() >= idleTimeoutNanos) {
				expired.add(idle.pollLast().client());
			}
			if (idle.isEmpty()) {
				idleClients.remove(key, idle);
			}
		}
		expired.forEach(WinRMClient::close);
	}

	/**
	 * Close every idle client, deleting their remote shells. Called when the extension shuts down.
	 */
	public void close() {
		final List<IdleClient> idle;
		synchronized (idleClients) {
			idle = idleClients.values().stream().flatMap(Deque::stream).toList();
			// Pending expiry checks hold their deques: empty them too
			idleClients.values().forEach(Deque::clear);
			idleClients.clear();
		}
		idle.forEach(idleClient -> idleClient.client().close());
	}

	/**
	 * Build the {@link WinRMClient} that carries out requests on the given host: transport, port,
	 * credentials, authentication schemes, timeout and TLS trust policy all come from the WinRM
	 * configuration. Nothing is connected yet: the first operation authenticates and, when several
	 * operations run on the same client, they share that single authenticated connection.
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout, ...)
	 * @return A new client, to be closed once it is no longer used
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

			final WqlResult result = withClient(hostname, winRmConfiguration, ClientKind.WQL, client -> {
				final WqlRequest request = client.wql(query);
				if (!namespace.isBlank()) {
					request.namespace(namespace);
				}
				return request.execute();
			});

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
	public String executeRemoteWinRmCommand(
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

			final CommandResult result = withClient(hostname, winRmConfiguration, ClientKind.COMMAND, client ->
				client.command(command).execute()
			);

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
