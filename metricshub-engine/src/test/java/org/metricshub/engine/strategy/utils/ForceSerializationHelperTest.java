package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.metricshub.engine.constants.Constants.EXPECTED_SNMP_TABLE_DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForceSerializationHelperTest {

	@Mock
	private IProtocolExtension protocolExtensionMock;

	private static final String HOST_NAME = "host.test.force.serialization";
	private static final String SELECT_COLUMNS = "ID,1,3";
	private static final SourceTable EXPECTED_SOURCE_TABLE = SourceTable.ofTable(EXPECTED_SNMP_TABLE_DATA);
	private static final String DESCRIPTION = "source";
	private static final String CONNECTOR_ID = "connector";

	@Test
	void testForceSerializationNullArguments() {
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		assertThrows(
			IllegalArgumentException.class,
			() -> ForceSerializationHelper.forceSerialization(
				null,
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			)
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> ForceSerializationHelper.forceSerialization(
				() -> emptySourceTable,
				null,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			)
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> ForceSerializationHelper.forceSerialization(
				() -> emptySourceTable,
				telemetryManager,
				null,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			)
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> ForceSerializationHelper.forceSerialization(
				() -> emptySourceTable,
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				null,
				emptySourceTable
			)
		);

		assertThrows(
			IllegalArgumentException.class,
			() -> ForceSerializationHelper.forceSerialization(
				() -> emptySourceTable,
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				null
			)
		);
	}

	@Test
	void testForceSerializationInterruptedException() throws InterruptedException {
		final ReentrantLock spyLock = spy(ReentrantLock.class);
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname(HOST_NAME).build());

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setForceSerializationLock(spyLock);

		doThrow(InterruptedException.class).when(spyLock).tryLock(anyLong(), any(TimeUnit.class));

		assertEquals(
			SourceTable.empty(),
			ForceSerializationHelper.forceSerialization(
				() -> SourceTable.ofTable(List.of(List.of("a", "b", "c"))),
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			)
		);

		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
	}

	@Test
	void testForceSerializationSkippedWhenInterrupted() {
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname(HOST_NAME).build());

		// The job deadline expired: the request is not sent and the interrupt is kept
		Thread.currentThread().interrupt();
		try {
			final SourceTable returned = ForceSerializationHelper.forceSerialization(
				() -> fail("The executable must not run on an interrupted thread"),
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			);

			assertSame(emptySourceTable, returned);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void testForceSerializationCouldNotAcquireLock() throws InterruptedException {
		final ReentrantLock spyLock = spy(ReentrantLock.class);
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname(HOST_NAME).build());

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setForceSerializationLock(spyLock);

		doReturn(false).when(spyLock).tryLock(anyLong(), any(TimeUnit.class));

		assertEquals(
			SourceTable.empty(),
			ForceSerializationHelper.forceSerialization(
				() -> SourceTable.ofTable(List.of(List.of("a", "b", "c"))),
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				emptySourceTable
			)
		);
	}

	@Test
	void testForceSerializationLockAcquired() throws Exception {
		final Source snmpTableSource = SnmpTableSource.builder()
			.oid("1.2.3.4")
			.selectColumns(SELECT_COLUMNS)
			.forceSerialization(true)
			.build();

		final ClientsExecutor clientsExecutor = spy(ClientsExecutor.class);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOST_NAME)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);

		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		doReturn(EXPECTED_SOURCE_TABLE).when(protocolExtensionMock)
			.processSource(eq(snmpTableSource), anyString(), any(TelemetryManager.class));

		final ISourceProcessor processor = SourceProcessor.builder()
			.connectorId(CONNECTOR_ID)
			.clientsExecutor(clientsExecutor)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build();

		assertEquals(
			EXPECTED_SOURCE_TABLE,
			ForceSerializationHelper.forceSerialization(
				() -> snmpTableSource.accept(processor),
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				SourceTable.empty()
			)
		);
	}

	@Test
	void testForceSerializationMultiThreads() throws Exception {
		final Source snmpTableSource = SnmpTableSource.builder()
			.oid("1.2.3.4")
			.selectColumns(SELECT_COLUMNS)
			.forceSerialization(true)
			.build();

		final ClientsExecutor clientsExecutor = spy(ClientsExecutor.class);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOST_NAME)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		// Mock source table information for enclosure
		doReturn(true).when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		doReturn(EXPECTED_SOURCE_TABLE).when(protocolExtensionMock)
			.processSource(eq(snmpTableSource), anyString(), any(TelemetryManager.class));

		final ISourceProcessor processor = SourceProcessor.builder()
			.connectorId(CONNECTOR_ID)
			.clientsExecutor(clientsExecutor)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build();

		final ExecutorService threadsPool = Executors.newFixedThreadPool(2);

		final Callable<SourceTable> callable1 = () -> ForceSerializationHelper.forceSerialization(
			() -> snmpTableSource.accept(processor),
			telemetryManager,
			CONNECTOR_ID,
			snmpTableSource,
			DESCRIPTION,
			SourceTable.empty()
		);

		final Callable<SourceTable> callable2 = () -> ForceSerializationHelper.forceSerialization(
			() -> snmpTableSource.accept(processor),
			telemetryManager,
			CONNECTOR_ID,
			snmpTableSource,
			DESCRIPTION,
			SourceTable.empty()
		);

		// This only checks the behavior for two parallel threads to validate there is no crash.

		final Future<SourceTable> future1 = threadsPool.submit(callable1);
		final Future<SourceTable> future2 = threadsPool.submit(callable2);

		final SourceTable result2 = future2.get(120, TimeUnit.SECONDS);
		final SourceTable result1 = future1.get(120, TimeUnit.SECONDS);

		assertEquals(EXPECTED_SOURCE_TABLE, result1);
		assertEquals(EXPECTED_SOURCE_TABLE, result2);
	}

	/**
	 * Creates a telemetry manager for {@link #HOST_NAME}.
	 *
	 * @return a new telemetry manager
	 */
	private static TelemetryManager newTelemetryManager() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname(HOST_NAME).build());
		return telemetryManager;
	}

	/**
	 * A device request that ignores interrupts: it signals {@code started}, then ends only once {@code release} is
	 * counted down.
	 *
	 * @param started the latch counted down when the request starts
	 * @param release the latch that ends the request
	 * @param ended   set when the request ended
	 * @return the table of the request, or an empty table when its caller was interrupted
	 */
	private static SourceTable deviceRequest(
		final CountDownLatch started,
		final CountDownLatch release,
		final AtomicBoolean ended
	) {
		try {
			return ThreadHelper.executeInterruptibly(() -> {
				started.countDown();
				while (true) {
					try {
						release.await();
						break;
					} catch (InterruptedException e) {
						// Ignored, like the libraries the wrapper is for
					}
				}
				ended.set(true);
				return EXPECTED_SOURCE_TABLE;
			});
		} catch (InterruptedException e) {
			return SourceTable.empty();
		} catch (ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Runs the given serialized job on a new thread, interrupts it once the device request started and waits for it.
	 *
	 * @param job     the job
	 * @param started the latch counted down when the device request starts
	 * @throws InterruptedException never
	 */
	private static void cutShort(final Runnable job, final CountDownLatch started) throws InterruptedException {
		final Thread thread = Thread.ofPlatform().start(job);
		assertTrue(started.await(5, TimeUnit.SECONDS));
		thread.interrupt();
		thread.join(1000);
		assertFalse(thread.isAlive(), "The job cut short returns at once");
	}

	@Test
	void testNextSerializedRequestWaitsForTheRequestOfAJobCutShort() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicBoolean ended = new AtomicBoolean();
		try {
			cutShort(
				() -> ForceSerializationHelper.forceSerialization(
					() -> deviceRequest(started, release, ended),
					telemetryManager,
					CONNECTOR_ID,
					null,
					DESCRIPTION,
					SourceTable.empty()
				),
				started
			);
			assertFalse(namespace.getForceSerializationLock().isLocked(), "The job cut short released the lock");
			assertEquals(1, namespace.getSerializedRequestsInFlight().size());

			final AtomicBoolean ranAfterTheRequestEnded = new AtomicBoolean();
			final Thread next = Thread.ofPlatform().start(() -> ForceSerializationHelper.forceSerialization(() -> {
				ranAfterTheRequestEnded.set(ended.get());
				return EXPECTED_SOURCE_TABLE;
			}, telemetryManager, CONNECTOR_ID, null, DESCRIPTION, SourceTable.empty()));
			next.join(300);
			assertTrue(next.isAlive(), "The next serialized request waits while the device still runs a request");

			release.countDown();
			next.join(5000);
			assertFalse(next.isAlive());
			assertTrue(ranAfterTheRequestEnded.get());
			assertTrue(namespace.getSerializedRequestsInFlight().isEmpty());
		} finally {
			release.countDown();
		}
	}

	@Test
	void testRequestOfAJobCutShortStillRunningAtTheDeadline() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		try {
			cutShort(
				() -> ForceSerializationHelper.forceSerialization(
					() -> deviceRequest(started, release, new AtomicBoolean()),
					telemetryManager,
					CONNECTOR_ID,
					null,
					DESCRIPTION,
					SourceTable.empty()
				),
				started
			);

			final SourceTable defaultValue = SourceTable.empty();
			final AtomicBoolean ran = new AtomicBoolean();
			final long start = System.nanoTime();
			assertSame(defaultValue, ForceSerializationHelper.forceSerialization(() -> {
				ran.set(true);
				return EXPECTED_SOURCE_TABLE;
			}, telemetryManager, CONNECTOR_ID, null, DESCRIPTION, defaultValue, 1));
			final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			assertFalse(ran.get());
			assertTrue(elapsedMillis >= 900 && elapsedMillis < 3000, "Took " + elapsedMillis + " ms");
			assertFalse(
				telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).getForceSerializationLock().isLocked()
			);
		} finally {
			release.countDown();
		}
	}

	@Test
	void testReentrantSerializationTracksTheRequestsAfterTheInnerCall() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final AtomicReference<SourceTable> inner = new AtomicReference<>();
		try {
			cutShort(() -> ForceSerializationHelper.forceSerialization(() -> {
				inner.set(
					ForceSerializationHelper.forceSerialization(
						() -> EXPECTED_SOURCE_TABLE,
						telemetryManager,
						CONNECTOR_ID,
						null,
						DESCRIPTION,
						SourceTable.empty()
					)
				);
				return deviceRequest(started, release, new AtomicBoolean());
			}, telemetryManager, CONNECTOR_ID, null, DESCRIPTION, SourceTable.empty()), started);
			assertSame(EXPECTED_SOURCE_TABLE, inner.get());
			assertEquals(1, namespace.getSerializedRequestsInFlight().size());
			assertFalse(namespace.getForceSerializationLock().isLocked());
		} finally {
			release.countDown();
		}
	}
}
