package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class MetricFactoryNonFiniteTest {

	private static final String METRIC = "hw.temperature";

	private final MetricFactory metricFactory = new MetricFactory("host", null);

	@Test
	void nonFiniteDoublesAreRefused() {
		for (final Double value : new Double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
			final Monitor monitor = Monitor.builder().build();
			assertNull(metricFactory.collectNumberMetric(monitor, METRIC, value, 1000L), String.valueOf(value));
			assertNull(monitor.getMetric(METRIC, NumberMetric.class), String.valueOf(value));
		}
	}

	@Test
	void nonFiniteTextsAreRefused() {
		for (final String value : new String[] { "NaN", "Infinity", "-Infinity", "1e400" }) {
			final Monitor monitor = Monitor.builder().build();
			assertNull(metricFactory.collectNumberMetric(monitor, METRIC, value, 1000L), value);
			assertNull(metricFactory.collectNumberMetric(monitor, METRIC, value, 1000L, "Gauge"), value);
			assertNull(monitor.getMetric(METRIC, NumberMetric.class), value);
		}
	}

	@Test
	void existingMetricKeepsItsValueAndTime() {
		final Monitor monitor = Monitor.builder().build();
		assertNotNull(metricFactory.collectNumberMetric(monitor, METRIC, 42.0, 1000L));

		assertNull(metricFactory.collectNumberMetric(monitor, METRIC, Double.NaN, 2000L));
		assertNull(metricFactory.collectNumberMetric(monitor, METRIC, "Infinity", 3000L));

		final NumberMetric metric = monitor.getMetric(METRIC, NumberMetric.class);
		assertEquals(42.0, metric.getValue());
		assertEquals(1000L, metric.getCollectTime());
	}

	@Test
	void finiteValueIsStored() {
		final Monitor monitor = Monitor.builder().build();
		assertEquals(1.5, metricFactory.collectNumberMetric(monitor, METRIC, "1.5", 1000L).getValue());
		assertEquals(1.5, monitor.getMetric(METRIC, NumberMetric.class).getValue());
	}

	@Test
	void nullValueBehavesAsToday() {
		final Monitor monitor = Monitor.builder().build();
		assertThrows(
			IllegalArgumentException.class,
			() -> metricFactory.collectNumberMetric(monitor, METRIC, (Double) null, 1000L)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> metricFactory.collectNumberMetric(monitor, METRIC, (String) null, 1000L)
		);
		assertNull(monitor.getMetric(METRIC, NumberMetric.class));
	}
}
