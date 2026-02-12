package org.metricshub.agent.opentelemetry.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
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
	void testHandleReturnsGaugeMetricRecorderForGaugeNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeMetricRecorder);
	}

	@Test
	void testHandleReturnsGaugeStateMetricRecorderForGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof GaugeStateMetricRecorder);
	}

	@Test
	void testHandleReturnsGaugeSuppressZerosStateMetricRecorderForGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.GAUGE);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof GaugeSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof GaugeSuppressZerosStateMetricRecorder);
	}

	@Test
	void testHandleReturnsCounterMetricRecorderForCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterMetricRecorder);
	}

	@Test
	void testHandleReturnsCounterStateMetricRecorderForCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof CounterStateMetricRecorder);
	}

	@Test
	void testHandleReturnsCounterSuppressZerosStateMetricRecorderForGaugeStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof CounterSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof CounterSuppressZerosStateMetricRecorder);
	}

	@Test
	void testHandleReturnsUpDownCounterMetricRecorderForUpDownCounterNumberMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockNumberMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(1, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterMetricRecorder);
	}

	@Test
	void testHandleReturnsUpDownCounterStateMetricRecorderForUpDownCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof UpDownCounterStateMetricRecorder);
	}

	@Test
	void testHandleReturnsUpDownCounterSuppressZerosStateMetricRecorderForUpDownCounterStateSetMetric() {
		when(mockContext.getType()).thenReturn(MetricType.UP_DOWN_COUNTER);
		when(mockStateSetMetric.getStateSet()).thenReturn(new String[] { "ok", "failed" });
		when(mockContext.isSuppressZerosCompression()).thenReturn(true);
		final List<AbstractMetricRecorder> recorders = MetricHandler.handle(
			mockContext,
			mockStateSetMetric,
			Collections.emptyMap(),
			new HashMap<>()
		);

		assertEquals(2, recorders.size());
		assertTrue(recorders.get(0) instanceof UpDownCounterSuppressZerosStateMetricRecorder);
		assertTrue(recorders.get(1) instanceof UpDownCounterSuppressZerosStateMetricRecorder);
	}
}
