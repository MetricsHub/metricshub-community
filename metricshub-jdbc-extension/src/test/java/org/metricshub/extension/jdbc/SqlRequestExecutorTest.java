package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.SqlResult;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistry;
import org.metricshub.extension.jdbc.driver.JdbcDriverRegistryHolder;
import org.metricshub.extension.jdbc.driver.JdbcDriverSelection;
import org.metricshub.extension.jdbc.driver.LoadedDriver;
import org.mockito.MockedStatic;

/**
 * Tests the request outcomes recorded by {@link SqlRequestExecutor#executeSql} and the health check
 * {@link JdbcExtension#isDatabaseAlive}.
 */
class SqlRequestExecutorTest {

	private static final String RESOURCE_HOSTNAME = "resource-host";
	private static final String PROTOCOL_HOSTNAME = "db-host-override";
	private static final String URL = "jdbc:h2:mem:sqlRequestExecutorTest;DB_CLOSE_DELAY=-1";
	private static final String SELECT_ALL = "SELECT id, name FROM items ORDER BY id";
	private static final JdbcDriverSelection H2_SELECTION = new JdbcDriverSelection("org.h2.Driver", null);
	private static final long BLOCK_MILLIS = 3000;

	private static Connection keepAlive;

	private final SqlRequestExecutor executor = spy(new SqlRequestExecutor());

	@BeforeAll
	static void createDatabase() throws SQLException {
		keepAlive = DriverManager.getConnection(URL, "sa", "");
		try (Statement statement = keepAlive.createStatement()) {
			statement.execute("CREATE TABLE items (id INT PRIMARY KEY, name VARCHAR(32))");
			statement.execute("INSERT INTO items VALUES (1, 'one'), (2, 'two')");
		}
	}

