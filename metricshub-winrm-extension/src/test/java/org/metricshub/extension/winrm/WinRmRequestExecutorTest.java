package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.engine.tool.ToolTable;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinFileOperations;
import org.metricshub.winrm.AuthScheme;
import org.metricshub.winrm.CommandResult;
import org.metricshub.winrm.RemoteFileInfo;
import org.metricshub.winrm.WinRMClient;
import org.metricshub.winrm.exceptions.WinRMClientException;
import org.metricshub.winrm.exceptions.WinRMFaultException;
import org.metricshub.winrm.exceptions.WindowsRemoteException;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;
import org.metricshub.winrm.exceptions.WqlSyntaxException;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

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
		return new WinRmRequestExecutor((hostname, configuration) -> {
			final WinRMClient client = mock(WinRMClient.class);
			created.add(client);
			return client;
		}, idleTimeout);
	}

	private static WinRmConfiguration configuration(final String password) {
		return WinRmConfiguration.builder().username("user").password(password.toCharArray()).build();
	}

	@Test
	void testWithClientReusesIdleClients() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofHours(1));

		// Sequential requests share one client
		final WinRMClient first = executor.withClient("host", configuration("pass"), client -> client);
		assertSame(first, executor.withClient("host", configuration("pass"), client -> client));

		// A concurrent request (here, nested) gets its own client
		final WinRMClient concurrent = executor.withClient(
			"host",
			configuration("pass"),
			outer -> executor.withClient("host", configuration("pass"), inner -> inner)
		);
		assertNotSame(first, concurrent);

		// The most recently used client is borrowed first, so the extra clients of a burst stay idle and expire
		assertSame(first, executor.withClient("host", configuration("pass"), client -> client));

		// Another host, or changed credentials, never reuse a client
		assertNotSame(first, executor.withClient("other", configuration("pass"), client -> client));
		assertNotSame(first, executor.withClient("host", configuration("changed"), client -> client));

		assertEquals(4, created.size());
		created.forEach(client -> verify(client, never()).close());

		// Shutting down closes the idle clients
		executor.close();
		created.forEach(client -> verify(client).close());
	}

	@Test
	void testWithClientClosesIdleClientsAfterIdleTimeout() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofMillis(200));

		final WinRMClient client = executor.withClient("host", configuration("pass"), c -> c);

		verify(client, timeout(5000)).close();
		assertNotSame(client, executor.withClient("host", configuration("pass"), c -> c));
	}

	@Test
	void testWithClientClosesClientAfterFailure() {
		final List<WinRMClient> created = new ArrayList<>();
		final WinRmRequestExecutor executor = newPoolingExecutor(created, Duration.ofHours(1));

		assertThrows(IllegalStateException.class, () -> executor.withClient("host", configuration("pass"), client -> {
			throw new IllegalStateException("timed out");
		}));

		final WinRMClient failed = created.get(0);
		verify(failed).close();
		assertNotSame(failed, executor.withClient("host", configuration("pass"), client -> client));
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
	void testListRemoteFilesIsATableOfPathSizeAndLastModified() throws Exception {
		final RemoteFileInfo file = mock(RemoteFileInfo.class);
		when(file.path()).thenReturn("C:\\logs\\app.log");
		when(file.size()).thenReturn(12L);
		when(file.lastModified()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
		final WinRMClient client = mock(WinRMClient.class, RETURNS_DEEP_STUBS);
		when(client.file("C:\\logs").list().maxDepth(1).filesOnly().glob("*.log").execute().entries()).thenReturn(
			List.of(file)
		);

		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq("host"), any())).thenReturn(client);

			assertEquals(
				new ToolTable(
					List.of("Path", "Size", "LastModified"),
					List.of(List.of("C:\\logs\\app.log", "12", "2026-01-02T03:04:05Z"))
				),
				new WinRmRequestExecutor().listRemoteFiles("host", WinRmConfiguration.builder().build(), "C:\\logs\\*.log")
			);
		}
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
		try (
			WinFileOperations fileOperations = winRmRequestExecutor.openFileOperations("host", configuration, null).get()
		) {
			assertTrue(fileOperations instanceof WinRmFileOperations);
		}
		// Any other Windows configuration falls back to the script-based file operations
		assertTrue(winRmRequestExecutor.openFileOperations("host", mock(IWinConfiguration.class), null).isEmpty());
	}

	/**
	 * A client whose close, like the deletion of its shell, ignores interrupts: it blocks until released, for 3 seconds
	 * at most, then counts down {@code closed}.
	 */
	private static WinRMClient clientWithBlockingClose(final CountDownLatch release, final CountDownLatch closed) {
		final WinRMClient client = mock(WinRMClient.class, RETURNS_DEEP_STUBS);
		doAnswer(invocation -> {
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (release.getCount() > 0 && System.nanoTime() < deadline) {
				try {
					release.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					// Ignored, like the shell deletion
				}
			}
			closed.countDown();
			return null;
		}).when(client).close();
		return client;
	}

	/**
	 * Run the given request on the current thread, which is interrupted during the request, and check that it returns
	 * at once without waiting for the close of its client, which still completes.
	 */
	private static <T> T assertReturnsAtOnce(
		final Callable<T> request,
		final CountDownLatch release,
		final CountDownLatch closed
	) throws Exception {
		final long start = System.nanoTime();
		try {
			final T result = request.call();
			final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			assertTrue(elapsedMillis < 1000, "Returned after " + elapsedMillis + " ms");
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(1, closed.getCount());
			return result;
		} finally {
			Thread.interrupted();
			release.countDown();
			assertTrue(closed.await(5, TimeUnit.SECONDS), "The client is closed");
		}
	}

	/**
	 * Answers like a request during which the deadline of the job expires: interrupts the current thread, then
	 * returns the given result.
	 */
	private static Answer<Object> interruptingAndReturning(final Object result) {
		return invocation -> {
			Thread.currentThread().interrupt();
			return result;
		};
	}

	@Test
	void testInterruptedCommandDoesNotWaitForTheShellDeletion() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final WinRMClient client = clientWithBlockingClose(release, closed);
		final CommandResult result = mock(CommandResult.class);
		when(result.stdout()).thenReturn("output");
		when(client.command("cmd").execute()).thenAnswer(interruptingAndReturning(result));

		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq("host"), any())).thenReturn(client);
			assertEquals(
				"output",
				assertReturnsAtOnce(
					() -> WinRmRequestExecutor.executeRemoteWinRmCommand("host", configuration("pass"), "cmd", null, null),
					release,
					closed
				)
			);
		}
	}

	@Test
	void testInterruptedFileReadDoesNotWaitForTheShellDeletion() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final WinRMClient client = clientWithBlockingClose(release, closed);
		when(client.file("C:\\hosts").readText(StandardCharsets.UTF_8)).thenAnswer(interruptingAndReturning("content"));

		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq("host"), any())).thenReturn(client);
			assertEquals(
				"content",
				assertReturnsAtOnce(
					() -> new WinRmRequestExecutor().readRemoteFile("host", configuration("pass"), "C:\\hosts"),
					release,
					closed
				)
			);
		}
	}

	@Test
	void testFileReadWrapsAClientCreationFailure() {
		final WinRMClientException failure = new WinRMClientException("unsupported authentication");
		try (MockedStatic<WinRmRequestExecutor> statics = mockStatic(WinRmRequestExecutor.class, CALLS_REAL_METHODS)) {
			statics.when(() -> WinRmRequestExecutor.newClient(eq("host"), any())).thenThrow(failure);
			final ClientException exception = assertThrows(
				ClientException.class,
				() -> new WinRmRequestExecutor().readRemoteFile("host", configuration("pass"), "C:\\hosts")
			);
			assertSame(failure, exception.getCause());
		}
	}

	@Test
	void testFileOperationsOfAnInterruptedCallerDoNotWaitForTheShellDeletion() throws Exception {
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final WinRmFileOperations fileOperations = new WinRmFileOperations(
			clientWithBlockingClose(release, closed),
			"host",
			null
		);
		assertReturnsAtOnce(() -> {
			Thread.currentThread().interrupt();
			fileOperations.close();
			return null;
		}, release, closed);
	}

	@Test
	void testFileOperationsCloseWaitsForTheShellDeletion() {
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final WinRmFileOperations fileOperations = new WinRmFileOperations(
			clientWithBlockingClose(release, closed),
			"host",
			null
		);
		Thread.startVirtualThread(() -> {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			release.countDown();
		});
		fileOperations.close();
		// A caller that is not interrupted returns once its client is closed, as before
		assertEquals(0, closed.getCount());
	}
}
