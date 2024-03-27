package org.sentrysoftware.metricshub.extension.snmp.it;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.strategy.collect.CollectStrategy;
import org.sentrysoftware.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.sentrysoftware.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.sentrysoftware.metricshub.engine.strategy.detection.DetectionStrategy;
import org.sentrysoftware.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.snmp.SnmpConfiguration;
import org.sentrysoftware.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;
import org.sentrysoftware.metricshub.extension.snmp.SnmpExtension;
import org.sentrysoftware.metricshub.extension.snmp.it.job.SnmpITJob;

class DellOpenManageIT {
	static {
		Locale.setDefault(Locale.US);
	}

	private static final String CONNECTOR_ID = "DellOpenManage";
	private static final Path CONNECTOR_DIRECTORY = Paths.get(
		"src",
		"it",
		"resources",
		"snmp",
		"DellOpenManageIT",
		"connectors"
	);
	private static final String LOCALHOST = "localhost";

	private static TelemetryManager telemetryManager;
	private static ClientsExecutor clientsExecutor;
	private static ExtensionManager extensionManager;

	@BeforeAll
	static void setUp() throws Exception {
		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community("public".toCharArray())
			.version(SnmpVersion.V1)
			.timeout(120L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(LOCALHOST)
			.hostname(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.connectors(Set.of("+" + CONNECTOR_ID))
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		final ConnectorStore connectorStore = new ConnectorStore(CONNECTOR_DIRECTORY);

		telemetryManager =
			TelemetryManager.builder().connectorStore(connectorStore).hostConfiguration(hostConfiguration).build();

		clientsExecutor = new ClientsExecutor(telemetryManager);

		extensionManager.setProtocolExtensions(List.of(new SnmpExtension()));
	}

	@Test
	void test() throws Exception {
		long discoveryTime = System.currentTimeMillis();
		long collectTime = discoveryTime + 60 * 2 * 1000;
		new SnmpITJob(telemetryManager)
			.withServerRecordData("snmp/DellOpenManageIT/input/input.snmp")
			.executeStrategies(
				new DetectionStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new DiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager)
			)
			.executeStrategies(
				new PrepareCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new ProtocolHealthCheckStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
				new CollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager)
			)
			.verifyExpected("snmp/DellOpenManageIT/expected/expected.json");
	}
}
