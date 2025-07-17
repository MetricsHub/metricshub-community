package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.METRICSHUB_RESOURCE_GROUP_KEY_FORMAT;
import static org.metricshub.agent.service.scheduling.ResourceScheduling.METRICSHUB_RESOURCE_KEY_FORMAT;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.AlertingSystemConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.configuration.YamlConfigurationProvider;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;

class ReloadServiceTest {

	private static String RESOURCES_PATH = "src/test/resources/service/reload/%s";

	private ReloadService createReloadServiceWithConfigs(AgentConfig oldConfig, AgentConfig newConfig)
		throws IOException {
		TestHelper.configureGlobalLogger();
		final AgentContext oldContext = new AgentContext(
			RESOURCES_PATH.formatted("default-config"),
			new ExtensionManager()
		);
		final AgentContext newContext = new AgentContext(
			RESOURCES_PATH.formatted("default-config"),
			new ExtensionManager()
		);
		return ReloadService.builder().withRunningAgentContext(oldContext).withReloadedAgentContext(newContext).build();
	}

	private void shutdownAgents(final ReloadService reloadService) {
		reloadService.getRunningAgentContext().getTaskSchedulingService().stop();
		reloadService.getReloadedAgentContext().getTaskSchedulingService().stop();
	}

	@Test
	void testGlobalConfigChanged_same_configuration() throws IOException {
		AgentConfig config = AgentConfig.builder().build();
		ReloadService reloadService = createReloadServiceWithConfigs(config, config);
		assertFalse(reloadService.globalConfigurationHasChanged(config, config));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_jobPoolSize_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().jobPoolSize(42).build();
		AgentConfig newConfig = AgentConfig.builder().jobPoolSize(43).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_loggerLevel_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().loggerLevel("INFO").build();
		AgentConfig newConfig = AgentConfig.builder().loggerLevel("DEBUG").build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_outputDirectory_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().outputDirectory("/tmp/old").build();
		AgentConfig newConfig = AgentConfig.builder().outputDirectory("/tmp/new").build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_collectPeriod_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().collectPeriod(60L).build();
		AgentConfig newConfig = AgentConfig.builder().collectPeriod(120L).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_discoveryCycle_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().discoveryCycle(15).build();
		AgentConfig newConfig = AgentConfig.builder().discoveryCycle(30).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_alertingSystemConfig_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig
			.builder()
			.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("T1").build())
			.build();
		AgentConfig newConfig = AgentConfig
			.builder()
			.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("T2").build())
			.build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_sequential_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().sequential(true).build();
		AgentConfig newConfig = AgentConfig.builder().sequential(false).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_enableSelfMonitoring_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().enableSelfMonitoring(true).build();
		AgentConfig newConfig = AgentConfig.builder().enableSelfMonitoring(false).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_resolveHostnameToFqdn_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().resolveHostnameToFqdn(true).build();
		AgentConfig newConfig = AgentConfig.builder().resolveHostnameToFqdn(false).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_monitorFilters_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().monitorFilters(Set.of("filterA")).build();
		AgentConfig newConfig = AgentConfig.builder().monitorFilters(Set.of("filterB")).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_jobTimeout_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().jobTimeout(30L).build();
		AgentConfig newConfig = AgentConfig.builder().jobTimeout(60L).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_otelCollector_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().otelCollector(OtelCollectorConfig.builder().build()).build();
		AgentConfig newConfig = AgentConfig
			.builder()
			.otelCollector(OtelCollectorConfig.builder().disabled(true).build())
			.build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_otelConfig_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().otelConfig(Map.of("key1", "value1")).build();
		AgentConfig newConfig = AgentConfig.builder().otelConfig(Map.of("key1", "value2")).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_attributes_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().attributes(Map.of("env", "prod")).build();
		AgentConfig newConfig = AgentConfig.builder().attributes(Map.of("env", "dev")).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_metrics_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().metrics(Map.of("cpu", 0.1)).build();
		AgentConfig newConfig = AgentConfig.builder().metrics(Map.of("cpu", 0.9)).build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_stateSetCompression_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().stateSetCompression("SUPPRESS_ZEROS").build();
		AgentConfig newConfig = AgentConfig.builder().stateSetCompression("NONE").build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	@Test
	void testGlobalConfigChanged_when_patchDirectory_differs() throws IOException {
		AgentConfig oldConfig = AgentConfig.builder().patchDirectory("/opt/patches").build();
		AgentConfig newConfig = AgentConfig.builder().patchDirectory("/opt/other").build();
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);
		assertTrue(reloadService.globalConfigurationHasChanged(oldConfig, newConfig));
		shutdownAgents(reloadService);
	}

	AgentContext loadAgentContext(final String fileName) throws IOException {
		return new AgentContext(
			RESOURCES_PATH.formatted(fileName),
			ExtensionManager
				.builder()
				.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
				.withProtocolExtensions(List.of(new HttpExtension()))
				.build()
		);
	}

	@Test
	void testCompareResources_sameResources() throws IOException {
		final AgentContext context = loadAgentContext("default-config");
		final AgentConfig config = context.getAgentConfig();

		assertNotNull(context);
		assertNotNull(config);

		assertNotNull(context);
		assertNotNull(config);

		ReloadService reloadService = createReloadServiceWithConfigs(config, config);
		reloadService.compareResources(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, config.getResources(), config.getResources());

		assertEquals(config.getResources(), config.getResources());
		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_newResource() throws IOException {
		TestHelper.configureGlobalLogger();

		// Load default context
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, ResourceConfig> defaultTopLevelResources = defaultConfig.getResources();
		final Map<String, ResourceConfig> defaultParisResources = defaultConfig
			.getResourceGroups()
			.get("paris")
			.getResources();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();

		// Load new context
		final AgentContext newContext = loadAgentContext("additional-host-config");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final Map<String, ResourceConfig> newTopLevelResources = newConfig.getResources();

		// Perform reload
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newTopLevelResources
		);
		reloadService.compareResourceGroups(defaultConfig.getResourceGroups(), newConfig.getResourceGroups());

		// Verify new top-level server has been added
		assertEquals(3, defaultTopLevelResources.size());
		assertTrue(defaultTopLevelResources.containsKey("top-level-server-3"));
		assertTrue(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-3"));
		assertTrue(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-3"))
		);

		// Verify new paris server has been added
		assertEquals(3, defaultParisResources.size());
		assertTrue(defaultParisResources.containsKey("paris-server-3"));
		assertTrue(defaultTelemetryManagers.get("paris").containsKey("paris-server-3"));
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-3")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_removedResource() throws IOException {
		TestHelper.configureGlobalLogger();

		// Load default context (with 2 top-level and 2 paris resources)
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, ResourceConfig> defaultTopLevelResources = defaultConfig.getResources();
		final Map<String, ResourceConfig> defaultParisResources = defaultConfig
			.getResourceGroups()
			.get("paris")
			.getResources();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();

		// Load new context (with top-level-server-1 and paris-server-1 removed)
		final AgentContext newContext = loadAgentContext("removed-host-config");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final Map<String, ResourceConfig> newTopLevelResources = newConfig.getResources();

		// Perform reload
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newTopLevelResources
		);
		reloadService.compareResourceGroups(defaultConfig.getResourceGroups(), newConfig.getResourceGroups());

		// Verify top-level-server-1 has been removed
		assertEquals(1, defaultTopLevelResources.size());
		assertFalse(defaultTopLevelResources.containsKey("top-level-server-2"));
		assertFalse(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-2"));
		assertFalse(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-2"))
		);

		// Verify paris-server-1 has been removed
		assertEquals(1, defaultParisResources.size());
		assertFalse(defaultParisResources.containsKey("paris-server-2"));
		assertFalse(defaultTelemetryManagers.get("paris").containsKey("paris-server-2"));
		assertFalse(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-2")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_updatedResource() throws IOException {
		TestHelper.configureGlobalLogger();

		// Load default context (2 top-level and 2 paris resources)
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, ResourceConfig> defaultTopLevelResources = defaultConfig.getResources();
		final Map<String, ResourceConfig> defaultParisResources = defaultConfig
			.getResourceGroups()
			.get("paris")
			.getResources();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();

		// Load new context with updates
		final AgentContext newContext = loadAgentContext("updated-host-config");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final Map<String, ResourceConfig> newTopLevelResources = newConfig.getResources();

		// Get password BEFORE update
		final HttpConfiguration parisServer2HttpConfigBefore = (HttpConfiguration) defaultParisResources
			.get("paris-server-2")
			.getProtocols()
			.get("http");

		assertEquals("password", String.valueOf(parisServer2HttpConfigBefore.getPassword()));

		// Get attribute BEFORE update
		assertEquals("storage", defaultTopLevelResources.get("top-level-server-2").getAttributes().get("host.type"));

		// Reload service
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newTopLevelResources
		);
		reloadService.compareResourceGroups(defaultConfig.getResourceGroups(), newConfig.getResourceGroups());

		// Get updated attributes/configurations after reload
		final Map<String, String> updatedAttributes = defaultTopLevelResources.get("top-level-server-2").getAttributes();
		final HttpConfiguration parisServer2HttpConfigAfter = (HttpConfiguration) defaultParisResources
			.get("paris-server-2")
			.getProtocols()
			.get("http");

		// Assert updates applied
		assertEquals(2, defaultTopLevelResources.size());
		assertEquals("windows", updatedAttributes.get("host.type"));
		assertTrue(defaultTopLevelResources.containsKey("top-level-server-2"));
		assertTrue(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-2"));
		assertTrue(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-2"))
		);

		assertEquals(2, defaultParisResources.size());
		assertEquals("password-updated", String.valueOf(parisServer2HttpConfigAfter.getPassword()));
		assertTrue(defaultParisResources.containsKey("paris-server-2"));
		assertTrue(defaultTelemetryManagers.get("paris").containsKey("paris-server-2"));
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-2")));

		shutdownAgents(reloadService);
	}

	private String getScheduleResourceName(final String resourceGroupKey, final String resourceKey) {
		return String.format(METRICSHUB_RESOURCE_KEY_FORMAT, resourceGroupKey, resourceKey);
	}

	@Test
	void testCompareResourceGroups_newGroupAdded() throws IOException {
		TestHelper.configureGlobalLogger();

		// Load initial config without the 'lyon' group
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();

		assertFalse(defaultConfig.getResourceGroups().containsKey("lyon"));

		// Load new config that includes 'lyon' group
		final AgentContext newContext = loadAgentContext("additional-group");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final ResourceGroupConfig newLyonGroup = newConfig.getResourceGroups().get("lyon");

		assertNotNull(newLyonGroup);
		assertTrue(newLyonGroup.getResources().containsKey("lyon-server-1"));

		// Compare groups
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		reloadService.compareResourceGroups(defaultConfig.getResourceGroups(), newConfig.getResourceGroups());

		// Check group added
		assertTrue(defaultConfig.getResourceGroups().containsKey("lyon"), "Lyon group should be added");
		assertTrue(defaultTelemetryManagers.containsKey("lyon"), "Telemetry for 'lyon' group should exist");

		// Check schedule for group and its resource
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("lyon", "lyon-server-1")));
		assertTrue(defaultSchedules.containsKey(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted("lyon")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResourceGroups_existingGroupRemoved() throws IOException {
		TestHelper.configureGlobalLogger();

		// Load full config with group 'paris'
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, ResourceConfig> parisResources = defaultConfig.getResourceGroups().get("paris").getResources();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();

		assertTrue(defaultConfig.getResourceGroups().containsKey("paris"));

		// Load new config with 'paris' group removed
		final AgentContext newContext = loadAgentContext("removed-group");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();

		assertFalse(newConfig.getResourceGroups().containsKey("paris"));

		// Compare
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		reloadService.compareResourceGroups(defaultConfig.getResourceGroups(), newConfig.getResourceGroups());

		// Assert group is removed
		assertFalse(defaultContext.getAgentConfig().getResourceGroups().containsKey("paris"));
		assertFalse(defaultTelemetryManagers.containsKey("paris"));
		assertNull(defaultSchedules.get(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted("paris")));

		parisResources
			.keySet()
			.forEach(id ->
				assertNull(defaultContext.getTaskSchedulingService().getSchedules().get(getScheduleResourceName("paris", id)))
			);

		shutdownAgents(reloadService);
	}
}
