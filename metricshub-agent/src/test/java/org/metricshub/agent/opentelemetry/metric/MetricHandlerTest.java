package org.metricshub.agent.opentelemetry.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterSuppressZerosStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeSuppressZerosStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterSuppressZerosStateMetricRecorder;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeMetricRecorder);
	}

	@Test
	void handle_shouldReturnGaugeStateMetricRecorder_whenHandlingGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof GaugeStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnGaugeSuppressZerosStateMetricRecorder_whenHandlingGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof GaugeSuppressZerosStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnCounterMetricRecorder_whenHandlingCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterMetricRecorder);
	}

	@Test
	void handle_shouldReturnCounterStateMetricRecorder_whenHandlingCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof CounterStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnCopunterSuppressZerosStateMetricRecorder_whenHandlingGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof CounterSuppressZerosStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnUpDownCounterMetricRecorder_whenHandlingUpDownCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterMetricRecorder);
	}

	@Test
	void handle_shouldReturnUpDownCounterStateMetricRecorder_whenHandlingUpDownCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof UpDownCounterStateMetricRecorder);
	}

	@Test
	void handle_shouldReturnUpDownCounterSuppressZerosStateMetricRecorderRecorder_whenHandlingUpDownCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof UpDownCounterSuppressZerosStateMetricRecorder);
	}
}
