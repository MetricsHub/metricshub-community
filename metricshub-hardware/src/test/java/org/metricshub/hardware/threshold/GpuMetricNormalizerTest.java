package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class GpuMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String GPU_MEMORY_UTILIZATION_LIMIT = "hw.gpu.memory.utilization.limit";
	private static final String GPU_UTILIZATION_LIMIT = "hw.gpu.utilization.limit";
	private static final String GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL =
		GPU_MEMORY_UTILIZATION_LIMIT + "{limit_type=\"critical\"}";
	private static final String GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED =
		GPU_MEMORY_UTILIZATION_LIMIT + "{limit_type=\"degraded\"}";
	private static final String GPU_UTILIZATION_LIMIT_TYPE_CRITICAL = GPU_UTILIZATION_LIMIT + "{limit_type=\"critical\"}";
	private static final String GPU_UTILIZATION_LIMIT_TYPE_DEGRADED = GPU_UTILIZATION_LIMIT + "{limit_type=\"degraded\"}";
	private static final String HW_GPU_UTILIZATION = "hw.gpu.utilization";
	private static final String HW_GPU_MEMORY_UTILIZATION = "hw.gpu.memory.utilization";

	@Test
	void testNormalizeGpuMemoryUtilizationLimitMetric() {
		GpuMetricNormalizer normalizer = new GpuMetricNormalizer(STRATEGY_TIME, HOSTNAME);

		// Scenario 1: Both degraded and critical metrics are present and critical < degraded
		NumberMetric criticalMetric = NumberMetric
			.builder()
			.value(1.0)
			.name(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL)
			.attributes(Map.of("limit_type", "critical"))
			.build();
		NumberMetric degradedMetric = NumberMetric
			.builder()
			.value(2.0)
			.name(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED)
			.attributes(Map.of("limit_type", "degraded", "otherAttribute", "otherAttributeValue"))
			.build();
		Monitor monitor = createMonitor(
			Map.of(
				HW_GPU_MEMORY_UTILIZATION,
				createGpuLimitMetric(HW_GPU_MEMORY_UTILIZATION),
				GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL,
				criticalMetric,
				GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED,
				degradedMetric
			)
		);
		setCollectTimes(criticalMetric);
		setCollectTimes(degradedMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_MEMORY_UTILIZATION, 0.95, 0.9);
		assertEquals(1.0, monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class).getValue());
		assertEquals(2.0, monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class).getValue());

		// Scenario 2: Both degraded and critical metrics are present and critical >= degraded
		criticalMetric =
			NumberMetric
				.builder()
				.value(2.0)
				.name(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
		degradedMetric =
			NumberMetric
				.builder()
				.value(1.0)
				.name(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED)
				.attributes(Map.of("limit_type", "degraded", "otherAttribute", "otherAttributeValue"))
				.build();
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_MEMORY_UTILIZATION,
					createGpuLimitMetric(HW_GPU_MEMORY_UTILIZATION),
					GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL,
					criticalMetric,
					GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED,
					degradedMetric
				)
			);
		setCollectTimes(degradedMetric);
		setCollectTimes(criticalMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_MEMORY_UTILIZATION, 0.95, 0.9);
		assertEquals(1.0, monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class).getValue());
		assertEquals(2.0, monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class).getValue());

		// Scenario 3: Only the degraded metric is present
		// Set a degraded metric name containing other attributes
		degradedMetric.setName(
			"hw.gpu.memory.utilization.limit{limit_type=\"degraded\", otherAttribute=\"otherAttributeValue\"}"
		);
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_MEMORY_UTILIZATION,
					createGpuLimitMetric(HW_GPU_MEMORY_UTILIZATION),
					degradedMetric.getName(),
					degradedMetric
				)
			);
		setCollectTimes(degradedMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_MEMORY_UTILIZATION, 0.95, 0.9);
		criticalMetric = monitor.getMetric(degradedMetric.getName().replace("degraded", "critical"), NumberMetric.class);
		assertNotNull(criticalMetric);
		// Check that the degraded attributes were copied in the critical attributes map
		assertTrue(criticalMetric.getAttributes().containsKey("otherAttribute"));
		assertTrue(criticalMetric.getAttributes().get("otherAttribute").equals("otherAttributeValue"));
		assertEquals(100 - ((100 - degradedMetric.getValue()) * 0.5), criticalMetric.getValue());

		// Scenario 4: Only the critical metric is present
		criticalMetric =
			NumberMetric
				.builder()
				.value(1.0)
				.name(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_MEMORY_UTILIZATION,
					createGpuLimitMetric(HW_GPU_MEMORY_UTILIZATION),
					GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL,
					criticalMetric
				)
			);
		setCollectTimes(criticalMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_MEMORY_UTILIZATION, 0.95, 0.9);
		degradedMetric = monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class);
		assertNotNull(degradedMetric);
		assertEquals(criticalMetric.getValue() * 0.9, degradedMetric.getValue());

		// Scenario 5: Neither metric is present
		monitor = createMonitor(Map.of(HW_GPU_MEMORY_UTILIZATION, createGpuLimitMetric(HW_GPU_MEMORY_UTILIZATION)));
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_MEMORY_UTILIZATION, 0.95, 0.9);
		criticalMetric = monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class);
		degradedMetric = monitor.getMetric(GPU_MEMORY_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class);
		assertNotNull(criticalMetric);
		assertEquals(0.95, criticalMetric.getValue());
		assertNotNull(degradedMetric);
		assertEquals(0.9, degradedMetric.getValue());
	}

	@Test
	void testNormalizeGpuUtilizationLimitMetric() {
		GpuMetricNormalizer normalizer = new GpuMetricNormalizer(STRATEGY_TIME, HOSTNAME);

		// Scenario 1: Both degraded and critical metrics are present and critical < degraded
		NumberMetric criticalMetric = NumberMetric
			.builder()
			.value(1.0)
			.name(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL)
			.attributes(Map.of("limit_type", "critical"))
			.build();
		NumberMetric degradedMetric = NumberMetric
			.builder()
			.value(2.0)
			.name(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED)
			.attributes(Map.of("limit_type", "degraded"))
			.build();
		Monitor monitor = createMonitor(
			Map.of(
				HW_GPU_UTILIZATION,
				createGpuLimitMetric(HW_GPU_UTILIZATION),
				GPU_UTILIZATION_LIMIT_TYPE_CRITICAL,
				criticalMetric,
				GPU_UTILIZATION_LIMIT_TYPE_DEGRADED,
				degradedMetric
			)
		);
		setCollectTimes(degradedMetric);
		setCollectTimes(criticalMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_UTILIZATION, 0.9, 0.8);
		assertEquals(1.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class).getValue());
		assertEquals(2.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class).getValue());

		// Scenario 2: Both degraded and critical metrics are present and critical >= degraded
		criticalMetric =
			NumberMetric
				.builder()
				.value(2.0)
				.name(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
		degradedMetric =
			NumberMetric
				.builder()
				.value(1.0)
				.name(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED)
				.attributes(Map.of("limit_type", "degraded"))
				.build();
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_UTILIZATION,
					createGpuLimitMetric(HW_GPU_UTILIZATION),
					GPU_UTILIZATION_LIMIT_TYPE_CRITICAL,
					criticalMetric,
					GPU_UTILIZATION_LIMIT_TYPE_DEGRADED,
					degradedMetric
				)
			);
		setCollectTimes(criticalMetric);
		setCollectTimes(degradedMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_UTILIZATION, 0.9, 0.8);
		assertEquals(1.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class).getValue());
		assertEquals(2.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class).getValue());

		// Scenario 3: Only the degraded metric is present
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_UTILIZATION,
					createGpuLimitMetric(HW_GPU_UTILIZATION),
					GPU_UTILIZATION_LIMIT_TYPE_DEGRADED,
					degradedMetric
				)
			);
		setCollectTimes(degradedMetric);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_UTILIZATION, 0.9, 0.8);
		criticalMetric = monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class);
		assertNotNull(criticalMetric);
		assertEquals(100 - ((100 - degradedMetric.getValue()) * 0.5), criticalMetric.getValue());

		// Scenario 4: Only the critical metric is present
		monitor =
			createMonitor(
				Map.of(
					HW_GPU_UTILIZATION,
					createGpuLimitMetric(HW_GPU_UTILIZATION),
					GPU_UTILIZATION_LIMIT_TYPE_CRITICAL,
					criticalMetric
				)
			);
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_UTILIZATION, 0.9, 0.8);
		degradedMetric = monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class);
		assertNotNull(degradedMetric);
		assertEquals(criticalMetric.getValue() * 0.9, degradedMetric.getValue());

		// Scenario 5: Neither metric is present
		monitor = createMonitor(Map.of(HW_GPU_UTILIZATION, createGpuLimitMetric(HW_GPU_UTILIZATION)));
		normalizer.normalizeGpuLimitMetric(monitor, HW_GPU_UTILIZATION, 0.9, 0.8);
		criticalMetric = monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class);
		degradedMetric = monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class);
		assertNotNull(criticalMetric);
		assertEquals(0.9, criticalMetric.getValue());
		assertNotNull(degradedMetric);
		assertEquals(0.8, degradedMetric.getValue());
	}

	@Test
	void testNormalize() {
		// Scenario: Both degraded and critical metrics are present and critical >= degraded for gpu.utilization prefix, hw.errors is present
		// Both degraded and critical metrics are absent for hw.errors prefix
		final NumberMetric criticalGpuUtilizationMetric = NumberMetric
			.builder()
			.value(2.0)
			.name(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL)
			.attributes(Map.of("limit_type", "critical"))
			.build();
		final NumberMetric degradedGpuUtilizationMetric = NumberMetric
			.builder()
			.value(1.0)
			.name(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED)
			.attributes(Map.of("limit_type", "degraded"))
			.build();
		final NumberMetric hwErrorsMetric = NumberMetric
			.builder()
			.value(1.0)
			.name("hw.errors")
			.collectTime(STRATEGY_TIME)
			.build();
		setCollectTimes(hwErrorsMetric);
		final Monitor monitor = createMonitor(
			Map.of(
				"hw.gpu.utilization",
				createGpuLimitMetric("hw.gpu.utilization"),
				"hw.errors",
				hwErrorsMetric,
				GPU_UTILIZATION_LIMIT_TYPE_CRITICAL,
				criticalGpuUtilizationMetric,
				GPU_UTILIZATION_LIMIT_TYPE_DEGRADED,
				degradedGpuUtilizationMetric
			)
		);
		final GpuMetricNormalizer gpuMetricNormalizer = new GpuMetricNormalizer(STRATEGY_TIME, HOSTNAME);
		setCollectTimes(criticalGpuUtilizationMetric);
		setCollectTimes(degradedGpuUtilizationMetric);
		gpuMetricNormalizer.normalize(monitor);
		assertEquals(
			1.0,
			monitor.getMetric("hw.errors.limit{limit_type=\"critical\", hw.type=\"gpu\"}", NumberMetric.class).getValue()
		);
		assertNull(monitor.getMetric("hw.errors.limit{limit_type=\"degraded\", hw.type=\"gpu\"}", NumberMetric.class));
		assertEquals(1.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_DEGRADED, NumberMetric.class).getValue());
		assertEquals(2.0, monitor.getMetric(GPU_UTILIZATION_LIMIT_TYPE_CRITICAL, NumberMetric.class).getValue());
	}

	/**
	 * Creates a GPU limit metric with the specified metric name prefix.
	 *
	 * @param metricNamePrefix the prefix for the metric name
	 * @return a {@link NumberMetric} object with a default value of 1.0 and the specified name prefix
	 */
	private NumberMetric createGpuLimitMetric(final String metricNamePrefix) {
		final NumberMetric gpuLimitMetric = NumberMetric.builder().value(1.0).name(metricNamePrefix).build();
		setCollectTimes(gpuLimitMetric);
		return gpuLimitMetric;
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
