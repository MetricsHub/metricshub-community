package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

class ResourceMeterTest {

	private static final String TEST_INSTRUMENTATION = "test.instrumentation";

	private ResourceMeter resourceMeter;

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		resourceMeter =
			ResourceMeter.builder().withInstrumentation(TEST_INSTRUMENTATION).withAttributes(Map.of("key", "value")).build();
	}

	@Test
	void testRecordSafeReturnsDefaultInstanceWhenExceptionOccurs() throws Exception {
		// Use reflection to add a throwing recorder for testing error handling
		final java.lang.reflect.Field field = ResourceMeter.class.getDeclaredField("metricRecorders");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		final java.util.List<AbstractMetricRecorder> recorders = (java.util.List<AbstractMetricRecorder>) field.get(
			resourceMeter
		);
		recorders.add(new ThrowingRecorder());

		final ResourceMetrics result = resourceMeter.recordSafe();

		assertEquals(ResourceMetrics.getDefaultInstance(), result, "Should return default instance on exception");
	}

	@Test
	void testRecordSafeReturnsResourceMetricsWhenMetricsAreRecorded() {
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

		final ResourceMetrics result = resourceMeter.recordSafe();

		assertNotNull(result, "ResourceMetrics should not be null");
		assertEquals(1, result.getScopeMetricsCount(), "Should include a single scope metrics");
		assertEquals(1, result.getScopeMetrics(0).getMetricsCount(), "Should include a single metric");
	}

	@Test
	void testRegisterRecorderAddsMetricRecorders() {
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

	@Test
	void testRecordSafeGroupsStateSetMetricsUnderSingleOtelMetric() {
		final StateSetMetric stateSetMetric = StateSetMetric
			.builder()
			.name("hw.status")
			.collectTime(System.currentTimeMillis())
			.stateSet(new String[] { "ok", "degraded", "failed" })
			.value("ok")
			.build();
		final MetricContext context = MetricContext
			.builder()
			.withDescription("Hardware status")
			.withType(MetricType.GAUGE)
			.withUnit("1")
			.withIsSuppressZerosCompression(false)
			.build();

		resourceMeter.registerRecorder(context, stateSetMetric);

		final ResourceMetrics result = resourceMeter.recordSafe();

		assertEquals(1, result.getScopeMetricsCount(), "Should include a single scope metrics");
		assertEquals(1, result.getScopeMetrics(0).getMetricsCount(), "Should group into a single metric");
		final Metric metric = result.getScopeMetrics(0).getMetrics(0);
		final List<NumberDataPoint> dataPoints = metric.getGauge().getDataPointsList();
		assertEquals(3, dataPoints.size(), "Should contain one datapoint per state");

		final Map<String, Double> stateValues = new HashMap<>();
		for (NumberDataPoint dataPoint : dataPoints) {
			final String state = dataPoint
				.getAttributesList()
				.stream()
				.filter(attribute -> "state".equals(attribute.getKey()))
				.map(KeyValue::getValue)
				.map(value -> value.getStringValue())
				.findFirst()
				.orElse(null);
			stateValues.put(state, dataPoint.getAsDouble());
		}

		assertEquals(1.0, stateValues.get("ok"));
		assertEquals(0.0, stateValues.get("degraded"));
		assertEquals(0.0, stateValues.get("failed"));
	}

	private static final class ThrowingRecorder extends AbstractMetricRecorder {

		private ThrowingRecorder() {
			super(
				NumberMetric.builder().name("boom.metric").value(1.0).collectTime(System.currentTimeMillis()).build(),
				"",
				"boom",
				Map.of(),
				new HashMap<>()
			);
		}

		@Override
		public Optional<Metric> doRecord() {
			throw new RuntimeException("Test exception");
		}

		@Override
		protected Metric buildMetric(final Double value) {
			return Metric.getDefaultInstance();
		}
	}
}
