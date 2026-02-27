package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
		assertEquals("Gauge", MetricFactory.formatMetricTypeName(MetricType.GAUGE), "GAUGE should format to 'Gauge'");
	}

	@Test
	void testFormatMetricTypeNameCounter() {
		assertEquals(
			"Counter",
			MetricFactory.formatMetricTypeName(MetricType.COUNTER),
			"COUNTER should format to 'Counter'"
		);
	}

	@Test
	void testFormatMetricTypeNameUpDownCounter() {
		assertEquals(
			"UpDownCounter",
			MetricFactory.formatMetricTypeName(MetricType.UP_DOWN_COUNTER),
			"UP_DOWN_COUNTER should format to 'UpDownCounter'"
		);
	}

	@Test
	void testFormatMetricTypeNameNull() {
		assertNull(MetricFactory.formatMetricTypeName(null), "null MetricType should return null");
	}

	@Test
	void testResolveMetricTypeFromNameKnownMetric() {
		// hw.host.energy is defined as Counter in metricshub-host-metrics.yaml
		assertEquals(
			"Counter",
			MetricFactory.resolveMetricTypeFromName("hw.host.energy"),
			"hw.host.energy should resolve to Counter"
		);
	}

	@Test
	void testResolveMetricTypeFromNameGaugeMetric() {
		// hw.host.power is defined as Gauge in metricshub-host-metrics.yaml
		assertEquals(
			"Gauge",
			MetricFactory.resolveMetricTypeFromName("hw.host.power"),
			"hw.host.power should resolve to Gauge"
		);
	}

	@Test
	void testResolveMetricTypeFromNameUpDownCounterMetric() {
		// metricshub.host.configured is defined as UpDownCounter
		assertEquals(
			"UpDownCounter",
			MetricFactory.resolveMetricTypeFromName("metricshub.host.configured"),
			"metricshub.host.configured should resolve to UpDownCounter"
		);
	}

	@Test
	void testResolveMetricTypeFromNameUnknownMetric() {
		// Unknown metric should fallback to null
		assertNull(MetricFactory.resolveMetricTypeFromName("unknown.metric.name"), "Unknown metric should resolve to null");
	}

	@Test
	void testResolveMetricTypeFromNameWithAttributes() {
		// Should strip attributes before lookup
		assertEquals(
			"Gauge",
			MetricFactory.resolveMetricTypeFromName("hw.host.power{hw.type=\"enclosure\"}"),
			"Metric name with attributes should resolve after stripping attributes"
		);
	}

	@Test
	void testResolveMetricTypeNullDefinition() {
		assertNull(MetricFactory.resolveMetricType(null), "null MetricDefinition should resolve to null");
	}

	@Test
	void testResolveMetricTypeGaugeDefinition() {
		final MetricDefinition def = MetricDefinition.builder().type(MetricType.GAUGE).build();
		assertEquals("Gauge", MetricFactory.resolveMetricType(def), "Gauge MetricDefinition should resolve to 'Gauge'");
	}

	@Test
	void testResolveMetricTypeCounterDefinition() {
		final MetricDefinition def = MetricDefinition.builder().type(MetricType.COUNTER).build();
		assertEquals(
			"Counter",
			MetricFactory.resolveMetricType(def),
			"Counter MetricDefinition should resolve to 'Counter'"
		);
	}

	@Test
	void testResolveMetricTypeStateSetDefinition() {
		final StateSet stateSet = new StateSet();
		stateSet.setOutput(MetricType.UP_DOWN_COUNTER);
		final MetricDefinition def = MetricDefinition.builder().type(stateSet).build();
		assertEquals(
			"UpDownCounter",
			MetricFactory.resolveMetricType(def),
			"StateSet with UpDownCounter output should resolve to 'UpDownCounter'"
		);
	}

	@Test
	void testCollectNumberMetricNewMetricSetsMetricType() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		final NumberMetric metric = metricFactory.collectNumberMetric(monitor, "hw.host.power", 100.0, 1000L);

		assertNotNull(metric, "Collected metric should not be null");
		assertEquals(100.0, metric.getValue(), "Metric value should be 100.0");
		assertEquals("Gauge", metric.getMetricType(), "hw.host.power should be resolved as Gauge");
		assertNull(metric.getRate(), "Rate should be null for a new Gauge metric");
	}

	@Test
	void testCollectNumberMetricCounterRateComputation() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		assertNotNull(firstCollect, "First collected metric should not be null");
		assertEquals("Counter", firstCollect.getMetricType(), "hw.host.energy should be resolved as Counter");
		assertNull(firstCollect.getRate(), "Rate should be null on first collect (new metric)");

		// Save to set previous values
		firstCollect.save();

		// Second collect - 60 seconds later
		final NumberMetric secondCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 8000.0, 61000L);

		assertNotNull(secondCollect, "Second collected metric should not be null");
		assertEquals("Counter", secondCollect.getMetricType(), "hw.host.energy should remain Counter on second collect");
		assertNotNull(secondCollect.getRate(), "Rate should be computed for Counter metric");
		// rate = (8000 - 5000) / ((61000 - 1000) / 1000.0) = 3000 / 60 = 50.0
		assertEquals(50.0, secondCollect.getRate(), 0.001, "Rate should be 50.0 = 3000 / 60");
	}

	@Test
	void testCollectNumberMetricCounterFirstCollectNoPreviousValues() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect - no previous values available
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		assertNotNull(firstCollect, "First collected Counter metric should not be null");
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
	void testCollectNumberMetricCounterNullCollectTime() {
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, new ConnectorStore());
		final Monitor monitor = Monitor.builder().id("monitor1").type("host").build();

		// First collect with valid collectTime
		final NumberMetric firstCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 5000.0, 1000L);
		firstCollect.save();

		// Second collect with null collectTime
		final NumberMetric secondCollect = metricFactory.collectNumberMetric(monitor, "hw.host.energy", 8000.0, null);

		assertNull(secondCollect.getRate(), "Rate should be null when collectTime is null");
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

		assertNotNull(metric, "Collected StateSet metric should not be null");
		assertEquals("ok", metric.getValue(), "StateSet metric value should be 'ok'");
		assertEquals("UpDownCounter", metric.getMetricType(), "hw.status should be resolved as UpDownCounter");
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

		assertNotNull(metric, "Updated StateSet metric should not be null");
		assertEquals("degraded", metric.getValue(), "StateSet metric value should be updated to 'degraded'");
		assertEquals("UpDownCounter", metric.getMetricType(), "hw.status should remain UpDownCounter after update");
	}
}
