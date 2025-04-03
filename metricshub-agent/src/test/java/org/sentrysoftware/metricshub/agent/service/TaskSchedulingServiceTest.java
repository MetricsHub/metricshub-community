package org.sentrysoftware.metricshub.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_VALUE;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.HOST_ID_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.HOST_TYPE_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.OS_LINUX;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_OTTAWA_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_OTTAWA_SITE_VALUE;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_PARIS_RESOURCE_GROUP_KEY;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SENTRY_PARIS_SITE_VALUE;
import static org.sentrysoftware.metricshub.agent.helper.TestConstants.SITE_ATTRIBUTE_KEY;
import static org.sentrysoftware.metricshub.agent.service.scheduling.ResourceGroupScheduling.HW_SITE_PUE_METRIC;
import static org.sentrysoftware.metricshub.agent.service.scheduling.ResourceGroupScheduling.METRICSHUB_RESOURCE_GROUP_KEY_FORMAT;
import static org.sentrysoftware.metricshub.agent.service.scheduling.ResourceScheduling.METRICSHUB_RESOURCE_KEY_FORMAT;
import static org.sentrysoftware.metricshub.agent.service.scheduling.SelfScheduling.METRICSHUB_OVERALL_SELF_TASK_KEY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.sentrysoftware.metricshub.agent.config.AgentConfig;
import org.sentrysoftware.metricshub.agent.config.ResourceConfig;
import org.sentrysoftware.metricshub.agent.config.ResourceGroupConfig;
import org.sentrysoftware.metricshub.agent.context.AgentInfo;
import org.sentrysoftware.metricshub.agent.helper.ConfigHelper;
import org.sentrysoftware.metricshub.agent.opentelemetry.MetricsExporter;
import org.sentrysoftware.metricshub.agent.opentelemetry.client.NoopClient;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class TaskSchedulingServiceTest {

	private static final String NO_CONFIG_RESOURCE_GROUP_KEY = "no-config";

	@Test
	void testScheduleSelfRecorder() {
		final AgentConfig agentConfig = AgentConfig
			.builder()
			.attributes(Map.of(COMPANY_ATTRIBUTE_KEY, COMPANY_ATTRIBUTE_VALUE))
			.build();

		final AgentInfo agentInfo = new AgentInfo();

		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));

		final TaskSchedulingService taskSchedulingService = TaskSchedulingService
			.builder()
			.withAgentConfig(agentConfig)
			.withAgentInfo(agentInfo)
			.withMetricsExporter(MetricsExporter.builder().withClient(new NoopClient()).build())
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.build();

		taskSchedulingService.scheduleSelfRecorder();

		verify(taskSchedulerMock, times(1)).schedule(any(Runnable.class), any(Trigger.class));

		assertEquals(scheduledFutureMock, taskSchedulingService.getSchedules().get(METRICSHUB_OVERALL_SELF_TASK_KEY));
	}

	@Test
	void testScheduleResourceGroupRecorders() {
		final Map<String, ResourceGroupConfig> resourceGroups = new HashMap<>();
		resourceGroups.put(
			SENTRY_PARIS_RESOURCE_GROUP_KEY,
			ResourceGroupConfig
				.builder()
				.attributes(Map.of(SITE_ATTRIBUTE_KEY, SENTRY_PARIS_SITE_VALUE))
				.metrics(Map.of(HW_SITE_PUE_METRIC, 1D))
				.build()
		);
		resourceGroups.put(
			SENTRY_OTTAWA_RESOURCE_GROUP_KEY,
			ResourceGroupConfig
				.builder()
				.attributes(Map.of(SITE_ATTRIBUTE_KEY, SENTRY_OTTAWA_SITE_VALUE))
				.metrics(Map.of(HW_SITE_PUE_METRIC, 1D))
				.build()
		);
		resourceGroups.put(NO_CONFIG_RESOURCE_GROUP_KEY, null);

		final AgentConfig agentConfig = AgentConfig.builder().resourceGroups(resourceGroups).build();

		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));

		final TaskSchedulingService taskSchedulingService = TaskSchedulingService
			.builder()
			.withAgentConfig(agentConfig)
			.withMetricsExporter(MetricsExporter.builder().withClient(new NoopClient()).build())
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.build();

		taskSchedulingService.scheduleResourceGroupRecorders();

		verify(taskSchedulerMock, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		assertEquals(
			scheduledFutureMock,
			taskSchedulingService
				.getSchedules()
				.get(String.format(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT, SENTRY_PARIS_RESOURCE_GROUP_KEY))
		);

		assertEquals(
			scheduledFutureMock,
			taskSchedulingService
				.getSchedules()
				.get(String.format(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT, SENTRY_OTTAWA_RESOURCE_GROUP_KEY))
		);

		assertNull(
			taskSchedulingService
				.getSchedules()
				.get(String.format(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT, NO_CONFIG_RESOURCE_GROUP_KEY))
		);
	}

	@Test
	void testScheduleResourcesInResourceGroups() throws IOException {
		final Map<String, ResourceConfig> resourceConfigMap = new HashMap<>();
		final String resourceKey1 = UUID.randomUUID().toString();
		resourceConfigMap.put(
			resourceKey1,
			ResourceConfig
				.builder()
				.attributes(
					Map.of(HOST_NAME, resourceKey1, HOST_ID_ATTRIBUTE_KEY, resourceKey1, HOST_TYPE_ATTRIBUTE_KEY, OS_LINUX)
				)
				.protocols(Map.of("snmp", SnmpConfiguration.builder().build()))
				.collectPeriod(AgentConfig.DEFAULT_COLLECT_PERIOD)
				.build()
		);

		final String resourceKey2 = UUID.randomUUID().toString();
		resourceConfigMap.put(
			resourceKey2,
			ResourceConfig
				.builder()
				.attributes(
					Map.of(HOST_NAME, resourceKey2, HOST_ID_ATTRIBUTE_KEY, resourceKey2, HOST_TYPE_ATTRIBUTE_KEY, OS_LINUX)
				)
				.protocols(Map.of("snmp", SnmpConfiguration.builder().build()))
				.collectPeriod(AgentConfig.DEFAULT_COLLECT_PERIOD)
				.build()
		);

		final ResourceGroupConfig resourceGroupConfig = ResourceGroupConfig.builder().resources(resourceConfigMap).build();

		final AgentConfig agentConfig = AgentConfig
			.builder()
			.resourceGroups(Map.of(SENTRY_PARIS_RESOURCE_GROUP_KEY, resourceGroupConfig))
			.build();

		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));

		final TaskSchedulingService taskSchedulingService = TaskSchedulingService
			.builder()
			.withAgentConfig(agentConfig)
			.withMetricsExporter(MetricsExporter.builder().withClient(new NoopClient()).build())
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.withTelemetryManagers(
				Map.of(
					SENTRY_PARIS_RESOURCE_GROUP_KEY,
					Map.of(resourceKey1, new TelemetryManager(), resourceKey2, new TelemetryManager())
				)
			)
			.withHostMetricDefinitions(ConfigHelper.readHostMetricDefinitions())
			.withExtensionManager(ExtensionManager.empty())
			.build();

		taskSchedulingService.scheduleResourcesInResourceGroups(SENTRY_PARIS_RESOURCE_GROUP_KEY, resourceGroupConfig);

		verify(taskSchedulerMock, times(2)).schedule(any(Runnable.class), any(Trigger.class));

		assertEquals(
			scheduledFutureMock,
			taskSchedulingService
				.getSchedules()
				.get(String.format(METRICSHUB_RESOURCE_KEY_FORMAT, SENTRY_PARIS_RESOURCE_GROUP_KEY, resourceKey1))
		);

		assertEquals(
			scheduledFutureMock,
			taskSchedulingService
				.getSchedules()
				.get(String.format(METRICSHUB_RESOURCE_KEY_FORMAT, SENTRY_PARIS_RESOURCE_GROUP_KEY, resourceKey2))
		);
	}
}
