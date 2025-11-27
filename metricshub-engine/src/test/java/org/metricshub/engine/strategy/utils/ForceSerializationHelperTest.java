package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.EXPECTED_SNMP_TABLE_DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
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
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForceSerializationHelperTest {

	@Mock
	private IProtocolExtension protocolExtensionMock;

	private static final String HOST_NAME = "host.test.force.serialization";
	private static final String SELECT_COLUMNS = "ID,1,3";
	private static final SourceTable EXPECTED_SOURCE_TABLE = SourceTable
		.builder()
		.table(EXPECTED_SNMP_TABLE_DATA)
		.headers(Arrays.asList(SELECT_COLUMNS.split(MetricsHubConstants.COMMA)))
		.build();
	private static final String DESCRIPTION = "source";
	private static final String CONNECTOR_ID = "connector";

	@Test
	void testForceSerializationNullArguments() {
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		assertThrows(
			IllegalArgumentException.class,
			() ->
				ForceSerializationHelper.forceSerialization(
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
			() ->
				ForceSerializationHelper.forceSerialization(
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
			() ->
				ForceSerializationHelper.forceSerialization(
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
			() ->
				ForceSerializationHelper.forceSerialization(
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
			() ->
				ForceSerializationHelper.forceSerialization(
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
				() -> SourceTable.builder().table(List.of(List.of("a", "b", "c"))),
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
	void testForceSerializationRestoresInterruptFlagAfterClearing() {
		final SourceTable emptySourceTable = SourceTable.empty();
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Source snmpTableSource = SnmpTableSource.builder().oid("1.2.3.4").selectColumns(SELECT_COLUMNS).build();

		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname(HOST_NAME).build());

		Thread.currentThread().interrupt();
		final boolean wasInterruptedBefore = Thread.currentThread().isInterrupted();

		final SourceTable returned = ForceSerializationHelper.forceSerialization(
			() -> {
				assertFalse(Thread.currentThread().isInterrupted());
				return EXPECTED_SOURCE_TABLE;
			},
			telemetryManager,
			CONNECTOR_ID,
			snmpTableSource,
			DESCRIPTION,
			emptySourceTable
		);

		assertEquals(EXPECTED_SOURCE_TABLE, returned);
		assertTrue(wasInterruptedBefore);
		assertTrue(Thread.currentThread().isInterrupted());
		Thread.interrupted();
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
				() -> SourceTable.builder().table(List.of(List.of("a", "b", "c"))),
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
		final Source snmpTableSource = SnmpTableSource
			.builder()
			.oid("1.2.3.4")
			.selectColumns(SELECT_COLUMNS)
			.forceSerialization(true)
			.build();

		final ClientsExecutor clientsExecutor = spy(ClientsExecutor.class);
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		doReturn(EXPECTED_SOURCE_TABLE)
			.when(protocolExtensionMock)
			.processSource(eq(snmpTableSource), anyString(), any(TelemetryManager.class));

		final ISourceProcessor processor = SourceProcessor
			.builder()
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
		final Source snmpTableSource = SnmpTableSource
			.builder()
			.oid("1.2.3.4")
			.selectColumns(SELECT_COLUMNS)
			.forceSerialization(true)
			.build();

		final ClientsExecutor clientsExecutor = spy(ClientsExecutor.class);
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();

		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		// Mock source table information for enclosure
		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		doReturn(EXPECTED_SOURCE_TABLE)
			.when(protocolExtensionMock)
			.processSource(eq(snmpTableSource), anyString(), any(TelemetryManager.class));

		final ISourceProcessor processor = SourceProcessor
			.builder()
			.connectorId(CONNECTOR_ID)
			.clientsExecutor(clientsExecutor)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build();

		final ExecutorService threadsPool = Executors.newFixedThreadPool(2);

		final Callable<SourceTable> callable1 = () ->
			ForceSerializationHelper.forceSerialization(
				() -> snmpTableSource.accept(processor),
				telemetryManager,
				CONNECTOR_ID,
				snmpTableSource,
				DESCRIPTION,
				SourceTable.empty()
			);

		final Callable<SourceTable> callable2 = () ->
			ForceSerializationHelper.forceSerialization(
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
}
