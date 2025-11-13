package org.metricshub.extension.snmp.it;

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
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.it.job.snmp.SnmpITJob;

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
	static void setUp() {
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

		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(new SnmpExtension())).build();
	}

	/**
	 * Updates the port in the given configuration to allow the engine access to this port.
	 *
	 * @param telemetryManager the telemetry manager where metrics are collected
	 * @param port             the port of the configuration to be updated
	 */
	static void updateSnmpPort(TelemetryManager telemetryManager, Integer port) {
		final SnmpConfiguration snmpConfiguration = (SnmpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SnmpConfiguration.class);

		snmpConfiguration.setPort(port);
	}

	@Test
	void test() throws Exception {
		long discoveryTime = System.currentTimeMillis();
		long collectTime = discoveryTime + 60 * 2 * 1000;
		new SnmpITJob(telemetryManager, DellOpenManageIT::updateSnmpPort)
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
