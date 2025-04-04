package org.metricshub.agent.opentelemetry.metric.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpDownCounterMetricRecorderTest {

	@Mock
	private NumberMetric mockMetric;

	private UpDownCounterMetricRecorder upDownCounterMetricRecorder;

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		upDownCounterMetricRecorder =
			UpDownCounterMetricRecorder
				.builder()
				.withMetric(mockMetric)
				.withUnit("errors")
				.withDescription("Test up-down counter metric")
				.build();
	}

	@Test
	void doRecord_shouldReturnMetric_whenMetricHasValue() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn(0.0);
		when(mockMetric.getName()).thenReturn("test.updowncounter.errors");
		when(mockMetric.getCollectTime()).thenReturn(System.currentTimeMillis());
		when(mockMetric.getAttributes()).thenReturn(Map.of("key", "value"));

		// When
		final Optional<Metric> result = upDownCounterMetricRecorder.doRecord();

		// Then
		assertTrue(result.isPresent(), "Expected a recorded metric");
		final Metric metric = result.get();
		assertEquals("test.updowncounter.errors", metric.getName(), "Metric name should match");
		assertEquals("Test up-down counter metric", metric.getDescription(), "Metric description should match");
		assertEquals("errors", metric.getUnit(), "Metric unit should match");
		final List<NumberDataPoint> dataPointsList = metric.getSum().getDataPointsList();
		assertEquals(1, dataPointsList.size(), "Expected a single metric");
		final NumberDataPoint numberDataPoint = dataPointsList.get(0);
		assertEquals(0.0, numberDataPoint.getAsDouble(), "Metric value should match");
		final List<KeyValue> attributesList = numberDataPoint.getAttributesList();
		assertEquals(1, attributesList.size(), "Expected a single attribute");
		final KeyValue keyValue = attributesList.get(0);
		assertEquals("key", keyValue.getKey(), "Attribute key should match");
		assertEquals("value", keyValue.getValue().getStringValue(), "Attribute value should match");
	}

	@Test
	void doRecord_shouldReturnMetric_whenMetricHasValue_andResourceAttributes() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn(0.0);
		when(mockMetric.getName()).thenReturn("test.updowncounter.errors");
		when(mockMetric.getCollectTime()).thenReturn(System.currentTimeMillis());
		when(mockMetric.getAttributes()).thenReturn(Map.of("key", "value"));

		upDownCounterMetricRecorder =
			UpDownCounterMetricRecorder
				.builder()
				.withMetric(mockMetric)
				.withUnit("errors")
				.withDescription("Test up-down counter metric")
				.withResourceAttributes(Map.of("resource", "resourceValue"))
				.build();

		// When
		final Optional<Metric> result = upDownCounterMetricRecorder.doRecord();

		// Then
		assertTrue(result.isPresent(), "Expected a recorded metric");
		final Metric metric = result.get();
		assertEquals("test.updowncounter.errors", metric.getName(), "Metric name should match");
		assertEquals("Test up-down counter metric", metric.getDescription(), "Metric description should match");
		assertEquals("errors", metric.getUnit(), "Metric unit should match");
		final List<NumberDataPoint> dataPointsList = metric.getSum().getDataPointsList();
		assertEquals(1, dataPointsList.size(), "Expected a single metric");
		final NumberDataPoint numberDataPoint = dataPointsList.get(0);
		assertEquals(0.0, numberDataPoint.getAsDouble(), "Metric value should match");
		final List<KeyValue> attributesList = numberDataPoint.getAttributesList();
		assertEquals(2, attributesList.size(), "Expected two attributes");
		final KeyValue resourceKeyVaue = attributesList
			.stream()
			.filter(kv -> "resource".equals(kv.getKey()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("resource attribute not found"));
		assertEquals("resource", resourceKeyVaue.getKey(), "Attribute resource should match");
		assertEquals("resourceValue", resourceKeyVaue.getValue().getStringValue(), "Attribute resourceValue should match");
		final KeyValue keyValue = attributesList
			.stream()
			.filter(kv -> "key".equals(kv.getKey()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("key attribute not found"));
		assertEquals("key", keyValue.getKey(), "Attribute key should match");
		assertEquals("value", keyValue.getValue().getStringValue(), "Attribute value should match");
	}

	@Test
	void doRecord_shouldReturnEmptyOptional_whenMetricIsNotUpdated() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(false);

		// When
		final Optional<Metric> result = upDownCounterMetricRecorder.doRecord();

		// Then
		assertTrue(result.isEmpty(), "Expected an empty optional when metric is not updated");
	}

	@Test
	void doRecord_shouldReturnEmptyOptional_whenExceptionOccurs() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenThrow(new RuntimeException("Test exception"));

		// When
		final Optional<Metric> result = upDownCounterMetricRecorder.doRecord();

		// Then
		assertTrue(result.isEmpty(), "Expected an empty optional when an exception occurs");
	}
}
