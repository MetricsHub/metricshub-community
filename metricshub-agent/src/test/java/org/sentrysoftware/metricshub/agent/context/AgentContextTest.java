package org.sentrysoftware.metricshub.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sentrysoftware.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.GRAFANA_DB_STATE_METRIC;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.GRAFANA_HEALTH_SOURCE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.GRAFANA_HEALTH_SOURCE_REF;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.GRAFANA_MONITOR_JOB_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.GRAFANA_SERVICE_RESOURCE_CONFIG_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.HTTP_ACCEPT_HEADER;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.HTTP_KEY_TYPE;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.HTTP_SERVICE_URL;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.ID_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_PARIS_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_PARIS_SITE_VALUE;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SERVER_1_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SERVICE_VERSION_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SITE_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.TEST_CONFIG_FILE_PATH;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.agent.config.AgentConfig;
import org.sentrysoftware.metricshub.agent.config.ConnectorVariables;
import org.sentrysoftware.metricshub.agent.config.ResourceConfig;
import org.sentrysoftware.metricshub.agent.config.ResourceGroupConfig;
import org.sentrysoftware.metricshub.agent.helper.OtelSdkConfigConstants;
import org.sentrysoftware.metricshub.engine.common.helpers.MapHelper;
import org.sentrysoftware.metricshub.engine.connector.model.common.HttpMethod;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.Mapping;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.Simple;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

class AgentContextTest {

	@Test
	void testInitialize() throws IOException {
		final AgentContext agentContext = new AgentContext(TEST_CONFIG_FILE_PATH);

		assertNotNull(agentContext.getAgentInfo());
		assertNotNull(agentContext.getConfigFile());
		assertNotNull(agentContext.getPid());

		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig);

		final Map<String, ResourceGroupConfig> resourceGroupsConfig = agentConfig.getResourceGroups();
		assertNotNull(resourceGroupsConfig);
		final ResourceGroupConfig resourceGroupConfig = resourceGroupsConfig.get(SENTRY_PARIS_RESOURCE_GROUP_KEY);
		assertNotNull(resourceGroupConfig);
		final Map<String, ResourceConfig> resourcesConfigInTheGroup = resourceGroupConfig.getResources();
		assertNotNull(resourcesConfigInTheGroup);
		assertNotNull(resourcesConfigInTheGroup.get(SERVER_1_RESOURCE_GROUP_KEY));
		final ResourceConfig grafanaServiceResourceConfig = resourcesConfigInTheGroup.get(
			GRAFANA_SERVICE_RESOURCE_CONFIG_KEY
		);
		assertNotNull(grafanaServiceResourceConfig);
		final Map<String, String> attributesConfig = grafanaServiceResourceConfig.getAttributes();
		assertNotNull(attributesConfig);
		assertEquals(SENTRY_PARIS_SITE_VALUE, attributesConfig.get(SITE_ATTRIBUTE_KEY));

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

		final SimpleMonitorJob simpleMonitorJobExpected = SimpleMonitorJob.builder().simple(simple).build();
		final Map<String, SimpleMonitorJob> expectedMonitors = Map.of(GRAFANA_MONITOR_JOB_KEY, simpleMonitorJobExpected);
		assertEquals(expectedMonitors, grafanaServiceResourceConfig.getMonitors());

		// Multi-hosts checks
		final ResourceConfig server2ResourceConfig = resourcesConfigInTheGroup.get("snmp-resources-server-2");
		assertNotNull(server2ResourceConfig);
		assertEquals("server-2", server2ResourceConfig.getAttributes().get(HOST_NAME));
		final ResourceConfig server3ResourceConfig = resourcesConfigInTheGroup.get("snmp-resources-server-3");
		assertEquals("server-3", server3ResourceConfig.getAttributes().get(HOST_NAME));
		assertNotNull(server3ResourceConfig);

		// Check the TelemetryManager map is correctly created
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		final Map<String, TelemetryManager> sentryParisTelemetryManagers = telemetryManagers.get(
			SENTRY_PARIS_RESOURCE_GROUP_KEY
		);
		assertEquals(4, sentryParisTelemetryManagers.size());

		// Check the OpenTelemetry SDK configuration is correctly created
		final Map<String, String> expectedOtelSdkConfiguration = new HashMap<>();
		expectedOtelSdkConfiguration.putAll(OtelSdkConfigConstants.DEFAULT_CONFIGURATION);
		expectedOtelSdkConfiguration.put(
			OtelSdkConfigConstants.OTEL_METRIC_EXPORT_INTERVAL,
			OtelSdkConfigConstants.DEFAULT_METRICS_EXPORT_INTERVAL
		);

		final Map<String, String> otelSdkConfiguration = agentContext.getOtelSdkConfiguration();

		assertTrue(
			MapHelper.areEqual(expectedOtelSdkConfiguration, agentContext.getOtelSdkConfiguration()),
			() -> String.format("expected %s but was: %s", expectedOtelSdkConfiguration, otelSdkConfiguration)
		);

		// Make sure the engine is notified with configuredConnectorId
		assertEquals(
			"MetricsHub-Configured-Connector-sentry-paris-grafana-service",
			sentryParisTelemetryManagers
				.get(GRAFANA_SERVICE_RESOURCE_CONFIG_KEY)
				.getHostConfiguration()
				.getConfiguredConnectorId()
		);
	}

	@Test
	void testInitializeWithTopLevelResources() throws IOException {
		// Create the agent context using the configuration file path
		final AgentContext agentContext = new AgentContext(
			"src/test/resources/config/top-level-resource-agent-context-test.yaml"
		);

		// Check AgentContext fields
		assertNotNull(agentContext.getAgentInfo());
		assertNotNull(agentContext.getConfigFile());
		assertNotNull(agentContext.getPid());

		// Verify that the agent configuration is not null
		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig);

		// Check whether top-level resources are included in the telemetry managers
		final Map<String, Map<String, TelemetryManager>> telemetryManagers = agentContext.getTelemetryManagers();
		assertEquals(2, telemetryManagers.size());

		// Check the presence of the top-level resources and the resources inside resource groups
		assertNotNull(telemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).get("server-2"));
		assertNotNull(telemetryManagers.get(SENTRY_PARIS_RESOURCE_GROUP_KEY).get("server-1"));
	}

	@Test
	void testInitializeWithConnectorVariables() throws IOException {
		final AgentContext agentContext = new AgentContext("src/test/resources/config/metricshub-connectorVariables.yaml");

		assertNotNull(agentContext.getAgentInfo());
		assertNotNull(agentContext.getConfigFile());
		assertNotNull(agentContext.getPid());

		final AgentConfig agentConfig = agentContext.getAgentConfig();
		assertNotNull(agentConfig);

		final ResourceConfig resourceConfig = agentConfig
			.getResourceGroups()
			.get(SENTRY_PARIS_RESOURCE_GROUP_KEY)
			.getResources()
			.get(SERVER_1_RESOURCE_GROUP_KEY);
		final Map<String, ConnectorVariables> variables = resourceConfig.getVariables();
		final ConnectorVariables expectedConnectorVariables = ConnectorVariables
			.builder()
			.variableValues(Map.of("restQueryPath", "/pure/api/v2"))
			.build();
		assertEquals(Map.of("PureStorageREST", expectedConnectorVariables), variables);
		// Case insensitive check
		assertEquals(expectedConnectorVariables, variables.get("purestoragerest"));
	}
}
