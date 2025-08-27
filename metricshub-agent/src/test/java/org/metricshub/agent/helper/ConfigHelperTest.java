package org.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.AgentConstants.CONFIG_EXAMPLE_FILENAME;
import static org.metricshub.agent.helper.AgentConstants.DEFAULT_CONFIG_FILENAME;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.PARIS_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.SERVER_1_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.TEST_CONFIG_DIRECTORY_PATH;
import static org.metricshub.agent.helper.TestConstants.TOP_LEVEL_RESOURCES_CONFIG_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.StateSetMetricCompression;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpExtension;
import org.mockito.MockedStatic;

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

	@BeforeAll
	static void setUp() {
		TestHelper.configureGlobalLogger();
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testGetProgramDataConfigDirectory() {
		// ProgramData invalid
		{
			try (
				final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class);
				final MockedStatic<ResourceHelper> mockedResourceHelper = mockStatic(ResourceHelper.class)
			) {
				mockedConfigHelper.when(ConfigHelper::getDefaultConfigDirectoryPath).thenCallRealMethod();
				mockedConfigHelper.when(ConfigHelper::getProgramDataPath).thenReturn(Optional.empty());
				mockedConfigHelper.when(ConfigHelper::getProgramDataConfigDirectory).thenCallRealMethod();
				mockedConfigHelper.when(() -> ConfigHelper.getSubPath(anyString())).thenCallRealMethod();
				mockedConfigHelper.when(ConfigHelper::getSourceDirectory).thenCallRealMethod();

				mockedResourceHelper
					.when(() -> ResourceHelper.findSourceDirectory(ConfigHelper.class))
					.thenAnswer(invocation -> tempDir.resolve("metricshub/app/jar").toFile());

				final Path configDirOnWindows = ConfigHelper.getDefaultConfigDirectoryPath();

				final String expectedPath = "metricshub\\app\\..\\config";

				assertNotNull(configDirOnWindows, "Config directory path should not be null");
				assertTrue(
					() -> configDirOnWindows.endsWith("metricshub\\app\\..\\config"),
					String.format("Found path %s. Expected path ends with %s.", configDirOnWindows.toString(), expectedPath)
				);
			}
		}

		// ProgramData valid
		{
			try (final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class)) {
				mockedConfigHelper.when(ConfigHelper::getDefaultConfigDirectoryPath).thenCallRealMethod();
				mockedConfigHelper.when(ConfigHelper::getProgramDataPath).thenReturn(Optional.of(tempDir.toString()));
				mockedConfigHelper.when(() -> ConfigHelper.createDirectories(any(Path.class))).thenCallRealMethod();
				mockedConfigHelper.when(ConfigHelper::getProgramDataConfigDirectory).thenCallRealMethod();

				final Path configDirectoryOnWindows = ConfigHelper.getDefaultConfigDirectoryPath();

				final String expectedPath = "metricshub\\config\\";

				assertNotNull(configDirectoryOnWindows, "Config directory path should not be null");
				assertTrue(
					() -> configDirectoryOnWindows.endsWith(expectedPath),
					String.format("Found path %s. Expected path ends with %s.", configDirectoryOnWindows.toString(), expectedPath)
				);
			}
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testFindUserGroup_onWindows() {
		String group = ConfigHelper.findUserGroup();

		// Assert we got a group name
		assertNotNull(group, "The Users group should be found on Windows");
		assertFalse(group.isBlank(), "The Users group name should not be blank");
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testGenerateDefaultConfigFileWithWritePermissions() throws IOException {
		try (final MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class)) {
			// Build a config directory
			final Path configDir = Files.createDirectories(tempDir.resolve("metricshub\\config").toAbsolutePath());

			// Create the example file
			final Path examplePath = Files.createDirectories(configDir.resolve("example")).resolve(CONFIG_EXAMPLE_FILENAME);
			Files.copy(
				Path.of("src", "test", "resources", "config", "metricshub", DEFAULT_CONFIG_FILENAME),
				examplePath,
				StandardCopyOption.REPLACE_EXISTING
			);

			// Generating default config should be a real call
			mockedConfigHelper
				.when(() -> ConfigHelper.generateDefaultConfigurationFileIfEmptyDir(configDir))
				.thenCallRealMethod();

			// Setting user permissions should be a real call
			mockedConfigHelper.when(() -> ConfigHelper.setUserPermissionsOnWindows(configDir)).thenCallRealMethod();
			mockedConfigHelper.when(() -> ConfigHelper.setUserPermissions(any(Path.class))).thenCallRealMethod();

			// Mock getSubPath as it will try to retrieve the example file deployed in production environment
			mockedConfigHelper.when(() -> ConfigHelper.getSubPath(anyString())).thenReturn(examplePath);

			// Mock getSourceDirectory as it will try to retrieve the example file deployed in production environment
			ConfigHelper.generateDefaultConfigurationFileIfEmptyDir(configDir);

			final File file = configDir.resolve(DEFAULT_CONFIG_FILENAME).toFile();

			assertTrue(file.exists(), "File should exist");

			ConfigHelper.setUserPermissionsOnWindows(configDir);

			assertTrue(file.canWrite(), "File should be writable");
		}
	}

	@Test
	void testBuildTelemetryManagers() throws IOException {
		final Path configDirectory = ConfigHelper.findConfigDirectory("src/test/resources/config/metricshub-server1");

		final ConnectorStore connectorStore = ConnectorStoreComposer
			.builder()
			.withRawConnectorStore(new RawConnectorStore(Path.of("src/test/resources")))
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.withAdditionalConnectors(null)
			.build()
			.generateStaticConnectorStore();

		final Connector connector = new Connector();
		connector.getOrCreateConnectorIdentity().setCompiledFilename(PURE_STORAGE_REST_CONNECTOR_ID);
		connectorStore.addOne(PURE_STORAGE_REST_CONNECTOR_ID, connector);

		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = ConfigHelper.buildTelemetryManagers(
			agentConfig,
			connectorStore
		);

		final Map<String, TelemetryManager> resourceGroupTelemetryManagers = telemetryManagers.get(
			PARIS_RESOURCE_GROUP_KEY
		);
		assertNotNull(resourceGroupTelemetryManagers, "Resource group telemetry managers should not be null");
		final TelemetryManager telemetryManager = resourceGroupTelemetryManagers.get(SERVER_1_RESOURCE_GROUP_KEY);
		assertNotNull(telemetryManager, "Telemetry manager should not be null");
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig
				.getResourceGroups()
				.get(PARIS_RESOURCE_GROUP_KEY)
				.getResources()
				.get(SERVER_1_RESOURCE_GROUP_KEY)
				.getAttributes()
				.get(MetricsHubConstants.HOST_NAME),
			hostConfiguration.getHostname(),
			"Host name should match"
		);

		assertEquals(
			Set.of("+" + PURE_STORAGE_REST_CONNECTOR_ID),
			hostConfiguration.getConnectors(),
			"Connectors should match"
		);
	}

	@Test
	void testBuildTelemetryManagersWithTopLevelResources() throws IOException {
		// Find the configuration file
		final Path configDirectory = ConfigHelper.findConfigDirectory(TOP_LEVEL_RESOURCES_CONFIG_PATH);

		// Create the connector store
		final ConnectorStore connectorStore = ConnectorStoreComposer
			.builder()
			.withRawConnectorStore(new RawConnectorStore(Path.of("src/test/resources")))
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.withAdditionalConnectors(null)
			.build()
			.generateStaticConnectorStore();

		final Connector connector = new Connector();
		connector.getOrCreateConnectorIdentity().setCompiledFilename(PURE_STORAGE_REST_CONNECTOR_ID);
		connectorStore.addOne(PURE_STORAGE_REST_CONNECTOR_ID, connector);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = ConfigHelper.buildTelemetryManagers(
			agentConfig,
			connectorStore
		);

		// Check that top-level resources are added to the telemetry managers map

		assertEquals(2, telemetryManagers.size(), "There should be 2 telemetry managers");

		// Check resources under resource groups

		final Map<String, TelemetryManager> resourceGroupTelemetryManagers = telemetryManagers.get(
			PARIS_RESOURCE_GROUP_KEY
		);
		assertNotNull(resourceGroupTelemetryManagers, "Resource group telemetry managers should not be null");
		final TelemetryManager telemetryManager = resourceGroupTelemetryManagers.get(SERVER_1_RESOURCE_GROUP_KEY);
		assertNotNull(telemetryManager, "Telemetry manager should not be null");
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig
				.getResourceGroups()
				.get(PARIS_RESOURCE_GROUP_KEY)
				.getResources()
				.get(SERVER_1_RESOURCE_GROUP_KEY)
				.getAttributes()
				.get(MetricsHubConstants.HOST_NAME),
			hostConfiguration.getHostname(),
			"Host name should match"
		);

		assertEquals(
			Set.of("+" + PURE_STORAGE_REST_CONNECTOR_ID),
			hostConfiguration.getConnectors(),
			"Connectors should match"
		);

		// Check resources under agent config (top-level resources)
		final Map<String, TelemetryManager> topLevelResourcesTelemetryManagers = telemetryManagers.get(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY
		);
		assertNotNull(topLevelResourcesTelemetryManagers, "Top-level resources telemetry managers should not be null");
		final TelemetryManager topLevelTelemetryManager = topLevelResourcesTelemetryManagers.get("server-2");
		assertNotNull(topLevelTelemetryManager, "Telemetry manager should not be null");
		final HostConfiguration topLevelhostConfiguration = topLevelTelemetryManager.getHostConfiguration();
		assertEquals(
			agentConfig.getResources().get("server-2").getAttributes().get(MetricsHubConstants.HOST_NAME),
			topLevelhostConfiguration.getHostname(),
			"Host name should match"
		);
	}

	@Test
	void testEnableSelfMonitoringWithTopLevelResources() throws IOException {
		// Find the configuration directory
		final Path configDirectory = ConfigHelper.findConfigDirectory(TOP_LEVEL_RESOURCES_CONFIG_PATH);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration for top-level resources
		assertFalse(
			agentConfig.getResources().get("server-2").getEnableSelfMonitoring(),
			"Self monitoring should be disabled"
		);
	}

	@Test
	void testEnableSelfMonitoringOnlyGlobalConfiguration() throws IOException {
		// Find the configuration directory
		final Path configDirectory = ConfigHelper.findConfigDirectory(TEST_CONFIG_DIRECTORY_PATH);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertTrue(
			agentConfig.getResourceGroups().get("paris").getResources().get("server-1").getEnableSelfMonitoring(),
			"Self monitoring should be enabled"
		);
	}

	@Test
	void testEnableSelfMonitoringConfigurationOverride() throws IOException {
		// Find the configuration directory
		final Path configDirectory = ConfigHelper.findConfigDirectory(
			"src/test/resources/config/metricshub-enable-self-monitoring-override"
		);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertFalse(
			agentConfig.getResourceGroups().get("paris").getResources().get("server-1").getEnableSelfMonitoring(),
			"Self monitoring should be disabled"
		);
	}

	@Test
	void testEnableSelfMonitoringNoConfiguration() throws IOException {
		// Find the configuration directory
		final Path configDirectory = ConfigHelper.findConfigDirectory(
			"src/test/resources/config/metricshub-enable-self-monitoring-no-config"
		);

		// Create the agent configuration
		final AgentConfig agentConfig = JsonHelper.deserialize(
			AgentContext.newAgentConfigObjectMapper(extensionManager),
			Files.newInputStream(configDirectory.resolve(DEFAULT_CONFIG_FILENAME)),
			AgentConfig.class
		);

		// Normalize agent configuration
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		// Check self monitoring configuration
		assertTrue(
			agentConfig.getResourceGroups().get("paris").getResources().get("server-1").getEnableSelfMonitoring(),
			"Self monitoring should be enabled"
		);
	}

	@Test
	void testNormalizeConfiguredConnector() {
		assertDoesNotThrow(
			() -> ConfigHelper.normalizeConfiguredConnector(PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, null),
			"Should not throw exception for null configured connector"
		);
		final Connector configuredConnector = new Connector();
		ConfigHelper.normalizeConfiguredConnector(PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, configuredConnector);
		assertEquals(
			"MetricsHub-Configured-Connector-paris-resource-test-key",
			configuredConnector.getCompiledFilename(),
			"Configured connector filename should match"
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
			assertDoesNotThrow(
				() -> ConfigHelper.addConfiguredConnector(connectorStore, null),
				"Should not throw exception for null configured connector"
			);

			// Ensure that the ConnectorStore size has remained unchanged
			assertEquals(initialSize, connectorStore.getStore().size(), "Store size should remain unchanged");
		}

		// Test Case 2: Existing configured connector
		{
			// Create a new Connector and configure it
			final Connector configuredConnector = new Connector();
			ConfigHelper.normalizeConfiguredConnector(PARIS_RESOURCE_GROUP_KEY, RESOURCE_KEY, configuredConnector);

			// Attempt to add the configured connector
			ConfigHelper.addConfiguredConnector(connectorStore, configuredConnector);

			// Verify that the configured connector is now in the ConnectorStore
			assertEquals(
				configuredConnector,
				connectorStore.getStore().get("MetricsHub-Configured-Connector-paris-resource-test-key"),
				"Configured connector should be present in the store"
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

		assertEquals(
			expected,
			ConfigHelper.fetchMetricDefinitions(connectorStore, PURE_STORAGE_REST_CONNECTOR_ID),
			"Should return the expected metric definitions"
		);
		assertEquals(
			defaultMetricDefinitionMap,
			ConfigHelper.fetchMetricDefinitions(connectorStore, "other"),
			"Should return the default metric definitions"
		);
		assertEquals(
			defaultMetricDefinitionMap,
			ConfigHelper.fetchMetricDefinitions(null, null),
			"Should return the default metric definitions"
		);
		assertEquals(
			defaultMetricDefinitionMap,
			ConfigHelper.fetchMetricDefinitions(null, PURE_STORAGE_REST_CONNECTOR_ID),
			"Should return the default metric definitions"
		);
		assertEquals(
			defaultMetricDefinitionMap,
			ConfigHelper.fetchMetricDefinitions(connectorStore, null),
			"Should return the default metric definitions"
		);
	}

	@Test
	void testCalculateDirectoryMD5ChecksumFixedContent() {
		final Path dir = Path.of("src", "test", "resources", "md5Checksum");
		final Predicate<Path> filesFilter = path -> path.toString().endsWith(".txt");
		final String checksum1 = ConfigHelper.calculateDirectoryMD5ChecksumSafe(dir, filesFilter);
		final String checksum2 = ConfigHelper.calculateDirectoryMD5ChecksumSafe(dir, filesFilter);
		assertNotNull(checksum1, "Checksum 1 should be present");
		assertNotNull(checksum2, "Checksum 2 should be present");
		assertEquals(checksum1, checksum2, "Checksums should match for the same directory content");
	}

	@Test
	void testCalculateDirectoryMD5ChecksumEmptyDirectory(@TempDir Path emptyDir) {
		final String checksum1 = ConfigHelper.calculateDirectoryMD5ChecksumSafe(emptyDir, path -> true);
		assertNotNull(checksum1, "Checksum 1 should not be null");
		assertFalse(checksum1.isEmpty(), "Checksum 1 should not be empty string");

		final String checksum2 = ConfigHelper.calculateDirectoryMD5ChecksumSafe(emptyDir, path -> true);
		assertNotNull(checksum2, "Checksum 2 should not be null");
		assertFalse(checksum2.isEmpty(), "Checksum 2 should not be empty string");
		assertEquals(checksum1, checksum2, "Checksums should match for the same empty directory");
	}

	@Test
	void testIsSuppressZerosCompressionShouldReturnTrueForMatchingValue() {
		assertTrue(
			ConfigHelper.isSuppressZerosCompression(StateSetMetricCompression.SUPPRESS_ZEROS),
			"Should return true for suppress_zeros"
		);
	}

	@Test
	void testIsSuppressZerosCompressionShouldReturnFalseForNonMatchingValue() {
		assertFalse(
			ConfigHelper.isSuppressZerosCompression(StateSetMetricCompression.NONE),
			"Should return false for non-matching value"
		);
	}
}
