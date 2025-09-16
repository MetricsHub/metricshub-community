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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.monitor.AbstractMonitorJob;
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

	@Test
	void testMergeMetricsDefinitionsOverridesStoreConnector() throws IOException {
		// given
		final ConnectorStore store = new ConnectorStore();
		final Connector storeConnector = new Connector();
		storeConnector
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-version").unit("{Counter}").build());
		store.addOne("TestConnector", storeConnector);

		final Connector configuredConnector = new Connector();
		final AbstractMonitorJob monitorJob = mock(AbstractMonitorJob.class);
		when(monitorJob.getMetrics())
			.thenReturn(
				Map.of("cpu.usage", MetricDefinition.builder().description("monitor-version").unit("{percent}").build())
			);
		configuredConnector.getMonitors().put("job1", monitorJob);

		final ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setConnector(configuredConnector);
		resourceConfig.setConnectors(Set.of("TestConnector"));

		final AgentConfig agentConfig = new AgentConfig();

		final ResourceGroupConfig resourceGroupConfig = new ResourceGroupConfig();
		resourceGroupConfig.setResources(Map.of("res1", resourceConfig));

		agentConfig.getResourceGroups().put("group1", resourceGroupConfig);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then
		assertEquals(
			"monitor-version",
			store.getStore().get("TestConnector").getMetrics().get("cpu.usage").getDescription()
		);
		assertEquals("{percent}", store.getStore().get("TestConnector").getMetrics().get("cpu.usage").getUnit());
	}

	@Test
	void testMergeMetricsDefinitionsAddsMissingMetricFromMonitor() throws IOException {
		// given
		final ConnectorStore store = new ConnectorStore();
		final Connector storeConnector = new Connector();
		// storeConnector has NO metrics defined
		store.addOne("TestConnector", storeConnector);

		final Connector configuredConnector = new Connector();
		final AbstractMonitorJob monitorJob = mock(AbstractMonitorJob.class);
		when(monitorJob.getMetrics())
			.thenReturn(
				Map.of("memory.usage", MetricDefinition.builder().description("monitor-only").unit("{bytes}").build())
			);
		configuredConnector.getMonitors().put("job1", monitorJob);

		final ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setConnector(configuredConnector);
		resourceConfig.setConnectors(Set.of("TestConnector"));

		final AgentConfig agentConfig = new AgentConfig();

		final ResourceGroupConfig resourceGroupConfig = new ResourceGroupConfig();
		resourceGroupConfig.setResources(Map.of("res1", resourceConfig));

		agentConfig.getResourceGroups().put("group1", resourceGroupConfig);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then: metric from monitor should be added to storeConnector
		assertTrue(
			store.getStore().get("TestConnector").getMetrics().containsKey("memory.usage"),
			"Store connector should inherit 'memory.usage' from monitor"
		);

		assertEquals(
			"monitor-only",
			store.getStore().get("TestConnector").getMetrics().get("memory.usage").getDescription()
		);
		assertEquals("{bytes}", store.getStore().get("TestConnector").getMetrics().get("memory.usage").getUnit());
	}

	@Test
	void testMergeMetricsDefinitionsKeepsStoreMetricWhenNotInMonitor() throws IOException {
		// given
		final ConnectorStore store = new ConnectorStore();
		final Connector storeConnector = new Connector();
		// storeConnector has a metric that monitor does not define
		storeConnector
			.getMetrics()
			.put("disk.usage", MetricDefinition.builder().description("store-only").unit("{GB}").build());
		store.addOne("TestConnector", storeConnector);

		final Connector configuredConnector = new Connector();
		final AbstractMonitorJob monitorJob = mock(AbstractMonitorJob.class);
		// monitor defines NO metrics
		when(monitorJob.getMetrics()).thenReturn(Map.of());
		configuredConnector.getMonitors().put("job1", monitorJob);

		final ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setConnector(configuredConnector);
		resourceConfig.setConnectors(Set.of("TestConnector"));

		final AgentConfig agentConfig = new AgentConfig();

		final ResourceGroupConfig resourceGroupConfig = new ResourceGroupConfig();
		resourceGroupConfig.setResources(Map.of("res1", resourceConfig));

		agentConfig.getResourceGroups().put("group1", resourceGroupConfig);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then: the store metric should remain unchanged
		assertTrue(
			store.getStore().get("TestConnector").getMetrics().containsKey("disk.usage"),
			"Store connector should keep 'disk.usage' since monitor did not define it"
		);

		assertEquals("store-only", store.getStore().get("TestConnector").getMetrics().get("disk.usage").getDescription());
		assertEquals("{GB}", store.getStore().get("TestConnector").getMetrics().get("disk.usage").getUnit());
	}

	@Test
	void testMergeMetricsDefinitionsWithMultipleResourceGroupsAndResources() throws IOException {
		// given
		final ConnectorStore store = new ConnectorStore();

		// === Override case (Group1 / Resource1) ===
		final Connector storeConnectorOverride1 = new Connector();
		storeConnectorOverride1
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-version-1").unit("{Counter}").build());
		store.addOne("ConnectorOverride1", storeConnectorOverride1);

		final Connector storeConnectorOverride2 = new Connector();
		storeConnectorOverride2
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-version-2").unit("{Counter}").build());
		store.addOne("ConnectorOverride2", storeConnectorOverride2);

		final Connector configuredConnectorOverride = new Connector();
		final AbstractMonitorJob monitorJobOverride = mock(AbstractMonitorJob.class);
		when(monitorJobOverride.getMetrics())
			.thenReturn(
				Map.of("cpu.usage", MetricDefinition.builder().description("monitor-version").unit("{percent}").build())
			);
		configuredConnectorOverride.getMonitors().put("job1", monitorJobOverride);

		final ResourceConfig resourceConfigOverride = new ResourceConfig();
		resourceConfigOverride.setConnector(configuredConnectorOverride);
		resourceConfigOverride.setConnectors(Set.of("ConnectorOverride1", "ConnectorOverride2"));

		// === Add case (Group1 / Resource2) ===
		final Connector storeConnectorAdd1 = new Connector();
		store.addOne("ConnectorAdd1", storeConnectorAdd1);

		final Connector storeConnectorAdd2 = new Connector();
		store.addOne("ConnectorAdd2", storeConnectorAdd2);

		final Connector configuredConnectorAdd = new Connector();
		final AbstractMonitorJob monitorJobAdd = mock(AbstractMonitorJob.class);
		when(monitorJobAdd.getMetrics())
			.thenReturn(Map.of("memory.usage", MetricDefinition.builder().description("monitor-only").unit("{MB}").build()));
		configuredConnectorAdd.getMonitors().put("job1", monitorJobAdd);

		final ResourceConfig resourceConfigAdd = new ResourceConfig();
		resourceConfigAdd.setConnector(configuredConnectorAdd);
		resourceConfigAdd.setConnectors(Set.of("ConnectorAdd1", "ConnectorAdd2"));

		// === Keep case (Group2 / Resource1) ===
		final Connector storeConnectorKeep1 = new Connector();
		storeConnectorKeep1
			.getMetrics()
			.put("disk.usage", MetricDefinition.builder().description("store-only-1").unit("{GB}").build());
		store.addOne("ConnectorKeep1", storeConnectorKeep1);

		final Connector storeConnectorKeep2 = new Connector();
		storeConnectorKeep2
			.getMetrics()
			.put("disk.usage", MetricDefinition.builder().description("store-only-2").unit("{GB}").build());
		store.addOne("ConnectorKeep2", storeConnectorKeep2);

		final Connector configuredConnectorKeep = new Connector();
		final AbstractMonitorJob monitorJobKeep = mock(AbstractMonitorJob.class);
		when(monitorJobKeep.getMetrics()).thenReturn(Map.of()); // monitor defines nothing
		configuredConnectorKeep.getMonitors().put("job1", monitorJobKeep);

		final ResourceConfig resourceConfigKeep = new ResourceConfig();
		resourceConfigKeep.setConnector(configuredConnectorKeep);
		resourceConfigKeep.setConnectors(Set.of("ConnectorKeep1", "ConnectorKeep2"));

		// === Extra Add case (Group2 / Resource2) ===
		final Connector storeConnectorExtraAdd1 = new Connector();
		store.addOne("ConnectorExtraAdd1", storeConnectorExtraAdd1);

		final Connector storeConnectorExtraAdd2 = new Connector();
		store.addOne("ConnectorExtraAdd2", storeConnectorExtraAdd2);

		final Connector configuredConnectorExtraAdd = new Connector();
		final AbstractMonitorJob monitorJobExtraAdd = mock(AbstractMonitorJob.class);
		when(monitorJobExtraAdd.getMetrics())
			.thenReturn(Map.of("net.usage", MetricDefinition.builder().description("monitor-only").unit("{bps}").build()));
		configuredConnectorExtraAdd.getMonitors().put("job1", monitorJobExtraAdd);

		final ResourceConfig resourceConfigExtraAdd = new ResourceConfig();
		resourceConfigExtraAdd.setConnector(configuredConnectorExtraAdd);
		resourceConfigExtraAdd.setConnectors(Set.of("ConnectorExtraAdd1", "ConnectorExtraAdd2"));

		// === Assemble resource groups ===
		final ResourceGroupConfig group1 = new ResourceGroupConfig();
		group1.setResources(Map.of("resOverride", resourceConfigOverride, "resAdd", resourceConfigAdd));

		final ResourceGroupConfig group2 = new ResourceGroupConfig();
		group2.setResources(Map.of("resKeep", resourceConfigKeep, "resExtraAdd", resourceConfigExtraAdd));

		final AgentConfig agentConfig = new AgentConfig();
		agentConfig.getResourceGroups().put("group1", group1);
		agentConfig.getResourceGroups().put("group2", group2);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then
		// Override case (both store connectors updated)
		assertEquals(
			"monitor-version",
			store.getStore().get("ConnectorOverride1").getMetrics().get("cpu.usage").getDescription()
		);
		assertEquals("{percent}", store.getStore().get("ConnectorOverride1").getMetrics().get("cpu.usage").getUnit());
		assertEquals(
			"monitor-version",
			store.getStore().get("ConnectorOverride2").getMetrics().get("cpu.usage").getDescription()
		);
		assertEquals("{percent}", store.getStore().get("ConnectorOverride2").getMetrics().get("cpu.usage").getUnit());

		// Add case (both store connectors get new metric)
		assertTrue(store.getStore().get("ConnectorAdd1").getMetrics().containsKey("memory.usage"));
		assertEquals(
			"monitor-only",
			store.getStore().get("ConnectorAdd1").getMetrics().get("memory.usage").getDescription()
		);
		assertTrue(store.getStore().get("ConnectorAdd2").getMetrics().containsKey("memory.usage"));
		assertEquals(
			"monitor-only",
			store.getStore().get("ConnectorAdd2").getMetrics().get("memory.usage").getDescription()
		);

		// Keep case (both store connectors unchanged)
		assertEquals(
			"store-only-1",
			store.getStore().get("ConnectorKeep1").getMetrics().get("disk.usage").getDescription()
		);
		assertEquals("{GB}", store.getStore().get("ConnectorKeep1").getMetrics().get("disk.usage").getUnit());
		assertEquals(
			"store-only-2",
			store.getStore().get("ConnectorKeep2").getMetrics().get("disk.usage").getDescription()
		);
		assertEquals("{GB}", store.getStore().get("ConnectorKeep2").getMetrics().get("disk.usage").getUnit());

		// Extra Add case (both store connectors get new metric)
		assertTrue(store.getStore().get("ConnectorExtraAdd1").getMetrics().containsKey("net.usage"));
		assertEquals(
			"monitor-only",
			store.getStore().get("ConnectorExtraAdd1").getMetrics().get("net.usage").getDescription()
		);
		assertTrue(store.getStore().get("ConnectorExtraAdd2").getMetrics().containsKey("net.usage"));
		assertEquals(
			"monitor-only",
			store.getStore().get("ConnectorExtraAdd2").getMetrics().get("net.usage").getDescription()
		);
	}

	@Test
	void testSameMonitorMetricOverridesMultipleConnectorsInSameStore() throws IOException {
		final ConnectorStore store = new ConnectorStore();

		// Store connector A
		final Connector storeConnectorA = new Connector();
		storeConnectorA
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-A").unit("{Counter}").build());
		store.addOne("ConnectorA", storeConnectorA);

		// Store connector B
		final Connector storeConnectorB = new Connector();
		storeConnectorB
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-B").unit("{Counter}").build());
		store.addOne("ConnectorB", storeConnectorB);

		// Configured connector monitor defines the override
		final Connector configuredConnector = new Connector();
		final AbstractMonitorJob monitorJob = mock(AbstractMonitorJob.class);
		when(monitorJob.getMetrics())
			.thenReturn(
				Map.of("cpu.usage", MetricDefinition.builder().description("monitor-version").unit("{percent}").build())
			);
		configuredConnector.getMonitors().put("job1", monitorJob);

		// Resource config points to both connectors from the same store
		final ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setConnector(configuredConnector);
		resourceConfig.setConnectors(Set.of("ConnectorA", "ConnectorB"));

		// Group and agent config
		final ResourceGroupConfig group = new ResourceGroupConfig();
		group.setResources(Map.of("res1", resourceConfig));

		final AgentConfig agentConfig = new AgentConfig();
		agentConfig.getResourceGroups().put("group1", group);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then: both connectors from the same store are overridden
		assertEquals("monitor-version", store.getStore().get("ConnectorA").getMetrics().get("cpu.usage").getDescription());
		assertEquals("{percent}", store.getStore().get("ConnectorA").getMetrics().get("cpu.usage").getUnit());

		assertEquals("monitor-version", store.getStore().get("ConnectorB").getMetrics().get("cpu.usage").getDescription());
		assertEquals("{percent}", store.getStore().get("ConnectorB").getMetrics().get("cpu.usage").getUnit());
	}

	@Test
	void testSameMonitorMetricOverridesMultipleStoreConnectors() throws IOException {
		final ConnectorStore store = new ConnectorStore();

		final Connector storeConnector1 = new Connector();
		storeConnector1
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-A").unit("{Counter}").build());
		store.addOne("StoreConnector1", storeConnector1);

		final Connector storeConnector2 = new Connector();
		storeConnector2
			.getMetrics()
			.put("cpu.usage", MetricDefinition.builder().description("store-B").unit("{Counter}").build());
		store.addOne("StoreConnector2", storeConnector2);

		final Connector configuredConnector = new Connector();
		final AbstractMonitorJob monitorJob = mock(AbstractMonitorJob.class);
		when(monitorJob.getMetrics())
			.thenReturn(
				Map.of("cpu.usage", MetricDefinition.builder().description("monitor-version").unit("{percent}").build())
			);
		configuredConnector.getMonitors().put("job1", monitorJob);

		final ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setConnector(configuredConnector);
		resourceConfig.setConnectors(Set.of("StoreConnector1", "StoreConnector2"));

		final AgentConfig agentConfig = new AgentConfig();
		final ResourceGroupConfig group = new ResourceGroupConfig();
		group.setResources(Map.of("res1", resourceConfig));
		agentConfig.getResourceGroups().put("group1", group);

		final AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setAgentConfig(agentConfig);

		// when
		agentContext.mergeMetricsDefinitions(store);

		// then
		assertEquals(
			"monitor-version",
			store.getStore().get("StoreConnector1").getMetrics().get("cpu.usage").getDescription()
		);
		assertEquals("{percent}", store.getStore().get("StoreConnector1").getMetrics().get("cpu.usage").getUnit());

		assertEquals(
			"monitor-version",
			store.getStore().get("StoreConnector2").getMetrics().get("cpu.usage").getDescription()
		);
		assertEquals("{percent}", store.getStore().get("StoreConnector2").getMetrics().get("cpu.usage").getUnit());
	}
}
