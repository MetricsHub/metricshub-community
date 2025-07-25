package org.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.opentelemetry.OtelConfigConstants.OTEL_EXPORTER_OTLP_METRICS_PROTOCOL;
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.METRICSHUB_RESOURCE_GROUP_KEY_FORMAT;
import static org.metricshub.agent.service.scheduling.ResourceScheduling.METRICSHUB_RESOURCE_KEY_FORMAT;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.AlertingSystemConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.opentelemetry.client.NoopClient;
import org.metricshub.configuration.YamlConfigurationProvider;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;

class ReloadServiceTest {

	private static String RESOURCES_PATH = "src/test/resources/service/reload/%s";

	private ExtensionManager extensionManager = ExtensionManager
		.builder()
		.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
		.build();

	private ReloadService createReloadServiceWithConfigs(AgentConfig oldConfig, AgentConfig newConfig)
		throws IOException {
		TestHelper.configureGlobalLogger();
		final AgentContext oldContext = new AgentContext(RESOURCES_PATH.formatted("default-config"), extensionManager);
		final AgentContext newContext = new AgentContext(RESOURCES_PATH.formatted("default-config"), extensionManager);

		oldContext.setAgentConfig(oldConfig);
		newContext.setAgentConfig(newConfig);
		return ReloadService.builder().withRunningAgentContext(oldContext).withReloadedAgentContext(newContext).build();
	}

	private void shutdownAgents(final ReloadService reloadService) throws InterruptedException {
		final TaskSchedulingService defaultTaskSchedulingService = reloadService
			.getRunningAgentContext()
			.getTaskSchedulingService();
		final TaskSchedulingService newTaskSchedulingService = reloadService
			.getReloadedAgentContext()
			.getTaskSchedulingService();

		defaultTaskSchedulingService.getMetricsExporter().shutdown();
		newTaskSchedulingService.getMetricsExporter().shutdown();
	}

	AgentContext loadAgentContext(final String fileName) throws IOException {
		final AgentContext agentContext = new AgentContext(
			RESOURCES_PATH.formatted(fileName),
			ExtensionManager
				.builder()
				.withConfigurationProviderExtensions(List.of(new YamlConfigurationProvider()))
				.withProtocolExtensions(List.of(new HttpExtension()))
				.build()
		);

		agentContext.setMetricsExporter(MetricsExporter.builder().withClient(new NoopClient()).build());

		return agentContext;
	}

	private String getScheduleResourceName(final String resourceGroupKey, final String resourceKey) {
		return String.format(METRICSHUB_RESOURCE_KEY_FORMAT, resourceGroupKey, resourceKey);
	}

	@Test
	void testCompareResources_sameResources() throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();
		final AgentContext context = loadAgentContext("default-config");
		final AgentConfig config = context.getAgentConfig();

		assertNotNull(context);
		assertNotNull(config);

		assertNotNull(context);
		assertNotNull(config);

		ReloadService reloadService = createReloadServiceWithConfigs(config, config);
		assertTrue(
			reloadService
				.compareResources(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, config.getResources(), config.getResources())
				.isEmpty(),
			"No resources should be scheduled for update"
		);

		assertTrue(
			reloadService
				.compareResourceGroups(config.getResourceGroups(), config.getResourceGroups())
				.get("paris")
				.isEmpty(),
			"No resources should be scheduled for update"
		);

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_newResource_shouldScheduleCorrectly() throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();

		// Load initial context (2 top-level + 2 Paris servers)
		final AgentContext defaultContext = loadAgentContext("default-config");

		// Load new context (adds top-level-server-3 and paris-server-3)
		final AgentContext newContext = loadAgentContext("additional-host-config");

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

		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();

