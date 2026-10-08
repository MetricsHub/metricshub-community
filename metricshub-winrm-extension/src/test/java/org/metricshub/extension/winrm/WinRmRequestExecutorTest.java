package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.extension.winrm.WinRmRequestExecutor.ClientKind.COMMAND;
import static org.metricshub.extension.winrm.WinRmRequestExecutor.ClientKind.WQL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinFileOperations;
import org.metricshub.winrm.AuthScheme;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.exceptions.WinRMFaultException;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;
import org.metricshub.winrm.exceptions.WqlSyntaxException;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;

class WinRmRequestExecutorTest {

	@Test
	void testIsAcceptableException() {
		WinRmRequestExecutor winRmRequestExecutor = new WinRmRequestExecutor();
		assertFalse(winRmRequestExecutor.isAcceptableException(null));
		assertFalse(winRmRequestExecutor.isAcceptableException(new Exception()));
		assertFalse(winRmRequestExecutor.isAcceptableException(new Exception(new Exception())));
		assertFalse(winRmRequestExecutor.isAcceptableException(new WindowsRemoteException("other")));
		assertFalse(winRmRequestExecutor.isAcceptableException(new WindowsRemoteException(new Exception())));
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new RuntimeException(new WindowsRemoteException("WBEM_E_INVALID_NAMESPACE"))
			)
		);
		assertTrue(
			winRmRequestExecutor.isAcceptableException(new RuntimeException(new WindowsRemoteException("WBEM_E_NOT_FOUND")))
		);
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new RuntimeException(new WindowsRemoteException("WBEM_E_INVALID_CLASS"))
			)
		);
		assertTrue(winRmRequestExecutor.isAcceptableException(new RuntimeException(new WqlQuerySyntaxException(""))));
	}

	@Test
	void testIsAcceptableExceptionWithFluentApiExceptions() {
		final WinRmRequestExecutor winRmRequestExecutor = new WinRmRequestExecutor();

		// The WBEM_E_* mnemonic is reported in the provider-level fault detail
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new WinRMFaultException("Fault", 500, "2150858778", "reason", "WBEM_E_INVALID_NAMESPACE")
			)
		);
		// ... or only in the message, when the remote service left the detail out
		assertTrue(
			winRmRequestExecutor.isAcceptableException(
				new RuntimeException(new WinRMFaultException("WBEM_E_INVALID_CLASS", 500, null, null, null))
			)
		);
		assertFalse(
			winRmRequestExecutor.isAcceptableException(new WinRMFaultException("Fault", 500, null, "reason", "other"))
		);

		assertTrue(winRmRequestExecutor.isAcceptableException(new WqlSyntaxException("bad query", new Exception())));
	}

	/**
	 * An executor whose client factory hands out mocks, recording them in the given list.
	 */
	private static WinRmRequestExecutor newPoolingExecutor(final List<WinRMClient> created, final Duration idleTimeout) {
		return new WinRmRequestExecutor(
			(hostname, configuration) -> {
				final WinRMClient client = mock(WinRMClient.class);
				created.add(client);
				return client;
			},
			idleTimeout
		);
	}

	private static WinRmConfiguration configuration(final String password) {
		return WinRmConfiguration.builder().username("user").password(password.toCharArray()).build();
	}

	@Test
	void testWithClientReusesIdleClients() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofHours(1));

		// Sequential requests share one client
		final WinRMClient first = executor.withClient("host", configuration("pass"), WQL, client -> client);
		assertSame(first, executor.withClient("host", configuration("pass"), WQL, client -> client));

		// A concurrent request (here, nested) gets its own client
		final WinRMClient concurrent = executor.withClient("host", configuration("pass"), WQL, outer ->
			executor.withClient("host", configuration("pass"), WQL, inner -> inner)
		);
		assertNotSame(first, concurrent);

		// The most recently used client is borrowed first, so the extra clients of a burst stay idle and expire
		assertSame(first, executor.withClient("host", configuration("pass"), WQL, client -> client));

		// Another host, changed credentials, or a command (whose client keeps a remote shell) never reuse a WQL client
		assertNotSame(first, executor.withClient("other", configuration("pass"), WQL, client -> client));
		assertNotSame(first, executor.withClient("host", configuration("changed"), WQL, client -> client));
		assertNotSame(first, executor.withClient("host", configuration("pass"), COMMAND, client -> client));

		assertEquals(5, created.size());
		created.forEach(client -> verify(client, never()).close());

		// Shutting down closes the idle clients
		executor.close();
		created.forEach(client -> verify(client).close());
	}

	@Test
	void testWithClientClosesIdleClientsAfterIdleTimeout() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofMillis(200));

		final WinRMClient client = executor.withClient("host", configuration("pass"), WQL, c -> c);

		verify(client, timeout(5000)).close();
		assertNotSame(client, executor.withClient("host", configuration("pass"), WQL, c -> c));
	}

	@Test
	void testWithClientClosesClientAfterFailure() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofHours(1));

		assertThrows(IllegalStateException.class, () ->
			executor.withClient("host", configuration("pass"), COMMAND, client -> {
				throw new IllegalStateException("timed out");
			})
		);

		final WinRMClient failed = created.get(0);
		verify(failed).close();
		assertNotSame(failed, executor.withClient("host", configuration("pass"), COMMAND, client -> client));
	}

	/**
	 * Building a client establishes no connection, so the whole configuration-to-client mapping can
	 * be exercised offline.
	 */
	@Test
	void testNewClient() {
		// HTTP with the configuration defaults: port 5985, NTLM, trust all certificates
		try (
			WinRMClient client = WinRmRequestExecutor.newClient(
				"host",
				WinRmConfiguration.builder().username("user").password("pass".toCharArray()).timeout(30L).build()
			)
		) {
			assertEquals("host", client.hostname());
		}

		// HTTPS with no port (the library deduces 5986), Kerberos first, and certificate validation on
		try (
			WinRMClient client = WinRmRequestExecutor.newClient(
				"host",
				WinRmConfiguration.builder()
					.username("DOMAIN\\user")
					.password("pass".toCharArray())
					.protocol(TransportProtocols.HTTPS)
					.port(null)
					.authentications(List.of(AuthenticationEnum.KERBEROS, AuthenticationEnum.NTLM))
					.trustAllCertificates(false)
					.timeout(30L)
					.build()
			)
		) {
			assertEquals("host", client.hostname());
		}

		// An empty authentication list leaves the library's own default (NTLM) in place
		try (
			WinRMClient client = WinRmRequestExecutor.newClient(
				"host",
				WinRmConfiguration.builder()
					.username("user")
					.password("pass".toCharArray())
					.authentications(List.of())
					.timeout(30L)
					.build()
			)
		) {
			assertEquals("host", client.hostname());
		}

		// Basic over HTTPS, as the library requires it
		try (
			WinRMClient client = WinRmRequestExecutor.newClient(
				"host",
				WinRmConfiguration.builder()
					.username("user")
					.password("pass".toCharArray())
					.protocol(TransportProtocols.HTTPS)
					.authentications(List.of(AuthenticationEnum.BASIC))
					.timeout(30L)
					.build()
			)
		) {
			assertEquals("host", client.hostname());
		}
	}

	@Test
	void testToAuthScheme() {
		assertEquals(AuthScheme.NTLM, WinRmRequestExecutor.toAuthScheme(AuthenticationEnum.NTLM));
		assertEquals(AuthScheme.KERBEROS, WinRmRequestExecutor.toAuthScheme(AuthenticationEnum.KERBEROS));
		// Basic used to be silently downgraded to NTLM
		assertEquals(AuthScheme.BASIC, WinRmRequestExecutor.toAuthScheme(AuthenticationEnum.BASIC));
	}

	@Test
	void testOpenFileOperations() throws Exception {
		final WinRmRequestExecutor winRmRequestExecutor = new WinRmRequestExecutor();
		final WinRmConfiguration configuration = WinRmConfiguration.builder()
			.username("user")
			.password("pass".toCharArray())
			.timeout(30L)
			.build();
		// Building the client connects nothing: the native file operations can be opened and closed offline
		try (WinFileOperations fileOperations = winRmRequestExecutor.openFileOperations("host", configuration).get()) {
			assertTrue(fileOperations instanceof WinRmFileOperations);
		}
		// Any other Windows configuration falls back to the script-based file operations
		assertTrue(winRmRequestExecutor.openFileOperations("host", mock(IWinConfiguration.class)).isEmpty());
	}
}
