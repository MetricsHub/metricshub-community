package org.metricshub.agent.service.scheduling;

import static org.metricshub.agent.helper.TestConstants.HOSTNAME;
import static org.metricshub.agent.helper.TestConstants.HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.OS_LINUX;
import static org.metricshub.agent.helper.TestConstants.PARIS_RESOURCE_GROUP_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.agent.service.task.MonitoringTask;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

class ResourceSchedulingTest {

	@Test
	void testSchedule() throws IOException {
		TestHelper.configureGlobalLogger();
		final ResourceConfig resourceConfig = ResourceConfig
			.builder()
			.loggerLevel("OFF")
			.attributes(Map.of(HOST_NAME, HOSTNAME, HOST_TYPE_ATTRIBUTE_KEY, OS_LINUX))
			.discoveryCycle(4)
			.collectPeriod(2L)
			.resolveHostnameToFqdn(true)
			.build();
		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));
		final TestHelper.TestOtelClient otelClient = new TestHelper.TestOtelClient();

		final MetricsExporter metricsExporter = MetricsExporter.builder().withClient(otelClient).build();

		final ResourceScheduling resourceScheduling = ResourceScheduling
			.builder()
			.withHostMetricDefinitions(ConfigHelper.readHostMetricDefinitions())
			.withMetricsExporter(metricsExporter)
			.withResourceConfig(resourceConfig)
			.withTelemetryManager(new TelemetryManager())
			.withTaskScheduler(taskSchedulerMock)
			.withResourceGroupKey(PARIS_RESOURCE_GROUP_KEY)
			.withResourceKey(HOSTNAME)
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.withExtensionManager(ExtensionManager.empty())
			.build();

		resourceScheduling.schedule();

		verify(taskSchedulerMock, times(1)).schedule(any(MonitoringTask.class), any(PeriodicTrigger.class));
	}
}
