package org.metricshub.hardware.it;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.OsCommandConfiguration;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.metricshub.hardware.strategy.HardwareStrategy;
import org.metricshub.it.job.oscommand.SuperConnectorITJob;

class SuperConnectorOsIT {
	static {
		Locale.setDefault(Locale.US);
	}

	private static final String CONNECTOR_ID = "SuperConnectorOs";
	private static final Path CONNECTOR_DIRECTORY = Paths.get(
		"src",
		"it",
		"resources",
		"os",
		"SuperConnectorOsIT",
		"connectors"
	);
	private static final String LOCALHOST = "localhost";

	private static TelemetryManager telemetryManager;
	private static ClientsExecutor clientsExecutor;
	private static ExtensionManager extensionManager;

	@BeforeAll
	static void setUp() {
		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration.builder().timeout(120L).build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(LOCALHOST)
			.hostname(LOCALHOST)
			.hostType(DeviceKind.STORAGE)
			.connectors(Set.of("+" + CONNECTOR_ID))
			.configurations(Map.of(OsCommandConfiguration.class, osCommandConfiguration))
			.build();

		final ConnectorStore connectorStore = new ConnectorStore(CONNECTOR_DIRECTORY);

		telemetryManager =
			TelemetryManager.builder().connectorStore(connectorStore).hostConfiguration(hostConfiguration).build();

		clientsExecutor = new ClientsExecutor(telemetryManager);

		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(new OsCommandExtension())).build();
	}

	@Test
	void test() throws Exception {
		long discoveryTime = System.currentTimeMillis();
		long collectTime = discoveryTime + 60 * 2 * 1000;
		new SuperConnectorITJob(clientsExecutor, telemetryManager)
			.executeStrategies(
				new DetectionStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new DiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new HardwarePostDiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager)
			)
			.executeStrategies(
				new PrepareCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new ProtocolHealthCheckStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new CollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new HardwarePostCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new HardwareStrategy(telemetryManager, collectTime)
			)
			.verifyExpected("os/SuperConnectorOsIT/expected/expected.json");
	}
}
