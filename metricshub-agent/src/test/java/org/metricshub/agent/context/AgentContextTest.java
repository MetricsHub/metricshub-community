package org.metricshub.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.GRAFANA_DB_STATE_METRIC;
import static org.metricshub.agent.helper.TestConstants.GRAFANA_HEALTH_SOURCE_KEY;
import static org.metricshub.agent.helper.TestConstants.GRAFANA_HEALTH_SOURCE_REF;
import static org.metricshub.agent.helper.TestConstants.GRAFANA_MONITOR_JOB_KEY;
import static org.metricshub.agent.helper.TestConstants.GRAFANA_SERVICE_RESOURCE_CONFIG_KEY;
import static org.metricshub.agent.helper.TestConstants.HTTP_ACCEPT_HEADER;
import static org.metricshub.agent.helper.TestConstants.HTTP_KEY_TYPE;
import static org.metricshub.agent.helper.TestConstants.HTTP_SERVICE_URL;
import static org.metricshub.agent.helper.TestConstants.ID_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.PARIS_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.PARIS_SITE_VALUE;
import static org.metricshub.agent.helper.TestConstants.SERVER_1_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.SERVICE_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.SITE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.TEST_CONFIG_DIRECTORY_PATH;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_KEYS;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.opentelemetry.OtelConfigConstants;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.configuration.YamlConfigurationProvider;
import org.metricshub.engine.common.helpers.MapHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpExtension;

class AgentContextTest {

	@BeforeAll
	static void loggingSetup() {
		TestHelper.configureGlobalLogger();
	}

	// Initialize the extension manager required by the agent context
	final ExtensionManager extensionManager = ExtensionManager
		.builder()
		.withProtocolExtensions(List.of(new SnmpExtension()))
		.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
		.build();

	@Test
	void testInitialize() throws IOException {
		final AgentContext agentContext = new AgentContext(TEST_CONFIG_DIRECTORY_PATH, extensionManager);

		assertNotNull(agentContext.getAgentInfo(), "AgentInfo should not be null");
		assertNotNull(agentContext.getConfigDirectory(), "ConfigDirectory should not be null");
		assertNotNull(agentContext.getPid(), "PID should not be null");

		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig, "AgentConfig should not be null");

		final Map<String, ResourceGroupConfig> resourceGroupsConfig = agentConfig.getResourceGroups();
		assertNotNull(resourceGroupsConfig, "ResourceGroupsConfig should not be null");
		final ResourceGroupConfig resourceGroupConfig = resourceGroupsConfig.get(PARIS_RESOURCE_GROUP_KEY);
		assertNotNull(resourceGroupConfig, "ResourceGroupConfig should not be null");
		final Map<String, ResourceConfig> resourcesConfigInTheGroup = resourceGroupConfig.getResources();
		assertNotNull(resourcesConfigInTheGroup, "ResourcesConfigInTheGroup should not be null");
		assertNotNull(resourcesConfigInTheGroup.get(SERVER_1_RESOURCE_GROUP_KEY), "ResourceConfig should not be null");
		final ResourceConfig grafanaServiceResourceConfig = resourcesConfigInTheGroup.get(
			GRAFANA_SERVICE_RESOURCE_CONFIG_KEY
		);
		assertNotNull(grafanaServiceResourceConfig, "GrafanaServiceResourceConfig should not be null");
		final Map<String, String> attributesConfig = grafanaServiceResourceConfig.getAttributes();
		assertNotNull(attributesConfig, "AttributesConfig should not be null");
		assertEquals(PARIS_SITE_VALUE, attributesConfig.get(SITE_ATTRIBUTE_KEY), "Site attribute should match");

		final Map<String, String> attributes = new LinkedHashMap<>();
		attributes.put(ID_ATTRIBUTE_KEY, "$1");
		attributes.put(SERVICE_VERSION_ATTRIBUTE_KEY, "$3");

		final Simple simple = Simple
			.builder()
			.sources(
				Map.of(
					GRAFANA_HEALTH_SOURCE_KEY,
					HttpSource
						.builder()
						.header(HTTP_ACCEPT_HEADER)
						.method(HttpMethod.GET)
						.resultContent(ResultContent.BODY)
						.url(HTTP_SERVICE_URL)
						.key(GRAFANA_HEALTH_SOURCE_REF)
						.type(HTTP_KEY_TYPE)
						.build()
				)
			)
			.mapping(
				Mapping
					.builder()
					.source(GRAFANA_HEALTH_SOURCE_REF)
					.attributes(attributes)
					.metrics(Map.of(GRAFANA_DB_STATE_METRIC, "$2"))
					.build()
			)
			.build();

		simple.setSourceDep(List.of(Set.of(GRAFANA_HEALTH_SOURCE_KEY)));

		final SimpleMonitorJob simpleMonitorJobExpected = SimpleMonitorJob
			.simpleBuilder()
			.keys(DEFAULT_KEYS)
			.simple(simple)
			.build();
		final Map<String, SimpleMonitorJob> expectedMonitors = Map.of(GRAFANA_MONITOR_JOB_KEY, simpleMonitorJobExpected);
		assertEquals(expectedMonitors, grafanaServiceResourceConfig.getMonitors(), "Monitors should match");

		// Multi-hosts checks
		final ResourceConfig server2ResourceConfig = resourcesConfigInTheGroup.get("snmp-resources-1-server-2");
		assertNotNull(server2ResourceConfig, "Server2ResourceConfig should not be null");
		assertEquals("server-2", server2ResourceConfig.getAttributes().get(HOST_NAME), "host.name should match");
		final ResourceConfig server3ResourceConfig = resourcesConfigInTheGroup.get("snmp-resources-2-server-3");
		assertEquals("server-3", server3ResourceConfig.getAttributes().get(HOST_NAME), "host.name should match");
		assertNotNull(server3ResourceConfig, "Server3ResourceConfig should not be null");