	@AfterAll
	static void dropDatabase() throws SQLException {
		try (Statement statement = keepAlive.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		keepAlive.close();
	}

	/**
	 * Creates the telemetry manager of a resource whose hostname differs from the hostname of its JDBC configuration.
	 *
	 * @return A new telemetry manager.
	 */
	private static TelemetryManager newTelemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(RESOURCE_HOSTNAME).configurations(Map.of()).build())
			.build();
	}

	/**
	 * Creates a JDBC configuration whose hostname overrides the hostname of the resource.
	 *
	 * @param url      The JDBC URL.
	 * @param password The password.
	 * @return A new JDBC configuration.
	 */
	private static JdbcConfiguration newConfiguration(final String url, final String password) {
		return JdbcConfiguration.builder()
			.hostname(PROTOCOL_HOSTNAME)
			.url(url.toCharArray())
			.username("sa")
			.password(password.toCharArray())
			.timeout(30L)
			.build();
	}

	/**
	 * Builds the expected JDBC counters.
	 *
	 * @param completed The number of completed attempts.
	 * @param timeout   The number of timed out attempts.
	 * @param failed    The number of failed attempts.
	 * @return The counters as {@link RequestStats#snapshot()} returns them.
	 */
	private static Map<String, Map<RequestOutcome, Long>> counts(
		final long completed,
		final long timeout,
		final long failed
	) {
		return Map.of(
			SqlRequestExecutor.OPERATION_TYPE,
			Map.of(RequestOutcome.COMPLETED, completed, RequestOutcome.TIMEOUT, timeout, RequestOutcome.FAILED, failed)
		);
	}

	/**
	 * Executes a query for a new resource and returns its request counters, whatever the query returns or throws.
	 *
	 * @param url       The JDBC URL.
	 * @param password  The password.
	 * @param query     The SQL query.
	 * @param selection The driver selection.
	 * @return The request counters of the resource.
	 */
	private Map<String, Map<RequestOutcome, Long>> countsAfter(
		final String url,
		final String password,
		final String query,
		final JdbcDriverSelection selection
	) {
		final TelemetryManager telemetryManager = newTelemetryManager();
		try {
			executor.executeSql(
				PROTOCOL_HOSTNAME,
				newConfiguration(url, password),
				query,
				false,
				telemetryManager,
				selection
			);
		} catch (ClientException | RuntimeException e) {
			// The recorded outcome is what is tested
		}
		return telemetryManager.getRequestStats().snapshot();
	}

	/**
	 * Mocks the driver registry so that the resolved driver is the given one.
	 *
	 * @param driver The driver.
	 * @return The static mock, to close.
	 */
	private static MockedStatic<JdbcDriverRegistryHolder> mockDriver(final Driver driver) {
		final JdbcDriverRegistry registry = mock(JdbcDriverRegistry.class);
		when(registry.resolve(anyString(), any())).thenReturn(new LoadedDriver(driver, null, null));
		final MockedStatic<JdbcDriverRegistryHolder> holder = mockStatic(JdbcDriverRegistryHolder.class);
		holder.when(JdbcDriverRegistryHolder::get).thenReturn(registry);
		return holder;
	}

	/**
	 * Executes a query whose request throws the given exception.
	 *
	 * @param exception The exception thrown by the request.
	 * @return The request counters of the resource.
	 * @throws SQLException never (spy stubbing)
	 */
	private Map<String, Map<RequestOutcome, Long>> countsWhenRequestThrows(final SQLException exception)
		throws SQLException {
		doThrow(exception).when(executor).query(any(), any(), any(), any(), anyBoolean(), anyInt(), any());
		return countsAfter(URL, "", SELECT_ALL, H2_SELECTION);
	}

	/**
	 * Blocks for {@value #BLOCK_MILLIS} ms whatever the interrupts, like a driver stuck in a socket read.
	 *
	 * @param <T>    The type of the result.
	 * @param result The result.
	 * @return The result.
	 */
	private static <T> T blockIgnoringInterrupts(final T result) {
		final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BLOCK_MILLIS);
		while (System.nanoTime() < end) {
			Thread.interrupted();
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
		}
		return result;
	}

	/**
	 * Interrupts the current thread 200 ms from now.
	 */
	private static void interruptIn200Millis() {
		final Thread caller = Thread.currentThread();
		CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(caller::interrupt);
	}

	/**
	 * Runs the health check with a driver whose connection answers isValid with the given value.
	 *
	 * @param valid The answer of isValid.
	 * @param stats The request counters.
	 * @return The result of the health check.
	 * @throws SQLException never (mock stubbing)
	 */
	private static boolean isAliveWhenValidIs(final boolean valid, final RequestStats stats) throws SQLException {
		final Connection connection = mock(Connection.class);
		when(connection.isValid(30)).thenReturn(valid);
		final Driver driver = mock(Driver.class);
		when(driver.connect(anyString(), any())).thenReturn(connection);
		try (MockedStatic<JdbcDriverRegistryHolder> holder = mockDriver(driver)) {
			return JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats);
		}
	}

	@Test
	void testCompletedWithRowsRecordedInResourceTelemetryManager() throws ClientException {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final List<List<String>> result = executor.executeSql(
			PROTOCOL_HOSTNAME,
			newConfiguration(URL, ""),
			SELECT_ALL,
			false,
			telemetryManager,
			H2_SELECTION
		);
		assertEquals(List.of(List.of("1", "one"), List.of("2", "two")), result);
		assertEquals(counts(1, 0, 0), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testEmptyResultIsCompleted() {
		assertEquals(counts(1, 0, 0), countsAfter(URL, "", "SELECT * FROM items WHERE id = 0", H2_SELECTION));
	}

	@Test
	void testSyntaxErrorIsFailed() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final JdbcConfiguration configuration = newConfiguration(URL, "");
		final ClientException exception = assertThrows(
			ClientException.class,
			() -> executor.executeSql(
				PROTOCOL_HOSTNAME,
				configuration,
				"SELEC nonsense",
				false,
				telemetryManager,
				H2_SELECTION
			)
		);
		assertEquals("SQL query failed on hostname " + PROTOCOL_HOSTNAME, exception.getMessage());
		assertEquals(counts(0, 0, 1), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testAuthenticationFailureIsFailed() {
		assertEquals(counts(0, 0, 1), countsAfter(URL, "wrong-password", SELECT_ALL, H2_SELECTION));
	}

	@Test
	void testSqlTimeoutExceptionIsTimeout() throws SQLException {
		assertEquals(counts(0, 1, 0), countsWhenRequestThrows(new SQLTimeoutException("Query timed out")));
	}

	@Test
	void testSocketTimeoutCauseIsTimeout() throws SQLException {
		assertEquals(
			counts(0, 1, 0),
			countsWhenRequestThrows(new SQLException("Communications link failure", "08S01", new SocketTimeoutException()))
		);
	}

	@Test
	void testPostgresQueryCanceledIsTimeout() throws SQLException {
		assertEquals(
			counts(0, 1, 0),
			countsWhenRequestThrows(new SQLException("canceling statement due to user request", "57014"))
		);
	}

	@Test
	void testTimeoutInTheNextExceptionChainIsTimeout() throws SQLException {
		final SQLException timeoutInNext = new SQLException("Batch failed", "08006");
		timeoutInNext.setNextException(new SQLTimeoutException("Query timed out"));
		assertEquals(counts(0, 1, 0), countsWhenRequestThrows(timeoutInNext));

		final SQLException canceledInNext = new SQLException("Batch failed", "08006");
		canceledInNext.setNextException(new SQLException("canceling statement due to user request", "57014"));
		assertEquals(counts(0, 1, 0), countsWhenRequestThrows(canceledInNext));
	}

	@Test
	void testCauseCycleIsFailed() throws SQLException {
		final SQLException first = new SQLException("Connection refused", "08001");
		final SQLException second = new SQLException("Connection refused", "08001", first);
		first.initCause(second);
		assertEquals(counts(0, 0, 1), countsWhenRequestThrows(first));
	}

	@Test
	void testConnectionRefusedIsFailed() throws SQLException {
		assertEquals(counts(0, 0, 1), countsWhenRequestThrows(new SQLException("Connection refused", "08001")));
	}

	@Test
	void testInterruptedQueryIsOneTimeout() throws SQLException {
		doAnswer(invocation -> blockIgnoringInterrupts(new SqlResult())).when(executor)
			.query(any(), any(), any(), any(), anyBoolean(), anyInt(), any());
		final TelemetryManager telemetryManager = spy(newTelemetryManager());
		final JdbcConfiguration configuration = newConfiguration(URL, "");

		final long start = System.nanoTime();
		interruptIn200Millis();
		final ClientException exception = assertThrows(
			ClientException.class,
			() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, SELECT_ALL, false, telemetryManager, H2_SELECTION)
		);
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
		final boolean interrupted = Thread.interrupted();

		assertTrue(elapsedMillis < 1000, "Returned after " + elapsedMillis + " ms");
		assertTrue(interrupted, "The interrupt status is kept");
		assertEquals("SQL query interrupted on hostname " + PROTOCOL_HOSTNAME, exception.getMessage());
		assertEquals(counts(0, 1, 0), telemetryManager.getRequestStats().snapshot());
		// Nothing is recorded
		verify(telemetryManager, never()).getRecordOutputDirectory();
	}

	@Test
	void testInterruptedBeforeTheQuerySendsAndRecordsNothing() throws SQLException {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final JdbcConfiguration configuration = newConfiguration(URL, "");

		Thread.currentThread().interrupt();
		assertThrows(
			ClientException.class,
			() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, SELECT_ALL, false, telemetryManager, H2_SELECTION)
		);
		assertTrue(Thread.interrupted(), "The interrupt status is kept");

		verify(executor, never()).query(any(), any(), any(), any(), anyBoolean(), anyInt(), any());
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testDriverResolutionFailureRecordsNothing() {
		assertEquals(Map.of(), countsAfter(URL, "", SELECT_ALL, new JdbcDriverSelection("com.example.NoSuchDriver", null)));
	}

	@Test
	void testUrlNotAcceptedRecordsNothing() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final JdbcConfiguration configuration = newConfiguration("jdbc:unknown://db:1234/x", "");
		final ClientException exception = assertThrows(
			ClientException.class,
			() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, SELECT_ALL, false, telemetryManager, H2_SELECTION)
		);
		// The message callers log is unchanged
		assertTrue(exception.getCause().getMessage().startsWith("Error executing query: Driver org.h2.Driver did not"));
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testEmptyQueryRecordsNothing() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final JdbcConfiguration configuration = newConfiguration(URL, "");
		assertThrows(
			IllegalArgumentException.class,
			() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, "", false, telemetryManager, H2_SELECTION)
		);
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testQueryOnAnInterruptedThreadCountsNothing() {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final JdbcConfiguration configuration = newConfiguration(URL, "");
		try (MockedStatic<JdbcClient> client = mockStatic(JdbcClient.class)) {
			// An earlier deadline of the job interrupted the thread: nothing is sent
			Thread.currentThread().interrupt();
			try {
				final ClientException e = assertThrows(
					ClientException.class,
					() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, SELECT_ALL, false, telemetryManager, H2_SELECTION)
				);
				assertInstanceOf(InterruptedException.class, e.getCause());
				assertTrue(Thread.currentThread().isInterrupted());
			} finally {
				Thread.interrupted();
			}
			client.verify(() -> JdbcClient.execute(any(), any(), any(), any(), anyBoolean(), anyInt(), any()), never());
		}
		assertEquals(Map.of(), telemetryManager.getRequestStats().snapshot());
	}

	@Test
	void testNullTelemetryManagerRecordsNothing() throws ClientException {
		assertEquals(
			List.of(List.of("1", "one"), List.of("2", "two")),
			executor.executeSql(PROTOCOL_HOSTNAME, newConfiguration(URL, ""), SELECT_ALL, false, null, H2_SELECTION)
		);
		final JdbcConfiguration configuration = newConfiguration(URL, "");
		assertThrows(
			ClientException.class,
			() -> executor.executeSql(PROTOCOL_HOSTNAME, configuration, "SELEC nonsense", false, null, H2_SELECTION)
		);
	}

	@Test
	void testHealthCheckValidIsCompleted() throws SQLException {
		final RequestStats stats = new RequestStats();
		assertTrue(isAliveWhenValidIs(true, stats));
		assertEquals(counts(1, 0, 0), stats.snapshot());
	}

	@Test
	void testHealthCheckNotValidIsFailed() throws SQLException {
		final RequestStats stats = new RequestStats();
		assertEquals(false, isAliveWhenValidIs(false, stats));
		assertEquals(counts(0, 0, 1), stats.snapshot());
	}

	@Test
	void testHealthCheckExceptions() throws SQLException {
		final RequestStats stats = new RequestStats();
		final Driver driver = mock(Driver.class);
		when(driver.connect(anyString(), any())).thenThrow(
			new SQLException("canceling statement due to user request", "57014")
		)
			.thenThrow(new SQLTimeoutException("Login timed out"))
			.thenThrow(new SQLException("password authentication failed", "28P01"));
		try (MockedStatic<JdbcDriverRegistryHolder> holder = mockDriver(driver)) {
			for (int i = 0; i < 3; i++) {
				assertEquals(false, JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats));
			}
		}
		assertEquals(counts(0, 2, 1), stats.snapshot());
	}

	@Test
	void testHealthCheckOnAnInterruptedThreadCountsNothing() throws SQLException {
		final RequestStats stats = new RequestStats();
		final Driver driver = mock(Driver.class);
		try (MockedStatic<JdbcDriverRegistryHolder> holder = mockDriver(driver)) {
			// An earlier deadline of the job interrupted the thread: nothing is sent
			Thread.currentThread().interrupt();
			try {
				assertEquals(false, JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats));
				assertTrue(Thread.currentThread().isInterrupted());
			} finally {
				Thread.interrupted();
			}
		}
		verify(driver, never()).connect(anyString(), any());
		assertEquals(Map.of(), stats.snapshot());
	}

	@Test
	void testHealthCheckWithRealDatabase() {
		final RequestStats stats = new RequestStats();
		assertTrue(JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats));
		assertEquals(false, JdbcExtension.isDatabaseAlive(URL, "sa", "wrong".toCharArray(), 30, H2_SELECTION, stats));
		assertEquals(counts(1, 0, 1), stats.snapshot());
	}

	@Test
	void testInterruptedHealthCheckIsOneTimeout() throws SQLException {
		final Connection connection = mock(Connection.class);
		when(connection.isValid(30)).thenReturn(true);
		final Driver driver = mock(Driver.class);
		when(driver.connect(anyString(), any())).thenAnswer(invocation -> blockIgnoringInterrupts(connection));
		final RequestStats stats = new RequestStats();

		final boolean alive;
		final long elapsedMillis;
		final boolean interrupted;
		try (MockedStatic<JdbcDriverRegistryHolder> holder = mockDriver(driver)) {
			final long start = System.nanoTime();
			interruptIn200Millis();
			alive = JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats);
			elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			interrupted = Thread.interrupted();
		}

		assertEquals(false, alive);
		assertTrue(elapsedMillis < 1000, "Returned after " + elapsedMillis + " ms");
		assertTrue(interrupted, "The interrupt status is kept");
		assertEquals(counts(0, 1, 0), stats.snapshot());
	}

	@Test
	void testHealthCheckInterruptedBeforeSendsAndRecordsNothing() throws SQLException {
		final Driver driver = mock(Driver.class);
		final RequestStats stats = new RequestStats();

		final boolean alive;
		try (MockedStatic<JdbcDriverRegistryHolder> holder = mockDriver(driver)) {
			Thread.currentThread().interrupt();
			alive = JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, stats);
		}
		assertTrue(Thread.interrupted(), "The interrupt status is kept");

		assertEquals(false, alive);
		verify(driver, never()).connect(anyString(), any());
		assertEquals(Map.of(), stats.snapshot());
	}

	@Test
	void testHealthCheckLocalExitsRecordNothing() {
		final RequestStats stats = new RequestStats();
		// The driver does not accept the URL
		assertEquals(false, JdbcExtension.isDatabaseAlive("jdbc:unknown://db/x", "sa", null, 30, H2_SELECTION, stats));
		// The driver cannot be resolved
		assertEquals(
			false,
			JdbcExtension.isDatabaseAlive(
				URL,
				"sa",
				null,
				30,
				new JdbcDriverSelection("com.example.NoSuchDriver", null),
				stats
			)
		);
		// No driver accepts the URL
		assertEquals(false, JdbcExtension.isDatabaseAlive("jdbc:unknown://db/x", "sa", null, 30, null, stats));
		assertEquals(Map.of(), stats.snapshot());
		// No request counters
		assertTrue(JdbcExtension.isDatabaseAlive(URL, "sa", new char[0], 30, H2_SELECTION, null));
	}
}
