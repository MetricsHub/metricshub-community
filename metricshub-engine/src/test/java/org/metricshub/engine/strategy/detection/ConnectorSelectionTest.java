package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.engine.constants.Constants.CONNECTOR;
import static org.metricshub.engine.constants.Constants.DETECTION_FOLDER;
import static org.metricshub.engine.constants.Constants.STRATEGY_TIME;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.extension.ExtensionManager;
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
null,null
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
			null, null
		);
		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();

		assertEquals(
			Collections.emptyList(),
			new ConnectorSelection(telemetryManager, clientsExecutor, Set.of(CONNECTOR), extensionManager).run()
		);
	}
}
