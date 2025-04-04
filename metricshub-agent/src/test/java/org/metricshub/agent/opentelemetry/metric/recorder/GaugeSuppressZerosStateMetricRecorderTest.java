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
import org.metricshub.engine.telemetry.metric.StateSetMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GaugeSuppressZerosStateMetricRecorderTest {

	@Mock
	private StateSetMetric mockMetric;

	private GaugeSuppressZerosStateMetricRecorder recorder;

	private static final String TEST_STATE_VALUE = "ok";

	@BeforeEach
	void setUp() {
		TestHelper.configureGlobalLogger();
		recorder =
			GaugeSuppressZerosStateMetricRecorder
				.builder()
				.withMetric(mockMetric)
				.withDescription("Test gauge suppress zeros state metric")
				.withStateValue(TEST_STATE_VALUE)
				.build();
	}

	@Test
	void doRecord_shouldReturnMetric_whenMetricHasMatchingStateValue() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn(TEST_STATE_VALUE);
		when(mockMetric.getName()).thenReturn("test.state.metric");
		when(mockMetric.getCollectTime()).thenReturn(System.currentTimeMillis());
		when(mockMetric.getAttributes()).thenReturn(Map.of("state", "ok"));

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isPresent(), "Expected a recorded metric");
		final Metric metric = result.get();
		assertEquals("test.state.metric", metric.getName(), "Metric name should match");
		final List<NumberDataPoint> dataPointsList = metric.getGauge().getDataPointsList();
		final NumberDataPoint numberDataPoint = dataPointsList.get(0);
		assertEquals(1.0, numberDataPoint.getAsDouble(), "Metric value should match for matching state");
		final List<KeyValue> attributesList = numberDataPoint.getAttributesList();
		assertEquals(1, attributesList.size(), "Expected a single attribute");
		final KeyValue attribute = attributesList.get(0);
		assertEquals("state", attribute.getKey(), "Attribute state should match");
		assertEquals("ok", attribute.getValue().getStringValue(), "Attribute value should match");
	}

	@Test
	void doRecord_shouldReturnMetric_whenMetricHasMatchingStateValue_andResourceAttributes() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn(TEST_STATE_VALUE);
		when(mockMetric.getName()).thenReturn("test.state.metric");
		when(mockMetric.getCollectTime()).thenReturn(System.currentTimeMillis());
		when(mockMetric.getAttributes()).thenReturn(Map.of("state", "ok"));

		recorder =
			GaugeSuppressZerosStateMetricRecorder
				.builder()
				.withMetric(mockMetric)
				.withDescription("Test gauge suppress zeros state metric")
				.withStateValue(TEST_STATE_VALUE)
				.withResourceAttributes(Map.of("resource", "resourceValue"))
				.build();

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isPresent(), "Expected a recorded metric");
		final Metric metric = result.get();
		assertEquals("test.state.metric", metric.getName(), "Metric name should match");
		final List<NumberDataPoint> dataPointsList = metric.getGauge().getDataPointsList();
		final NumberDataPoint numberDataPoint = dataPointsList.get(0);
		assertEquals(1.0, numberDataPoint.getAsDouble(), "Metric value should match for matching state");
		final List<KeyValue> attributesList = numberDataPoint.getAttributesList();
		assertEquals(2, attributesList.size(), "Expected two attributes");
		final KeyValue resourceKeyVaue = attributesList
			.stream()
			.filter(kv -> "resource".equals(kv.getKey()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("resource attribute not found"));
		assertEquals("resource", resourceKeyVaue.getKey(), "Attribute resource should match");
		assertEquals("resourceValue", resourceKeyVaue.getValue().getStringValue(), "Attribute resourceValue should match");
		final KeyValue stateKeyVaue = attributesList
			.stream()
			.filter(kv -> "state".equals(kv.getKey()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("state attribute not found"));
		assertEquals("state", stateKeyVaue.getKey(), "Attribute state should match");
		assertEquals("ok", stateKeyVaue.getValue().getStringValue(), "Attribute stateValue should match");
	}

	@Test
	void doRecord_shouldReturnMetricWithZero_whenStateHasSwitchedFromMatching() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn("failed");
		when(mockMetric.getPreviousValue()).thenReturn(TEST_STATE_VALUE);
		when(mockMetric.getName()).thenReturn("test.state.metric");

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isPresent(), "Expected a recorded metric");
		final List<NumberDataPoint> dataPointsList = result.get().getGauge().getDataPointsList();
		final NumberDataPoint numberDataPoint = dataPointsList.get(0);
		assertEquals(0.0, numberDataPoint.getAsDouble(), "Metric value should be zero for switched state");
		final List<KeyValue> attributesList = numberDataPoint.getAttributesList();
		assertEquals(1, attributesList.size(), "Expected a single attribute");
		final KeyValue attribute = attributesList.get(0);
		assertEquals("state", attribute.getKey(), "Attribute state should match");
		assertEquals("ok", attribute.getValue().getStringValue(), "Attribute value should match");
	}

	@Test
	void doRecord_shouldReturnEmptyOptional_whenStateHasNotSwitchedAndDoesNotMatch() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenReturn("failed");
		when(mockMetric.getPreviousValue()).thenReturn("failed");

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isEmpty(), "Expected an empty optional when state is unchanged and does not match");
	}

	@Test
	void doRecord_shouldReturnEmptyOptional_whenMetricIsNotUpdated() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(false);

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isEmpty(), "Expected an empty optional when metric is not updated");
	}

	@Test
	void doRecord_shouldReturnEmptyOptional_whenExceptionOccurs() {
		// Given
		when(mockMetric.isUpdated()).thenReturn(true);
		when(mockMetric.getValue()).thenThrow(new RuntimeException("Test exception"));

		// When
		final Optional<Metric> result = recorder.doRecord();

		// Then
		assertTrue(result.isEmpty(), "Expected an empty optional when an exception occurs");
	}
}
