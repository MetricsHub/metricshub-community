package org.sentrysoftware.metricshub.agent.opentelemetry.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.CounterMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.CounterStateMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.GaugeMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.GaugeStateMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterMetricRecorder;
import org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterStateMetricRecorder;
import org.sentrysoftware.metricshub.engine.connector.model.metric.MetricType;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;
import org.sentrysoftware.metricshub.engine.telemetry.metric.StateSetMetric;

@ExtendWith(MockitoExtension.class)
class MetricHandlerTest {

	@Mock
	private StateSetMetric mockStateSetMetric;

	@Mock
	private NumberMetric mockNumberMetric;

	@Mock
	private MetricContext mockContext;

	@BeforeEach
	void setUp() {
		when(mockContext.getDescription()).thenReturn("Test description");
		when(mockContext.getUnit()).thenReturn("s");
	}

	@Test
	void handle_shouldReturnGaugeMetricRecorder_whenHandlingGaugeNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockNumberMetric);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeMetricRecorder);
	}

	@Test
	void handle_shouldReturnGaugeStateMetricRecorder_whenHandlingGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockStateSetMetric);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof GaugeStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnCounterMetricRecorder_whenHandlingCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockNumberMetric);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterMetricRecorder);
	}

	@Test
	void handle_shouldReturnCounterStateMetricRecorder_whenHandlingCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockStateSetMetric);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof CounterStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnUpDownCounterMetricRecorder_whenHandlingUpDownCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockNumberMetric);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterMetricRecorder);
	}

	@Test
	void handle_shouldReturnUpDownCounterStateMetricRecorder_whenHandlingUpDownCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(mockContext, mockStateSetMetric);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof UpDownCounterStateMetricRecorder);
	}
}
