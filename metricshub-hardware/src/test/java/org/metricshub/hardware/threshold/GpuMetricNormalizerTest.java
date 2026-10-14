package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class GpuMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL =
		"hw.gpu.memory.utilization.limit{limit_type=\"critical\"}";
	private static final String GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED =
		"hw.gpu.memory.utilization.limit{limit_type=\"degraded\"}";
	private static final String GPU_UTILIZATION_LIMIT_TYPE_CRITICAL = "hw.gpu.utilization.limit{limit_type=\"critical\"}";
	private static final String GPU_UTILIZATION_LIMIT_TYPE_DEGRADED = "hw.gpu.utilization.limit{limit_type=\"degraded\"}";

	@Test
	void testNormalize() {
		final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
		setCollectTimes(hwErrorsMetric);
		final Monitor monitor = createMonitor(
			Map.of(
				"hw.gpu.utilization",
				createFullUtilizationMetric("hw.gpu.utilization"),
				"hw.gpu.memory.utilization",
				createFullUtilizationMetric("hw.gpu.memory.utilization"),
				"hw.errors",
				hwErrorsMetric
			)
		);
		new GpuMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

		assertEquals(
			1.0,
			monitor.getMetric("hw.errors.limit{limit_type=\"critical\", hw.type=\"gpu\"}", NumberMetric.class).getValue()
		);
		assertNull(monitor.getMetric("hw.errors.limit{limit_type=\"degraded\", hw.type=\"gpu\"}", NumberMetric.class));

		// A fully utilized GPU is healthy: no default utilization limits
		assertNull(monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class));
		assertNull(monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class));
		assertNull(monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class));
		assertNull(monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class));
	}

	/**
	 * Creates a utilization ratio metric at 100%.
	 *
	 * @param metricName the metric name
	 * @return a {@link NumberMetric} object with a value of 1.0 and the specified name
	 */
	private NumberMetric createFullUtilizationMetric(final String metricName) {
		final NumberMetric metric = NumberMetric.builder().value(1.0).name(metricName).build();
		setCollectTimes(metric);
		return metric;
	}

	/**
	 * Sets the previous and the current collect times of a given metric
	 * @param metric A given metric
	 */
	private void setCollectTimes(final NumberMetric metric) {
		metric.setCollectTime(STRATEGY_TIME);
		metric.setPreviousCollectTime(STRATEGY_TIME - -1000 * 60 * 2);
	}

	/**
	 * Creates a GPU monitor with the specified metrics.
	 *
	 * @param metrics a map of metric names to {@link NumberMetric} objects
	 * @return a {@link Monitor} object with the specified metrics
	 */
	private Monitor createMonitor(Map<String, NumberMetric> metrics) {
		return Monitor.builder().id("monitor").type("gpu").metrics(new HashMap<>(metrics)).build();
	}
}
