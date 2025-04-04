package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceMeterTest {

	private static final String TEST_INSTRUMENTATION = "test.instrumentation";

	@Mock
	private AbstractMetric mockMetric;

	@Mock
	private MetricContext mockContext;

	@Mock
	private AbstractMetricRecorder mockRecorder;

	private ResourceMeter resourceMeter;

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		resourceMeter =
			ResourceMeter.builder().withInstrumentation(TEST_INSTRUMENTATION).withAttributes(Map.of("key", "value")).build();
	}

	@Test
	void recordSafe_shouldReturnDefaultInstance_whenExceptionOccurs() {
		resourceMeter.getMetricRecorders().add(mockRecorder);
		when(mockRecorder.doRecord()).thenThrow(new RuntimeException("Test exception"));

		final ResourceMetrics result = resourceMeter.recordSafe();

		assertEquals(ResourceMetrics.getDefaultInstance(), result, "Should return default instance on exception");
	}

	@Test
	void recordSafe_shouldReturnResourceMetrics_whenMetricsAreRecorded() {
		resourceMeter.getMetricRecorders().add(mockRecorder);
		final Metric metric = Metric
			.newBuilder()
			.setName(MetricFactory.extractName("name"))
			.setDescription("description")
			.setUnit("unit")
			.build();
		when(mockRecorder.doRecord()).thenReturn(Optional.of(metric));

		final ResourceMetrics result = resourceMeter.recordSafe();

		assertNotNull(result, "ResourceMetrics should not be null");
	}

	@Test
	void registerRecorder_shouldAddMetricRecorders() {
		final NumberMetric numberMetric = NumberMetric
			.builder()
			.name("test.metric")
			.value(10.0)
			.collectTime(System.currentTimeMillis())
			.build();
		final MetricContext context = MetricContext
			.builder()
			.withDescription("Test description")
			.withUnit("s")
			.withType(MetricType.GAUGE)
			.build();

		resourceMeter.registerRecorder(context, numberMetric);

		assertFalse(
			resourceMeter.getMetricRecorders().isEmpty(),
			"Metric recorders should not be empty after registration"
		);
	}
}