		// Check the TelemetryManager map is correctly created
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		final Map<String, TelemetryManager> parisTelemetryManagers = telemetryManagers.get(PARIS_RESOURCE_GROUP_KEY);
		assertEquals(4, parisTelemetryManagers.size(), "TelemetryManagers size should match");

		// Check the OpenTelemetry configuration is correctly created
		final Map<String, String> expectedOtelConfiguration = new HashMap<>();
		expectedOtelConfiguration.put(OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop");
		expectedOtelConfiguration.put(
			OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_POOL_SIZE,
			String.valueOf(agentConfig.getJobPoolSize())
		);

		final Map<String, String> otelConfiguration = agentContext.getOtelConfiguration();

		assertTrue(
			MapHelper.areEqual(expectedOtelConfiguration, otelConfiguration),
			() -> String.format("expected %s but was: %s", expectedOtelConfiguration, otelConfiguration)
		);

		// Make sure the engine is notified with configuredConnectorId
		assertEquals(
			"MetricsHub-Configured-Connector-paris-grafana-service",
			parisTelemetryManagers.get(GRAFANA_SERVICE_RESOURCE_CONFIG_KEY).getHostConfiguration().getConfiguredConnectorId(),
			"ConfiguredConnectorId should match"
		);
	}

	@Test
	void testInitializeWithTopLevelResources() throws IOException {
		// Create the agent context using the configuration file path
		final AgentContext agentContext = new AgentContext(
			"src/test/resources/config/top-level-resource-agent-context-test",
			extensionManager
		);

		// Check AgentContext fields
		assertNotNull(agentContext.getAgentInfo(), "AgentInfo should not be null");
		assertNotNull(agentContext.getConfigDirectory(), "ConfigDirectory should not be null");
		assertNotNull(agentContext.getPid(), "PID should not be null");

		// Verify that the agent configuration is not null
		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig, "AgentConfig should not be null");

		// Check whether top-level resources are included in the telemetry managers
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		assertEquals(2, telemetryManagers.size(), "TelemetryManagers size should be 2");

		// Check the presence of the top-level resources and the resources inside resource groups
		assertNotNull(
			telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).get("server-2"),
			"server-2 should not be null"
		);
		assertNotNull(telemetryManagers.get(PARIS_RESOURCE_GROUP_KEY).get("server-1"), "server-1 should not be null");
	}

	@Test
	void testInitializeWithConnectorVariables() throws IOException {
		final AgentContext agentContext = new AgentContext(
			"src/test/resources/config/metricshub-connectorVariables",
			extensionManager
		);

		assertNotNull(agentContext.getAgentInfo(), "AgentInfo should not be null");
		assertNotNull(agentContext.getConfigDirectory(), "ConfigDirectory should not be null");
		assertNotNull(agentContext.getPid(), "PID should not be null");

		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig, "AgentConfig should not be null");

		final ResourceConfig resourceConfig = agentConfig
			.getResourceGroups()
			.get(PARIS_RESOURCE_GROUP_KEY)
			.getResources()
			.get(SERVER_1_RESOURCE_GROUP_KEY);

		final Map<String, AdditionalConnector> additionalConnectors = resourceConfig.getAdditionalConnectors();
		// Check the number of additional connectors
		assertEquals(5, additionalConnectors.size(), "AdditionalConnectors size should be 5");

		final Map<String, ConnectorVariables> variables = resourceConfig.getConnectorVariables();

		// Check the number of configured ConnectorVariables
		assertEquals(4, variables.size(), "ConnectorVariables size should be 4");

		AdditionalConnector pureStorageREST = AdditionalConnector
			.builder()
			.uses("PureStorageREST")
			.variables(Map.of("restQueryPath", "/pure/api/v2"))
			.force(false)
			.build();
		assertEquals(pureStorageREST, additionalConnectors.get("PureStorageREST"), "PureStorageREST should match");
		AdditionalConnector windows = AdditionalConnector
			.builder()
			.uses("Windows")
			.variables(Map.of("osType", "windows"))
			.build();
		AdditionalConnector linux = AdditionalConnector.builder().uses("Linux").variables(null).build();
		AdditionalConnector ipmiTool = AdditionalConnector.builder().uses("IpmiTool").variables(Map.of()).build();
		AdditionalConnector linuxProcess = AdditionalConnector.builder().uses("LinuxProcess").build();
		final Map<String, AdditionalConnector> expectedAdditionalConnectors = new LinkedHashMap<>();

		expectedAdditionalConnectors.put("PureStorageREST", pureStorageREST);
		expectedAdditionalConnectors.put("Windows", windows);
		expectedAdditionalConnectors.put("Linux", linux);
		expectedAdditionalConnectors.put("IpmiTool", ipmiTool);
		expectedAdditionalConnectors.put("LinuxProcess", linuxProcess);
		assertEquals(expectedAdditionalConnectors, additionalConnectors, "AdditionalConnectors should match");
	}

	@Test
	void testInitializeWithEnvironmentVariables() throws IOException {
		final AgentContext agentContext = new AgentContext(
			"src/test/resources/config/metricshub-environmentVariables",
			extensionManager
		);

		assertNotEquals(
			"${env::JAVA_HOME}",
			agentContext.getAgentConfig().getOutputDirectory(),
			"Output directory should not be the same"
		);
	}
}
