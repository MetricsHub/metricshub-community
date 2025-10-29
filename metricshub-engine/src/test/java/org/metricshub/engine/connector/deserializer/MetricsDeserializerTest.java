package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.connector.model.metric.StateSet;

class MetricsDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/metrics/";
	}

	@Test
	void testDeserializeMetrics() throws IOException {
		final Connector connector = getConnector("metrics");

		assertNotNull(connector);

		final Map<String, MetricDefinition> metrics = connector.getMetrics();

		assertTrue(metrics instanceof HashMap, "metrics are expected to be a HashMap.");

		final Map<String, MetricDefinition> expected = Map.of(
			"hw.energy",
			MetricDefinition.builder().type(MetricType.GAUGE).unit("J").description("descr").build(),
			"hw.status",
			MetricDefinition
				.builder()
				.type(StateSet.builder().set(Set.of("degraded", "failed", "ok")).build())
				.description("descr")
				.unit(EMPTY)
				.build()
		);
		assertEquals(expected, metrics);
	}
}
