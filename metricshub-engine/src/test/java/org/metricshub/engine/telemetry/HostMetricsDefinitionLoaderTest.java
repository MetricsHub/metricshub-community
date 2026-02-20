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
		assertNotNull(metrics);
		// metricshub-host-metrics.yaml defines 9 metrics
		assertTrue(metrics.size() >= 9, "Expected at least 9 host metric definitions, got " + metrics.size());
		assertTrue(metrics.containsKey("hw.host.ambient_temperature"));
		assertTrue(metrics.containsKey("hw.host.energy"));
		assertTrue(metrics.containsKey("hw.host.heating_margin"));
		assertTrue(metrics.containsKey("hw.host.power"));
		assertTrue(metrics.containsKey("metricshub.host.configured"));
		assertTrue(metrics.containsKey("metricshub.host.up"));
		assertTrue(metrics.containsKey("hw.status"));
		assertTrue(metrics.containsKey("metricshub.host.response_time"));
		assertTrue(metrics.containsKey("metricshub.job.duration"));
	}

	@Test
	void testEnergyIsCounter() {
		final MetricDefinition energyDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.host.energy");
		assertNotNull(energyDef);
		final IMetricType type = energyDef.getType();
		assertNotNull(type);
		assertEquals(MetricType.COUNTER, type.get());
	}

	@Test
	void testPowerIsGauge() {
		final MetricDefinition powerDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.host.power");
		assertNotNull(powerDef);
		final IMetricType type = powerDef.getType();
		assertNotNull(type);
		assertEquals(MetricType.GAUGE, type.get());
	}

	@Test
	void testConfiguredIsUpDownCounter() {
		final MetricDefinition configuredDef = HostMetricsDefinitionLoader
			.getInstance()
			.getMetricDefinition("metricshub.host.configured");
		assertNotNull(configuredDef);
		final IMetricType type = configuredDef.getType();
		assertNotNull(type);
		assertEquals(MetricType.UP_DOWN_COUNTER, type.get());
	}

	@Test
	void testHwStatusIsStateSet() {
		final MetricDefinition statusDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition("hw.status");
		assertNotNull(statusDef);
		final IMetricType type = statusDef.getType();
		assertTrue(type instanceof StateSet, "hw.status should be a StateSet type");
		final StateSet stateSet = (StateSet) type;
		final Set<String> states = stateSet.getSet();
		assertNotNull(states);
		assertEquals(3, states.size());
		assertTrue(states.contains("degraded"));
		assertTrue(states.contains("failed"));
		assertTrue(states.contains("ok"));
	}

	@Test
	void testUnknownMetricReturnsNull() {
		assertNull(HostMetricsDefinitionLoader.getInstance().getMetricDefinition("unknown.metric"));
	}
}
