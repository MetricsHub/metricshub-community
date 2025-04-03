package org.sentrysoftware.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.sentrysoftware.metricshub.agent.helper.AgentConstants.CONFIG_EXAMPLE_FILENAME;
import static org.sentrysoftware.metricshub.agent.helper.AgentConstants.DEFAULT_CONFIG_FILENAME;
import static org.sentrysoftware.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_PARIS_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SERVER_1_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.TEST_CONFIG_FILE_PATH;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.TOP_LEVEL_RESOURCES_CONFIG_PATH;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.extension.snmp.SnmpExtension;
import org.mockito.MockedStatic;
import org.sentrysoftware.metricshub.agent.config.AgentConfig;
import org.sentrysoftware.metricshub.agent.config.StateSetMetricCompression;
import org.sentrysoftware.metricshub.agent.context.AgentContext;
import org.sentrysoftware.metricshub.engine.common.helpers.JsonHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.common.helpers.ResourceHelper;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.metric.MetricDefinition;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

class ConfigHelperTest {

	private static final String RESOURCE_KEY = "resource-test-key";
	private static final String PURE_STORAGE_REST_CONNECTOR_ID = "PureStorageREST";

	@TempDir
	static Path tempDir;

	// Initialize the extension manager required by the agent context
	final ExtensionManager extensionManager = ExtensionManager
		.builder()
		.withProtocolExtensions(List.of(new SnmpExtension()))
		.build();

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testGetProgramDataConfigFile() {
		// ProgramData invalid
		{
			try (
				final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class);
				final MockedStatic<ResourceHelper> mockedResourceHelper = mockStatic(ResourceHelper.class)
			) {
				mockedConfigHelper.when(() -> ConfigHelper.getProgramDataPath()).thenReturn(Optional.empty());
				mockedConfigHelper
					.when(() -> ConfigHelper.getProgramDataConfigFile(anyString(), anyString()))
					.thenCallRealMethod();
				mockedConfigHelper.when(() -> ConfigHelper.getSubPath(anyString())).thenCallRealMethod();
				mockedConfigHelper.when(() -> ConfigHelper.getSourceDirectory()).thenCallRealMethod();

				mockedResourceHelper
					.when(() -> ResourceHelper.findSourceDirectory(ConfigHelper.class))
					.thenAnswer(invocation -> tempDir.resolve("metricshub/app/jar").toFile());

				final Path configFileOnWindows = ConfigHelper.getProgramDataConfigFile("config", DEFAULT_CONFIG_FILENAME);

				final String expectedPath = "metricshub\\app\\..\\config\\" + DEFAULT_CONFIG_FILENAME;

				assertNotNull(configFileOnWindows);
				assertTrue(
					() -> configFileOnWindows.endsWith("metricshub\\app\\..\\config\\" + DEFAULT_CONFIG_FILENAME),
					String.format("Found path %s. Expected path ends with %s.", configFileOnWindows.toString(), expectedPath)
				);
			}
		}

