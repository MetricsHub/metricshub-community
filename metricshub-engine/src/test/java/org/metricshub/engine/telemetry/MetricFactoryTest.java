package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.connector.model.metric.StateSet;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

class MetricFactoryTest {

	private static final String HOSTNAME = "localhost";

	@Test
	void testFormatMetricTypeNameGauge() {
		assertEquals("Gauge", MetricFactory.formatMetricTypeName(MetricType.GAUGE));
	}

	@Test
	void testFormatMetricTypeNameCounter() {
		assertEquals("Counter", MetricFactory.formatMetricTypeName(MetricType.COUNTER));
	}

	@Test
	void testFormatMetricTypeNameUpDownCounter() {
		assertEquals("UpDownCounter", MetricFactory.formatMetricTypeName(MetricType.UP_DOWN_COUNTER));
	}

	@Test
	void testFormatMetricTypeNameNull() {
		assertNull(MetricFactory.formatMetricTypeName(null));
	}

	@Test
	void testResolveMetricTypeFromNameKnownMetric() {
		// hw.host.energy is defined as Counter in metricshub-host-metrics.yaml
		assertEquals("Counter", MetricFactory.resolveMetricTypeFromName("hw.host.energy"));
	}

	@Test
	void testResolveMetricTypeFromNameGaugeMetric() {
		// hw.host.power is defined as Gauge in metricshub-host-metrics.yaml
		assertEquals("Gauge", MetricFactory.resolveMetricTypeFromName("hw.host.power"));
	}

	@Test
	void testResolveMetricTypeFromNameUpDownCounterMetric() {
		// metricshub.host.configured is defined as UpDownCounter
		assertEquals("UpDownCounter", MetricFactory.resolveMetricTypeFromName("metricshub.host.configured"));
	}

	@Test
	void testResolveMetricTypeFromNameUnknownMetric() {
		// Unknown metric should fallback to null
		assertNull(MetricFactory.resolveMetricTypeFromName("unknown.metric.name"));
	}

	@Test
	void testResolveMetricTypeFromNameWithAttributes() {
		// Should strip attributes before lookup
		assertEquals("Gauge", MetricFactory.resolveMetricTypeFromName("hw.host.power{hw.type=\"enclosure\"}"));
	}

	@Test
	void testResolveMetricTypeNullDefinition() {
		assertNull(MetricFactory.resolveMetricType(null));
	}

	@Test
	void testResolveMetricTypeGaugeDefinition() {
		final MetricDefinition def = MetricDefinition.builder().type(MetricType.GAUGE).build();
		assertEquals("Gauge", MetricFactory.resolveMetricType(def));
	}

	@Test
	void testResolveMetricTypeCounterDefinition() {
		final MetricDefinition def = MetricDefinition.builder().type(MetricType.COUNTER).build();
		assertEquals("Counter", MetricFactory.resolveMetricType(def));
	}

	@Test
	void testResolveMetricTypeStateSetDefinition() {
		final StateSet stateSet = new StateSet();
		stateSet.setOutput(MetricType.UP_DOWN_COUNTER);
		final MetricDefinition def = MetricDefinition.builder().type(stateSet).build();
		assertEquals("UpDownCounter", MetricFactory.resolveMetricType(def));
	}

	@Test
	void testCollectNumberMetricNewMetricSetsMetricType() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		final NumberMetric metric = metricFactory.collectNumberMetric(monitor, "hw.host.power", 100.0, 1000L);

		assertNotNull(metric);
		assertEquals(100.0, metric.getValue());
		assertEquals("Gauge", metric.getMetricType());
		assertNull(metric.getRate());
	}

	@Test
	void testCollectNumberMetricCounterRateComputation() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		assertNotNull(firstCollect);
		assertEquals("Counter", firstCollect.getMetricType());
		assertNull(firstCollect.getRate(), "Rate should be null on first collect (new metric)");

		// Save to set previous values
		firstCollect.save();

		// Second collect - 60 seconds later
		final NumberMetric secondCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 8000.0, 61000L);

		assertNotNull(secondCollect);
		assertEquals("Counter", secondCollect.getMetricType());
		assertNotNull(secondCollect.getRate(), "Rate should be computed for Counter metric");
		// rate = (8000 - 5000) / ((61000 - 1000) / 1000.0) = 3000 / 60 = 50.0
		assertEquals(50.0, secondCollect.getRate(), 0.001);
	}

	@Test
	void testCollectNumberMetricCounterFirstCollectNoPreviousValues() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect - no previous values available
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		assertNotNull(firstCollect);
		assertNull(firstCollect.getRate(), "Rate should be null on first collect");
	}

	@Test
	void testCollectNumberMetricGaugeNeverHasRate() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.power", 100.0, 1000L);
		firstCollect.save();

		// Second collect
		final NumberMetric secondCollect = metricFactory.collectNumberMetric(monitor, "hw.host.power", 200.0, 61000L);

		assertNull(secondCollect.getRate(), "Rate should always be null for Gauge metrics");
	}

	@Test
	void testCollectNumberMetricCounterZeroTimeDelta() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		firstCollect.save();

		// Second collect at the same time (zero delta)
		final NumberMetric secondCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 8000.0, 1000L);

		assertNull(secondCollect.getRate(), "Rate should be null when time delta is zero");
	}

	@Test
	void testCollectStateSetMetricSetsMetricType() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		final StateSetMetric metric = metricFactory.collectStateSetMetric(
			monitor,
			"hw.status{hw.type=\"cpu\"}",
			"ok",
			new String[] { "ok", "degraded", "failed" },
			1000L
		);

		assertNotNull(metric);
		assertEquals("ok", metric.getValue());
		assertEquals("UpDownCounter", metric.getMetricType());
	}

	@Test
	void testCollectStateSetMetricExistingUpdatesMetricType() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect
		metricFactory.collectStateSetMetric(
			monitor,
			"hw.status{hw.type=\"cpu\"}",
			"ok",
			new String[] { "ok", "degraded", "failed" },
			1000L
		);

		// Second collect - same metric
		final StateSetMetric metric = metricFactory.collectStateSetMetric(
			monitor,
			"hw.status{hw.type=\"cpu\"}",
			"degraded",
			new String[] { "ok", "degraded", "failed" },
			2000L
		);

		assertNotNull(metric);
		assertEquals("degraded", metric.getValue());
		assertEquals("UpDownCounter", metric.getMetricType());
	}
}
