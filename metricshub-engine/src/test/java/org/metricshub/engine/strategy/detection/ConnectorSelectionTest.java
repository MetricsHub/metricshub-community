package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.DETECTION_FOLDER;
import static org.metricshub.engine.constants.Constants.STRATEGY_TIME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

class ConnectorSelectionTest {

	@Test
	void testRunNull() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();
		final Set<String> emptySet = Collections.emptySet();
		assertThrows(
			IllegalArgumentException.class,
			() -> new ConnectorSelection(null, clientsExecutor, emptySet, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new ConnectorSelection(telemetryManager, null, emptySet, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new ConnectorSelection(telemetryManager, clientsExecutor, null, extensionManager)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new ConnectorSelection(telemetryManager, clientsExecutor, emptySet, null)
		);
	}

	@Test
	void testRunEmptyTelemetryManager() {
		TelemetryManager telemetryManager = new TelemetryManager();
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final ExtensionManager extensionManager = ExtensionManager.empty();
		assertEquals(
			Collections.emptyList(),
			new ConnectorSelection(telemetryManager, clientsExecutor, Collections.emptySet(), extensionManager).run()
		);
	}

	@Test
	void testRunNoSelectedConnectors() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final HostConfiguration hostConfiguration = new HostConfiguration();
		hostConfiguration.setHostname("localhost");

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(new Detection());

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			null
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();

		assertEquals(
			Collections.emptyList(),
			new ConnectorSelection(telemetryManager, clientsExecutor, Collections.emptySet(), extensionManager).run()
		);
	}

	@Test
	void testRunConnectorNotSelected() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final HostConfiguration hostConfiguration = new HostConfiguration();
		hostConfiguration.setHostname("localhost");

		final ConnectorIdentity connectorIdentity = new ConnectorIdentity();
		connectorIdentity.setDetection(new Detection());

		final Connector connector = new Connector();
		connector.setConnectorIdentity(connectorIdentity);

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		// Connector not in the selected connectors set
		connectorStore.getStore().put("connector2", connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			null
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();

		assertEquals(
			Collections.emptyList(),
			new ConnectorSelection(telemetryManager, clientsExecutor, Set.of(CONNECTOR), extensionManager).run()
		);
	}

	@Test
	void testDetectionIgnoresHealthChecks() {
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		final HostProperties hostProperties = new HostProperties();
		hostProperties.setLocalhost(true);

		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname("localhost")
			.hostType(DeviceKind.LINUX)
			.sequential(true)
			.build();

		final Criterion detectionCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.LINUX))
			.build();
		final Criterion healthCheckCriterion = DeviceTypeCriterion.builder()
			.type("deviceType")
			.keep(Set.of(DeviceKind.WINDOWS))
			.build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(CONNECTOR)
					.detection(
						Detection.builder().appliesTo(Set.of(DeviceKind.LINUX)).criteria(List.of(detectionCriterion)).build()
					)
					.healthChecks(List.of(healthCheckCriterion))
					.build()
			)
			.build();

		final File store = new File(DETECTION_FOLDER);
		final Path storePath = store.toPath();

		final ConnectorStore connectorStore = new ConnectorStore(storePath);
		connectorStore.getStore().put(CONNECTOR, connector);

		final TelemetryManager telemetryManager = new TelemetryManager(
			monitors,
			hostProperties,
			hostConfiguration,
			connectorStore,
			STRATEGY_TIME,
			null
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		final List<ConnectorTestResult> results = new ConnectorSelection(
			telemetryManager,
			clientsExecutor,
			Set.of(CONNECTOR),
			ExtensionManager.empty()
		).run();

		assertEquals(1, results.size());
		assertTrue(results.get(0).isSuccess());
	}

	@Test
	void testInterruptedParallelDetectionReturnsNoResultOfAThreadCutShort() throws Exception {
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostId("host")
			.hostname("host")
			.hostType(DeviceKind.LINUX)
			.sequential(false)
			.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		final Connector connector = Connector.builder()
			.connectorIdentity(
				ConnectorIdentity.builder()
					.compiledFilename(CONNECTOR)
					.detection(
						Detection.builder()
							.appliesTo(Set.of(DeviceKind.LINUX))
							.criteria(List.of(SnmpGetCriterion.builder().oid("1.3.1").build()))
							.build()
					)
					.build()
			)
			.build();

		// The criterion of the pool thread answers only after the caller stopped waiting for the pool
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(SnmpGetCriterion.class)).when(extension).getSupportedCriteria();
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch callerReturned = new CountDownLatch(1);
		final AtomicReference<Thread> poolThread = new AtomicReference<>();
		doAnswer(invocation -> {
			poolThread.set(Thread.currentThread());
			started.countDown();
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException e) {
				// The forced shutdown of the pool
				callerReturned.await();
				Thread.currentThread().interrupt();
			}
			return CriterionTestResult.success(invocation.getArgument(0), "ok");
		}).when(extension).processCriterion(any(), anyString(), any(), anyBoolean());

		final ConnectorSelection connectorSelection = new ConnectorSelection(
			telemetryManager,
			new ClientsExecutor(telemetryManager),
			Set.of(CONNECTOR),
			ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build()
		);
		final AtomicReference<Stream<ConnectorTestResult>> results = new AtomicReference<>();
		final Thread caller = new Thread(
			() -> results.set(connectorSelection.runAllConnectorsDetectionCriteria(Stream.of(connector), hostConfiguration))
		);
		caller.start();
		assertTrue(started.await(5, TimeUnit.SECONDS));

		// The job deadline expires while the caller waits for the pool
		caller.interrupt();
		caller.join(5_000);
		assertFalse(caller.isAlive());
		callerReturned.countDown();

		// The pool thread ends once its detection, cut short, is over
		poolThread.get().join(5_000);
		assertFalse(poolThread.get().isAlive());

		// The detection cut short would fail a connector that matches: it is not returned, even read after the pool ended
		assertEquals(List.of(), results.get().toList());
	}
}