		// ProgramData valid
		{
			try (final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class)) {
				mockedConfigHelper.when(() -> ConfigHelper.getProgramDataPath()).thenReturn(Optional.of(tempDir.toString()));

				mockedConfigHelper.when(() -> ConfigHelper.createDirectories(any(Path.class))).thenCallRealMethod();
				mockedConfigHelper
					.when(() -> ConfigHelper.getProgramDataConfigFile(anyString(), anyString()))
					.thenCallRealMethod();

				final Path configFileOnWindows = ConfigHelper.getProgramDataConfigFile("config", DEFAULT_CONFIG_FILENAME);

				final String expectedPath = "metricshub\\config\\" + DEFAULT_CONFIG_FILENAME;

				assertNotNull(configFileOnWindows);
				assertTrue(
					() -> configFileOnWindows.endsWith(expectedPath),
					String.format("Found path %s. Expected path ends with %s.", configFileOnWindows.toString(), expectedPath)
				);
			}
		}
	}

	@Test
	void testGetDefaultConfigFilePermission() throws IOException {
		try (final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class)) {
			// Build a config directory
			final Path configDir = Files.createDirectories(tempDir.resolve("metricshub\\config").toAbsolutePath());

			// Create the example file
			final Path examplePath = Path.of(configDir + "\\" + CONFIG_EXAMPLE_FILENAME);
			Files.copy(
				Path.of("src", "test", "resources", "config", DEFAULT_CONFIG_FILENAME),
				examplePath,
				StandardCopyOption.REPLACE_EXISTING
			);

			// Mock the method which gets the real production config file
			mockedConfigHelper
				.when(() -> ConfigHelper.getDefaultConfigFilePath(anyString(), anyString()))
				.thenAnswer(invocation -> Paths.get(configDir.toString(), DEFAULT_CONFIG_FILENAME));

			// Call real method when invoking getDefaultConfigFile
			mockedConfigHelper
				.when(() -> ConfigHelper.getDefaultConfigFile(anyString(), anyString(), anyString()))
				.thenCallRealMethod();

			// Mock getSubPath as it will try to retrieve the example file deployed in production environment
			mockedConfigHelper.when(() -> ConfigHelper.getSubPath(anyString())).thenReturn(examplePath);

			// Call the real method
			File file = ConfigHelper.getDefaultConfigFile("config", DEFAULT_CONFIG_FILENAME, CONFIG_EXAMPLE_FILENAME);
			assertTrue(file.canWrite());
		}
	}

	@Test
	void testBuildTelemetryManagers() throws IOException {
		final File configFile = ConfigHelper.findConfigFile("src/test/resources/config/metricshub-server1.yaml");

		final ConnectorStore connectorStore = new ConnectorStore(Path.of("src/test/resources"));
		final Connector connector = new Connector();
		connector.getOrCreateConnectorIdentity().setCompiledFilename(PURE_STORAGE_REST_CONNECTOR_ID);
		connectorStore.addOne(PURE_STORAGE_REST_CONNECTOR_ID, connector);

		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = ConfigHelper.buildTelemetryManagers(
			agentConfig,
			connectorStore
		);

		final Map<String, TelemetryManager> resourceGroupTelemetryManagers = telemetryManagers.get(
			SENTRY_PARIS_RESOURCE_GROUP_KEY
		);
		assertNotNull(resourceGroupTelemetryManagers);
		final TelemetryManager telemetryManager = resourceGroupTelemetryManagers.get(SERVER_1_RESOURCE_GROUP_KEY);
		assertNotNull(telemetryManager);
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig
				.getResourceGroups()
				.get(SENTRY_PARIS_RESOURCE_GROUP_KEY)
				.getResources()
				.get(SERVER_1_RESOURCE_GROUP_KEY)
				.getAttributes()
				.get(MetricsHubConstants.HOST_NAME),
			hostConfiguration.getHostname()
		);

		assertEquals(Set.of("+" + PURE_STORAGE_REST_CONNECTOR_ID), hostConfiguration.getConnectors());
	}

	@Test
	void testBuildTelemetryManagersWithTopLevelResources() throws IOException {
		// Find the configuration file
		final File configFile = ConfigHelper.findConfigFile(TOP_LEVEL_RESOURCES_CONFIG_PATH);

		// Create the connector store
		final ConnectorStore connectorStore = new ConnectorStore(Path.of("src/test/resources"));
		final Connector connector = new Connector();
		connector.getOrCreateConnectorIdentity().setCompiledFilename(PURE_STORAGE_REST_CONNECTOR_ID);
		connectorStore.addOne(PURE_STORAGE_REST_CONNECTOR_ID, connector);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = ConfigHelper.buildTelemetryManagers(
			agentConfig,
			connectorStore
		);

		// Check that top-level resources are added to the telemetry managers map

		assertEquals(2, telemetryManagers.size());

		// Check resources under resource groups

		final Map<String, TelemetryManager> resourceGroupTelemetryManagers = telemetryManagers.get(
			SENTRY_PARIS_RESOURCE_GROUP_KEY
		);
		assertNotNull(resourceGroupTelemetryManagers);
		final TelemetryManager telemetryManager = resourceGroupTelemetryManagers.get(SERVER_1_RESOURCE_GROUP_KEY);
		assertNotNull(telemetryManager);
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig
				.getResourceGroups()
				.get(SENTRY_PARIS_RESOURCE_GROUP_KEY)
				.getResources()
				.get(SERVER_1_RESOURCE_GROUP_KEY)
				.getAttributes()
				.get(MetricsHubConstants.HOST_NAME),
			hostConfiguration.getHostname()
		);

		assertEquals(Set.of("+" + PURE_STORAGE_REST_CONNECTOR_ID), hostConfiguration.getConnectors());

		// Check resources under agent config (top-level resources)
		final Map<String, TelemetryManager> topLevelResourcesTelemetryManagers = telemetryManagers.get(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY
		);
		assertNotNull(topLevelResourcesTelemetryManagers);
		final TelemetryManager topLevelTelemetryManager = topLevelResourcesTelemetryManagers.get("server-2");
		assertNotNull(topLevelTelemetryManager);
		final HostConfiguration topLevelhostConfiguration = topLevelTelemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig.getResources().get("server-2").getAttributes().get(MetricsHubConstants.HOST_NAME),
			topLevelhostConfiguration.getHostname()
		);
	}

	@Test
	void testEnableSelfMonitoringWithTopLevelResources() throws IOException {
		// Find the configuration file
		final File configFile = ConfigHelper.findConfigFile(TOP_LEVEL_RESOURCES_CONFIG_PATH);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration for top-level resources
		assertFalse(agentConfig.getResources().get("server-2").getEnableSelfMonitoring());
	}

	@Test
	void testEnableSelfMonitoringOnlyGlobalConfiguration() throws IOException {
		// Find the configuration file
		final File configFile = ConfigHelper.findConfigFile(TEST_CONFIG_FILE_PATH);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertTrue(
			agentConfig.getResourceGroups().get("sentry-paris").getResources().get("server-1").getEnableSelfMonitoring()
		);
	}

	@Test
	void testEnableSelfMonitoringConfigurationOverride() throws IOException {
		// Find the configuration file
		final File configFile = ConfigHelper.findConfigFile(
			"src/test/resources/config/metricshub-enable-self-monitoring-override.yaml"
		);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertFalse(
			agentConfig.getResourceGroups().get("sentry-paris").getResources().get("server-1").getEnableSelfMonitoring()
		);
	}

	@Test
	void testEnableSelfMonitoringNoConfiguration() throws IOException {
		// Find the configuration file
		final File configFile = ConfigHelper.findConfigFile(
			"src/test/resources/config/metricshub-enable-self-monitoring-no-config.yaml"
		);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			new FileInputStream(configFile),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertTrue(
			agentConfig.getResourceGroups().get("sentry-paris").getResources().get("server-1").getEnableSelfMonitoring()
		);
	}

	@Test
	void testUpdateConnectorStore() {
		// Create a custom connectors Map
		final Map<String, Connector> customConnectors = Map.of("custom-connector-1", new Connector());

		// Initialize the original connector store
		final ConnectorStore connectorStore = new ConnectorStore(Path.of("src/test/resources/storeMerge"));

		// Call updateConnectorStore
		ConfigHelper.updateConnectorStore(connectorStore, customConnectors);

		final Map<String, Connector> store = connectorStore.getStore();
		// Check that the merge of custom and standard connectors was successfully executed
		assertEquals(2, store.size());
		assertTrue(store.containsKey("custom-connector-1"));
		assertTrue(store.containsKey("noTemplateVariable"));
	}

	@Test
	void testNormalizeConfiguredConnector() {
		assertDoesNotThrow(() ->
			ConfigHelper.normalizeConfiguredConnector(SENTRY_PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, null)
		);
		final Connector configuredConnector = new Connector();
		ConfigHelper.normalizeConfiguredConnector(SENTRY_PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, configuredConnector);
		assertEquals(
			"MetricsHub-Configured-Connector-sentry-paris-resource-test-key",
			configuredConnector.getCompiledFilename()
		);
	}

	@Test
	void testAddConfiguredConnector() {
		// Create a ConnectorStore with a specified path
		final ConnectorStore connectorStore = new ConnectorStore(Path.of("src/test/resources"));
		final int initialSize = connectorStore.getStore().size();

		// Test Case 1: No configured connector
		{
			// Attempt to add a null configured connector
			assertDoesNotThrow(() -> ConfigHelper.addConfiguredConnector(connectorStore, null));

			// Ensure that the ConnectorStore size has remained unchanged
			assertEquals(initialSize, connectorStore.getStore().size());
		}

		// Test Case 2: Existing configured connector
		{
			// Create a new Connector and configure it
			final Connector configuredConnector = new Connector();
			ConfigHelper.normalizeConfiguredConnector(SENTRY_PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, configuredConnector);

			// Attempt to add the configured connector
			ConfigHelper.addConfiguredConnector(connectorStore, configuredConnector);

			// Verify that the configured connector is now in the ConnectorStore
			assertEquals(
				configuredConnector,
				connectorStore.getStore().get("MetricsHub-Configured-Connector-sentry-paris-resource-test-key")
			);
		}
	}

	@Test
	void testFetchMetricDefinitions() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Map<String, MetricDefinition> metricDefintionMap = Map.of(
			"metric",
			MetricDefinition.builder().unit("unit").build()
		);
		connectorStore.setStore(
			Map.of(PURE_STORAGE_REST_CONNECTOR_ID, Connector.builder().metrics(metricDefintionMap).build())
		);

		final Map<String, MetricDefinition> defaultMetricDefinitionMap = Map.of(
			MetricsHubConstants.CONNECTOR_STATUS_METRIC_KEY,
			MetricsHubConstants.CONNECTOR_STATUS_METRIC_DEFINITION
		);

		final Map<String, MetricDefinition> expected = new HashMap<>();
		expected.putAll(metricDefintionMap);
		expected.putAll(defaultMetricDefinitionMap);

		assertEquals(expected, ConfigHelper.fetchMetricDefinitions(connectorStore, PURE_STORAGE_REST_CONNECTOR_ID));
		assertEquals(defaultMetricDefinitionMap, ConfigHelper.fetchMetricDefinitions(connectorStore, "other"));
		assertEquals(defaultMetricDefinitionMap, ConfigHelper.fetchMetricDefinitions(null, null));
		assertEquals(defaultMetricDefinitionMap, ConfigHelper.fetchMetricDefinitions(null, PURE_STORAGE_REST_CONNECTOR_ID));
		assertEquals(defaultMetricDefinitionMap, ConfigHelper.fetchMetricDefinitions(connectorStore, null));
	}

	@Test
	void testCalculateMD5Checksum() {
		// Check that calculateMD5Checksum returns always the same value for the same input file
		final File file = Path.of("src", "test", "resources", "md5Checksum", "checkSumTest.txt").toFile();
		final String md5CheckSumFirstCallResult = ConfigHelper.calculateMD5Checksum(file);
		final String md5CheckSumSecondCallResult = ConfigHelper.calculateMD5Checksum(file);
		assertNotNull(md5CheckSumFirstCallResult);
		assertNotNull(md5CheckSumSecondCallResult);
		assertEquals(md5CheckSumFirstCallResult, md5CheckSumSecondCallResult);

		// Check that calculateMD5Checksum returns different values for different input files
		final File secondFile = Path.of("src", "test", "resources", "md5Checksum", "otherCheckSumTest.txt").toFile();
		final String md5CheckSumFirstFileResult = ConfigHelper.calculateMD5Checksum(file);
		final String md5CheckSumSecondFileResult = ConfigHelper.calculateMD5Checksum(secondFile);
		assertNotNull(md5CheckSumFirstFileResult);
		assertNotNull(md5CheckSumSecondFileResult);
		assertNotEquals(md5CheckSumFirstFileResult, md5CheckSumSecondFileResult);
	}

	@Test
	void isSuppressZerosCompression_shouldReturnTrueForMatchingValue() {
		assertTrue(
			ConfigHelper.isSuppressZerosCompression(StateSetMetricCompression.SUPPRESS_ZEROS),
			"Should return true for suppress_zeros"
		);
	}

	@Test
	void isSuppressZerosCompression_shouldReturnFalseForNonMatchingValue() {
		assertFalse(
			ConfigHelper.isSuppressZerosCompression(StateSetMetricCompression.NONE),
			"Should return false for non-matching value"
		);
	}
}
