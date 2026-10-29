package org.metricshub.engine.strategy.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;

class ToolStrategyTest {

	private static final Path YAML_TEST_PATH = Paths.get("src", "test", "resources", "test-files", "strategy", "tool");
	private static final String CONNECTOR_ID = "TestConnectorWithTool";
	private static final String TOOL = "listThings";

	private ToolStrategy newStrategy(final String toolName, final Map<String, String> arguments) {
		final ConnectorStore connectorStore = new ConnectorStore(YAML_TEST_PATH);
		final Connector connector = connectorStore.getStore().get(CONNECTOR_ID);
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.connectorStore(connectorStore)
			.hostConfiguration(
				HostConfiguration.builder().hostId("host-01").hostname("host-01").hostType(DeviceKind.LINUX).build()
			)
			.build();
		return ToolStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(System.currentTimeMillis())
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(ExtensionManager.empty())
			.connector(connector)
			.toolName(toolName)
			.arguments(arguments)
			.build();
	}

	@Test
	void testRunSubstitutesArgumentsInSourcesAndComputes() {
		final ToolStrategy strategy = newStrategy(TOOL, Map.of("pattern", "^b", "owner", "me"));

		strategy.run();

		// The filter ran on the first source, the append on the second: both read ${param::...}
		assertEquals(List.of(List.of("beta", "2-me")), strategy.getResult().getTable());
	}

	@Test
	void testRunAppliesParameterDefaults() {
		final ToolStrategy strategy = newStrategy(TOOL, Map.of("owner", "me"));

		strategy.run();

		assertEquals(
			List.of(List.of("alpha", "1-me"), List.of("beta", "2-me"), List.of("gamma", "3-me")),
			strategy.getResult().getTable()
		);
	}

	@Test
	void testRunRejectsMissingRequiredArgument() {
		final ToolStrategy strategy = newStrategy(TOOL, Map.of("pattern", ".*"));

		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, strategy::run);

		assertTrue(exception.getMessage().contains("owner"), exception.getMessage());
		assertTrue(strategy.getResult().isEmpty());
	}

	@Test
	void testRunRejectsUnknownTool() {
		final ToolStrategy strategy = newStrategy("nope", Map.of());

		assertThrows(IllegalArgumentException.class, strategy::run);
	}
}
