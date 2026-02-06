package org.metricshub.agent.service.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_VALUE;
import static org.metricshub.agent.helper.TestConstants.PARIS_RESOURCE_GROUP_KEY;
import static org.metricshub.agent.helper.TestConstants.SITE_ATTRIBUTE_KEY;
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.HW_SITE_CARBON_INTENSITY_METRIC;
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.HW_SITE_ELECTRICITY_COST_METRIC;
import static org.metricshub.agent.service.scheduling.ResourceGroupScheduling.HW_SITE_PUE_METRIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.helper.TestConstants;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.common.helpers.MapHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ResourceGroupSchedulingTest {

	@Test
	void testSchedule() {
		TestHelper.configureGlobalLogger();
		final AgentConfig agentConfig = AgentConfig
			.builder()
			.attributes(Map.of(COMPANY_ATTRIBUTE_KEY, COMPANY_ATTRIBUTE_VALUE))
			.build();

		final ResourceGroupConfig resourceGroupConfig = ResourceGroupConfig
			.builder()
			.metrics(
				Map.of(HW_SITE_CARBON_INTENSITY_METRIC, 350D, HW_SITE_ELECTRICITY_COST_METRIC, 0.12D, HW_SITE_PUE_METRIC, 1.8D)
			)
			.attributes(Map.of(SITE_ATTRIBUTE_KEY, TestConstants.PARIS_SITE_VALUE))
			.build();

		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		final TestHelper.TestOtelClient otelClient = new TestHelper.TestOtelClient();
		final MetricsExporter metricsExporter = MetricsExporter.builder().withClient(otelClient).build();

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));

		final ResourceGroupScheduling resourceGroupScheduling = ResourceGroupScheduling
			.builder()
			.withAgentConfig(agentConfig)
			.withExtensionManager(ExtensionManager.empty())
			.withMetricsExporter(metricsExporter)
			.withResourceGroupConfig(resourceGroupConfig)
			.withResourceGroupKey(PARIS_RESOURCE_GROUP_KEY)
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.build();
		resourceGroupScheduling.schedule();
		resourceGroupScheduling.recordAndExport();
		final ExportMetricsServiceRequest request = otelClient.getRequest();

		assertNotNull(request);
		final List<ResourceMetrics> resourceMetricsList = request.getResourceMetricsList();
		assertFalse(resourceMetricsList.isEmpty());
		final ResourceMetrics resourceMetrics = resourceMetricsList.get(0);
		assertNotNull(resourceMetrics);
		final Resource resource = resourceMetrics.getResource();
		assertNotNull(resource);
		final Map<String, String> resourceAttributes = resource
			.getAttributesList()
			.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
		assertTrue(
			MapHelper.areEqual(
				Map.of(COMPANY_ATTRIBUTE_KEY, COMPANY_ATTRIBUTE_VALUE, SITE_ATTRIBUTE_KEY, TestConstants.PARIS_SITE_VALUE),
				resourceAttributes
			)
		);
		final List<ScopeMetrics> scopeMetricsList = resourceMetrics.getScopeMetricsList();
		assertEquals(1, scopeMetricsList.size());
		final ScopeMetrics scopeMetrics = scopeMetricsList.get(0);
		final List<Metric> metricsList = scopeMetrics.getMetricsList();
		assertEquals(3, metricsList.size());
		final Map<String, Metric> metrics = metricsList
			.stream()
			.collect(Collectors.toMap(Metric::getName, Function.identity()));
		assertTrue(metrics.containsKey(HW_SITE_CARBON_INTENSITY_METRIC));
		assertTrue(metrics.containsKey(HW_SITE_ELECTRICITY_COST_METRIC));
		assertTrue(metrics.containsKey(HW_SITE_PUE_METRIC));

		// Check metrics
		final Metric carbonIntensityMetric = metrics.get(HW_SITE_CARBON_INTENSITY_METRIC);
		assertEquals(350D, carbonIntensityMetric.getGauge().getDataPoints(0).getAsDouble());

		final Metric electricityCostMetric = metrics.get(HW_SITE_ELECTRICITY_COST_METRIC);
		assertEquals(0.12D, electricityCostMetric.getGauge().getDataPoints(0).getAsDouble());

		final Metric pueMetric = metrics.get(HW_SITE_PUE_METRIC);
		assertEquals(1.8D, pueMetric.getGauge().getDataPoints(0).getAsDouble());
	}
}
