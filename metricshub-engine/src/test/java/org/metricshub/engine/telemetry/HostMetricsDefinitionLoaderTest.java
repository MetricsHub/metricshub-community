package org.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.metric.IMetricType;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.connector.model.metric.StateSet;

class HostMetricsDefinitionLoaderTest {

	@Test
	void testSingleton() {
		final HostMetricsDefinitionLoader instance1 = HostMetricsDefinitionLoader.getInstance();
		final HostMetricsDefinitionLoader instance2 = HostMetricsDefinitionLoader.getInstance();
		assertSame(instance1, instance2, "getInstance() should always return the same instance");
	}

	@Test
	void testAllMetricsLoaded() {
		final Map<String, MetricDefinition> metrics = HostMetricsDefinitionLoader.getInstance().getHostMetricDefinitions();
		assertNotNull(metrics, "Host metric definitions map should not be null");
		// metricshub-host-metrics.yaml defines 9 metrics
		assertTrue(metrics.size() >= 9, "Expected at least 9 host metric definitions, got " + metrics.size());
		assertTrue(metrics.containsKey("hw.host.ambient_temperature"), "Should contain hw.host.ambient_temperature");
		assertTrue(metrics.containsKey("hw.host.energy"), "Should contain hw.host.energy");
		assertTrue(metrics.containsKey("hw.host.heating_margin"), "Should contain hw.host.heating_margin");
		assertTrue(metrics.containsKey("hw.host.power"), "Should contain hw.host.power");
		assertTrue(metrics.containsKey("metricshub.host.configured"), "Should contain metricshub.host.configured");
		assertTrue(metrics.containsKey("metricshub.host.up"), "Should contain metricshub.host.up");
		assertTrue(metrics.containsKey("hw.status"), "Should contain hw.status");
		assertTrue(metrics.containsKey("metricshub.host.response_time"), "Should contain metricshub.host.response_time");
		assertTrue(metrics.containsKey("metricshub.job.duration"), "Should contain metricshub.job.duration");
	}

	@Test
	void testEnergyIsCounter() {
		final MetricDefinition energyDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.host.energy");
		assertNotNull(energyDef, "hw.host.energy definition should not be null");
		final IMetricType type = energyDef.getType();
		assertNotNull(type, "hw.host.energy type should not be null");
		assertEquals(MetricType.COUNTER, type.get(), "hw.host.energy should be of type COUNTER");
	}

	@Test
	void testPowerIsGauge() {
		final MetricDefinition powerDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.host.power");
		assertNotNull(powerDef, "hw.host.power definition should not be null");
		final IMetricType type = powerDef.getType();
		assertNotNull(type, "hw.host.power type should not be null");
		assertEquals(MetricType.GAUGE, type.get(), "hw.host.power should be of type GAUGE");
	}

	@Test
	void testConfiguredIsUpDownCounter() {
		final MetricDefinition configuredDef = HostMetricsDefinitionLoader
			.getInstance()
			.getMetricDefinition("metricshub.host.configured");
		assertNotNull(configuredDef, "metricshub.host.configured definition should not be null");
		final IMetricType type = configuredDef.getType();
		assertNotNull(type, "metricshub.host.configured type should not be null");
		assertEquals(
			MetricType.UP_DOWN_COUNTER,
			type.get(),
			"metricshub.host.configured should be of type UP_DOWN_COUNTER"
		);
	}

	@Test
	void testHwStatusIsStateSet() {
		final MetricDefinition statusDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.status");
		assertNotNull(statusDef, "hw.status definition should not be null");
		final IMetricType type = statusDef.getType();
		assertTrue(type instanceof StateSet, "hw.status should be a StateSet type");
		final StateSet stateSet = (StateSet) type;
		final Set<String> states = stateSet.getSet();
		assertNotNull(states, "hw.status state set should not be null");
		assertEquals(3, states.size(), "hw.status should have exactly 3 states");
		assertTrue(states.contains("degraded"), "hw.status states should contain 'degraded'");
		assertTrue(states.contains("failed"), "hw.status states should contain 'failed'");
		assertTrue(states.contains("ok"), "hw.status states should contain 'ok'");
	}

	@Test
	void testUnknownMetricReturnsNull() {
		assertNull(
			HostMetricsDefinitionLoader.getInstance().getMetricDefinition("unknown.metric"),
			"Unknown metric should return null"
		);
	}
}
