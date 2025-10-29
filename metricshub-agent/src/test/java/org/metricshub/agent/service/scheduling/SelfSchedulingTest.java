package org.metricshub.agent.service.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.COMPANY_ATTRIBUTE_VALUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.service.TestHelper;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SelfSchedulingTest {

	@Test
	void testSchedule() {
		TestHelper.configureGlobalLogger();
		final AgentInfo agentInfo = new AgentInfo();
		final AgentConfig agentConfig = AgentConfig
			.builder()
			.attributes(Map.of(COMPANY_ATTRIBUTE_KEY, COMPANY_ATTRIBUTE_VALUE))
			.build();
		final TestHelper.TestOtelClient otelClient = new TestHelper.TestOtelClient();

		final MetricsExporter metricsExporter = MetricsExporter.builder().withClient(otelClient).build();
		final ThreadPoolTaskScheduler taskSchedulerMock = spy(ThreadPoolTaskScheduler.class);
		final ScheduledFuture<?> scheduledFutureMock = spy(ScheduledFuture.class);

		doReturn(scheduledFutureMock).when(taskSchedulerMock).schedule(any(Runnable.class), any(Trigger.class));

		final SelfScheduling selfScheduling = SelfScheduling
			.builder()
			.withAgentConfig(agentConfig)
			.withAgentInfo(agentInfo)
			.withMetricsExporter(metricsExporter)
			.withSchedules(new HashMap<>())
			.withTaskScheduler(taskSchedulerMock)
			.build();
		selfScheduling.schedule();

		selfScheduling.recordAndExport();

		final ExportMetricsServiceRequest request = otelClient.getRequest();

		// Verify the host metrics
		final ResourceMetrics resourceMetrics = request.getResourceMetricsList().stream().findFirst().orElseThrow();
		assertNotNull(resourceMetrics);
		final Resource resource = resourceMetrics.getResource();
		assertNotNull(resource);
		final List<KeyValue> resourceKeyValueAttributes = resource.getAttributesList();
		final Map<String, String> resourceAttributes = resourceKeyValueAttributes
			.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));

		// Verify resource attributes
		assertNotNull(resourceAttributes.get(AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_INFO_NAME_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_INFO_VERSION_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY));
		assertNotNull(resourceAttributes.get(COMPANY_ATTRIBUTE_KEY));

		// Verify the metrics
		final List<ScopeMetrics> scopeMetricsList = resourceMetrics.getScopeMetricsList();
		assertEquals(1, scopeMetricsList.size());
		final ScopeMetrics scopeMetrics = scopeMetricsList.get(0);
		assertEquals(1, scopeMetrics.getMetricsCount());
		final List<Metric> metrics = scopeMetrics.getMetricsList();
		assertEquals(1, metrics.size());
		final Metric metric = metrics.get(0);
		assertEquals(AgentInfo.METRICS_HUB_AGENT_METRIC_NAME, metric.getName());
		assertEquals(SelfScheduling.METRICS_HUB_AGENT_INFORMATION, metric.getDescription());
		assertEquals("", metric.getUnit());
		final Gauge gauge = metric.getGauge();
		final List<NumberDataPoint> dataPointsList = gauge.getDataPointsList();
		assertEquals(1, dataPointsList.size());
		final NumberDataPoint dataPoint = dataPointsList.get(0);
		assertEquals(1.0, dataPoint.getAsDouble());
	}
}