		// Create the ReloadService
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		// Perform comparisons
		final Map<String, ResourceConfig> scheduledTopLevelResources = reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newConfig.getResources()
		);

		final Map<String, Map<String, ResourceConfig>> scheduledGroupResources = reloadService.compareResourceGroups(
			defaultConfig.getResourceGroups(),
			newConfig.getResourceGroups()
		);

		// Combine all into one map and schedule
		final Map<String, Map<String, ResourceConfig>> resourcesToSchedule = new HashMap<>();
		resourcesToSchedule.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, scheduledTopLevelResources);
		resourcesToSchedule.putAll(scheduledGroupResources);

		reloadService.scheduleResources(resourcesToSchedule, Integer.MAX_VALUE);

		// 🔍 Check top-level-server-3 was added
		assertEquals(3, defaultTopLevelResources.size(), "Expected 3 top-level servers");
		assertTrue(defaultTopLevelResources.containsKey("top-level-server-3"));
		assertTrue(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-3"));
		assertTrue(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-3"))
		);

		// 🔍 Check paris-server-3 was added
		assertEquals(3, defaultParisResources.size(), "Expected 3 Paris servers");
		assertTrue(defaultParisResources.containsKey("paris-server-3"));
		assertTrue(defaultTelemetryManagers.get("paris").containsKey("paris-server-3"));
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-3")));

		// Stop the services and the exporters
		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_removedResource_shouldCleanupCorrectly() throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();

		// Load initial context (contains top-level-server-1,2 and paris-server-1,2)
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

		// Load new context (with top-level-server-2 and paris-server-2 removed)
		final AgentContext newContext = loadAgentContext("removed-host-config");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();

		// Prepare ReloadService
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		// Run comparisons
		final Map<String, ResourceConfig> topLevelToSchedule = reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newConfig.getResources()
		);
		final Map<String, Map<String, ResourceConfig>> groupToSchedule = reloadService.compareResourceGroups(
			defaultConfig.getResourceGroups(),
			newConfig.getResourceGroups()
		);

		// Schedule remaining resources (this also triggers cleanup of removed resources)
		final Map<String, Map<String, ResourceConfig>> allToSchedule = new HashMap<>();
		allToSchedule.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, topLevelToSchedule);
		allToSchedule.putAll(groupToSchedule);
		reloadService.scheduleResources(allToSchedule, Integer.MAX_VALUE);

		// 🔍 Verify top-level-server-2 was removed
		assertEquals(1, defaultTopLevelResources.size());
		assertFalse(defaultTopLevelResources.containsKey("top-level-server-2"));
		assertFalse(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-2"));
		assertFalse(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-2"))
		);

		// 🔍 Verify paris-server-2 was removed
		assertEquals(1, defaultParisResources.size());
		assertFalse(defaultParisResources.containsKey("paris-server-2"));
		assertFalse(defaultTelemetryManagers.get("paris").containsKey("paris-server-2"));
		assertFalse(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-2")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResources_updatedResource_shouldRescheduleAndUpdateConfigs()
		throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();

		// Load default context (with original values for resources)
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

		// Load new context with updated resource configs
		final AgentContext newContext = loadAgentContext("updated-host-config");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final Map<String, ResourceConfig> newTopLevelResources = newConfig.getResources();

		// Get configuration before update
		final HttpConfiguration parisServer2HttpConfigBefore = (HttpConfiguration) defaultParisResources
			.get("paris-server-2")
			.getProtocols()
			.get("http");

		assertEquals(
			"password",
			String.valueOf(parisServer2HttpConfigBefore.getPassword()),
			"Initial password should match"
		);

		final String hostTypeBefore = defaultTopLevelResources.get("top-level-server-2").getAttributes().get("host.type");

		assertEquals("storage", hostTypeBefore, "Initial host type should be 'storage'");

		// Prepare ReloadService
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		// Perform comparison
		final Map<String, ResourceConfig> topLevelToSchedule = reloadService.compareResources(
			TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
			defaultTopLevelResources,
			newTopLevelResources
		);

		final Map<String, Map<String, ResourceConfig>> groupToSchedule = reloadService.compareResourceGroups(
			defaultConfig.getResourceGroups(),
			newConfig.getResourceGroups()
		);

		// Schedule updates (includes replacing the old configs)
		final Map<String, Map<String, ResourceConfig>> allToSchedule = new HashMap<>();
		allToSchedule.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, topLevelToSchedule);
		allToSchedule.putAll(groupToSchedule);

		reloadService.scheduleResources(allToSchedule, Integer.MAX_VALUE);

		// Verify top-level-server-2 was updated
		assertEquals(2, defaultTopLevelResources.size());
		assertTrue(defaultTopLevelResources.containsKey("top-level-server-2"));

		final String updatedHostType = defaultTopLevelResources.get("top-level-server-2").getAttributes().get("host.type");

		assertEquals("windows", updatedHostType, "host.type should be updated to 'windows'");
		assertTrue(defaultTelemetryManagers.get(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY).containsKey("top-level-server-2"));
		assertTrue(
			defaultSchedules.containsKey(getScheduleResourceName(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, "top-level-server-2"))
		);

		// Verify paris-server-2 was updated
		assertEquals(2, defaultParisResources.size());
		assertTrue(defaultParisResources.containsKey("paris-server-2"));

		final HttpConfiguration parisServer2HttpConfigAfter = (HttpConfiguration) defaultParisResources
			.get("paris-server-2")
			.getProtocols()
			.get("http");

		assertEquals("password-updated", String.valueOf(parisServer2HttpConfigAfter.getPassword()));
		assertTrue(defaultTelemetryManagers.get("paris").containsKey("paris-server-2"));
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("paris", "paris-server-2")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResourceGroups_newGroupAdded_shouldScheduleAndRegister() throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();

		// Load initial context without the 'lyon' group
		final AgentContext defaultContext = loadAgentContext("default-config");
		defaultContext.getTaskSchedulingService().start();
		final AgentConfig defaultConfig = defaultContext.getAgentConfig();
		final Map<String, Map<String, TelemetryManager>> defaultTelemetryManagers = defaultContext
			.getTaskSchedulingService()
			.getTelemetryManagers();
		final Map<String, ScheduledFuture<?>> defaultSchedules = defaultContext.getTaskSchedulingService().getSchedules();

		assertFalse(
			defaultConfig.getResourceGroups().containsKey("lyon"),
			"Initial config should not contain 'lyon' group"
		);

		// Load new context that includes 'lyon' group with lyon-server-1
		final AgentContext newContext = loadAgentContext("additional-group");
		newContext.getTaskSchedulingService().start();
		final AgentConfig newConfig = newContext.getAgentConfig();
		final ResourceGroupConfig newLyonGroup = newConfig.getResourceGroups().get("lyon");

		assertNotNull(newLyonGroup);
		assertTrue(newLyonGroup.getResources().containsKey("lyon-server-1"), "Expected 'lyon-server-1' to exist");

		// Create ReloadService and compare groups
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		final Map<String, Map<String, ResourceConfig>> groupToSchedule = reloadService.compareResourceGroups(
			defaultConfig.getResourceGroups(),
			newConfig.getResourceGroups()
		);

		// Schedule the new group's resources
		reloadService.scheduleResources(groupToSchedule, Integer.MAX_VALUE);

		// Assert group was added to running config
		assertTrue(defaultConfig.getResourceGroups().containsKey("lyon"), "Lyon group should be added");

		// Assert telemetry for lyon group was created
		assertTrue(defaultTelemetryManagers.containsKey("lyon"), "TelemetryManager for 'lyon' group should be present");

		// Assert lyon-server-1 and the group itself are scheduled
		assertTrue(defaultSchedules.containsKey(getScheduleResourceName("lyon", "lyon-server-1")));
		assertTrue(defaultSchedules.containsKey(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted("lyon")));

		shutdownAgents(reloadService);
	}

	@Test
	void testCompareResourceGroups_existingGroupRemoved_shouldCleanupCorrectly()
		throws IOException, InterruptedException {
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

		// Prepare ReloadService
		final ReloadService reloadService = ReloadService
			.builder()
			.withRunningAgentContext(defaultContext)
			.withReloadedAgentContext(newContext)
			.build();

		// Compare groups and collect updates
		final Map<String, Map<String, ResourceConfig>> groupToSchedule = reloadService.compareResourceGroups(
			defaultConfig.getResourceGroups(),
			newConfig.getResourceGroups()
		);

		// Apply changes (this triggers resource + telemetry + schedule cleanup)
		reloadService.scheduleResources(groupToSchedule, Integer.MAX_VALUE);

		// Assert group is removed
		assertFalse(
			defaultContext.getAgentConfig().getResourceGroups().containsKey("paris"),
			"Group 'paris' should be removed"
		);
		assertFalse(defaultTelemetryManagers.containsKey("paris"), "Telemetry manager for 'paris' should be removed");
		assertNull(
			defaultSchedules.get(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT.formatted("paris")),
			"Group schedule should be removed"
		);

		parisResources
			.keySet()
			.forEach(id ->
				assertNull(
					defaultContext.getTaskSchedulingService().getSchedules().get(getScheduleResourceName("paris", id)),
					"Schedule for 'paris' resource %s should be removed".formatted(id)
				)
			);

		shutdownAgents(reloadService);
	}

	@Test
	void testResourceGroupGlobalConfigHasChanged_when_loggerLevel_differs() throws IOException, InterruptedException {
		TestHelper.configureGlobalLogger();

		ResourceGroupConfig oldGroup = ResourceGroupConfig.builder().loggerLevel("INFO").build();

		ResourceGroupConfig newGroup = ResourceGroupConfig.builder().loggerLevel("DEBUG").build();

		ReloadService reloadService = createReloadServiceWithConfigs(
			AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).build(),
			AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).build()
		);

		assertTrue(
			reloadService.resourceGroupGlobalConfigHasChanged(oldGroup, newGroup),
			"Expected change in loggerLevel to be detected"
		);

		shutdownAgents(reloadService);
	}

	@ParameterizedTest(name = "{index} ⇒ field {0} should trigger config change")
	@MethodSource("provideChangedConfigs")
	void testGlobalConfigChangedParameterized(String fieldName, AgentConfig oldConfig, AgentConfig newConfig)
		throws IOException, InterruptedException {
		ReloadService reloadService = createReloadServiceWithConfigs(oldConfig, newConfig);

		assertTrue(
			reloadService.globalConfigurationHasChanged(oldConfig, newConfig),
			"Expected change in field '%s' to be detected".formatted(fieldName)
		);

		shutdownAgents(reloadService);
	}

	private static Stream<Arguments> provideChangedConfigs() {
		return Stream.of(
			Arguments.of(
				"jobPoolSize",
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).jobPoolSize(10).build(),
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).jobPoolSize(11).build()
			),
			Arguments.of(
				"loggerLevel",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.loggerLevel("INFO")
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.loggerLevel("DEBUG")
					.build()
			),
			Arguments.of(
				"outputDirectory",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.outputDirectory("/tmp/a")
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.outputDirectory("/tmp/b")
					.build()
			),
			Arguments.of(
				"collectPeriod",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.collectPeriod(30L)
					.build(),
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).collectPeriod(60L).build()
			),
			Arguments.of(
				"discoveryCycle",
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).discoveryCycle(5).build(),
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).discoveryCycle(10).build()
			),
			Arguments.of(
				"alertingSystemConfig",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("A").build())
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("B").build())
					.build()
			),
			Arguments.of(
				"sequential",
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).sequential(true).build(),
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).sequential(false).build()
			),
			Arguments.of(
				"enableSelfMonitoring",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.enableSelfMonitoring(true)
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.enableSelfMonitoring(false)
					.build()
			),
			Arguments.of(
				"resolveHostnameToFqdn",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.resolveHostnameToFqdn(true)
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.resolveHostnameToFqdn(false)
					.build()
			),
			Arguments.of(
				"monitorFilters",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.monitorFilters(Set.of("a"))
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.monitorFilters(Set.of("b"))
					.build()
			),
			Arguments.of(
				"jobTimeout",
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).jobTimeout(10L).build(),
				AgentConfig.builder().otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop")).jobTimeout(20L).build()
			),
			Arguments.of(
				"otelCollector",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.otelCollector(OtelCollectorConfig.builder().build())
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.otelCollector(OtelCollectorConfig.builder().disabled(true).build())
					.build()
			),
			Arguments.of(
				"otelConfig",
				AgentConfig.builder().otelConfig(Map.of("x", "1")).build(),
				AgentConfig.builder().otelConfig(Map.of("x", "2")).build()
			),
			Arguments.of(
				"attributes",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.attributes(Map.of("env", "prod"))
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.attributes(Map.of("env", "dev"))
					.build()
			),
			Arguments.of(
				"metrics",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.metrics(Map.of("cpu", 0.1))
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.metrics(Map.of("cpu", 0.9))
					.build()
			),
			Arguments.of(
				"stateSetCompression",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.stateSetCompression("NONE")
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.stateSetCompression("ALL")
					.build()
			),
			Arguments.of(
				"patchDirectory",
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.patchDirectory("/a")
					.build(),
				AgentConfig
					.builder()
					.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
					.patchDirectory("/b")
					.build()
			)
		);
	}

	@ParameterizedTest(name = "{index} ⇒ field: {0}")
	@MethodSource("provideChangedResourceGroupConfigs")
	void testResourceGroupConfigChangedParameterized(
		String fieldName,
		ResourceGroupConfig oldGroup,
		ResourceGroupConfig newGroup
	) throws IOException, InterruptedException {
		// Create two default configs with the injected resource groups
		AgentConfig oldAgentConfig = AgentConfig
			.builder()
			.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
			.resourceGroups(Map.of("paris", oldGroup))
			.build();
		AgentConfig newAgentConfig = AgentConfig
			.builder()
			.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
			.resourceGroups(Map.of("paris", oldGroup))
			.build();

		// Create ReloadService with default configs
		ReloadService reloadService = createReloadServiceWithConfigs(oldAgentConfig, newAgentConfig);

		assertTrue(
			reloadService.resourceGroupGlobalConfigHasChanged(oldGroup, newGroup),
			"Expected config change in field '%s' to be detected".formatted(fieldName)
		);

		shutdownAgents(reloadService);
	}

	@Test
	void testResourceGroupConfigChanged_sameConfig_shouldReturnFalse() throws IOException, InterruptedException {
		ResourceGroupConfig group = ResourceGroupConfig.builder().build();

		// Create two default configs with the injected resource groups
		AgentConfig oldAgentConfig = AgentConfig
			.builder()
			.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
			.resourceGroups(Map.of("paris", group))
			.build();
		AgentConfig newAgentConfig = AgentConfig
			.builder()
			.otelConfig(Map.of(OTEL_EXPORTER_OTLP_METRICS_PROTOCOL, "noop"))
			.resourceGroups(Map.of("paris", group))
			.build();

		ReloadService reloadService = createReloadServiceWithConfigs(oldAgentConfig, newAgentConfig);

		assertFalse(reloadService.resourceGroupGlobalConfigHasChanged(group, group));

		shutdownAgents(reloadService);
	}

	private static Stream<Arguments> provideChangedResourceGroupConfigs() {
		return Stream.of(
			Arguments.of(
				"loggerLevel",
				ResourceGroupConfig.builder().loggerLevel("INFO").build(),
				ResourceGroupConfig.builder().loggerLevel("DEBUG").build()
			),
			Arguments.of(
				"outputDirectory",
				ResourceGroupConfig.builder().outputDirectory("/opt/logs1").build(),
				ResourceGroupConfig.builder().outputDirectory("/opt/logs2").build()
			),
			Arguments.of(
				"collectPeriod",
				ResourceGroupConfig.builder().collectPeriod(60L).build(),
				ResourceGroupConfig.builder().collectPeriod(120L).build()
			),
			Arguments.of(
				"discoveryCycle",
				ResourceGroupConfig.builder().discoveryCycle(10).build(),
				ResourceGroupConfig.builder().discoveryCycle(30).build()
			),
			Arguments.of(
				"alertingSystemConfig",
				ResourceGroupConfig
					.builder()
					.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("old").build())
					.build(),
				ResourceGroupConfig
					.builder()
					.alertingSystemConfig(AlertingSystemConfig.builder().problemTemplate("new").build())
					.build()
			),
			Arguments.of(
				"sequential",
				ResourceGroupConfig.builder().sequential(true).build(),
				ResourceGroupConfig.builder().sequential(false).build()
			),
			Arguments.of(
				"enableSelfMonitoring",
				ResourceGroupConfig.builder().enableSelfMonitoring(true).build(),
				ResourceGroupConfig.builder().enableSelfMonitoring(false).build()
			),
			Arguments.of(
				"resolveHostnameToFqdn",
				ResourceGroupConfig.builder().resolveHostnameToFqdn(true).build(),
				ResourceGroupConfig.builder().resolveHostnameToFqdn(false).build()
			),
			Arguments.of(
				"monitorFilters",
				ResourceGroupConfig.builder().monitorFilters(Set.of("filter-a")).build(),
				ResourceGroupConfig.builder().monitorFilters(Set.of("filter-b")).build()
			),
			Arguments.of(
				"jobTimeout",
				ResourceGroupConfig.builder().jobTimeout(30L).build(),
				ResourceGroupConfig.builder().jobTimeout(60L).build()
			),
			Arguments.of(
				"attributes",
				ResourceGroupConfig.builder().attributes(Map.of("env", "prod")).build(),
				ResourceGroupConfig.builder().attributes(Map.of("env", "dev")).build()
			),
			Arguments.of(
				"metrics",
				ResourceGroupConfig.builder().metrics(Map.of("carbon", 100.0)).build(),
				ResourceGroupConfig.builder().metrics(Map.of("carbon", 300.0)).build()
			),
			Arguments.of(
				"stateSetCompression",
				ResourceGroupConfig.builder().stateSetCompression("SUPPRESS_ZEROS").build(),
				ResourceGroupConfig.builder().stateSetCompression("NONE").build()
			)
		);
	}
}
