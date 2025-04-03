package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class AbstractMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_ERRORS_LIMIT = "hw.errors.limit";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU =
		HW_ERRORS_LIMIT + "{limit_type=\"critical\", hw.type=\"cpu\"}";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU =
		HW_ERRORS_LIMIT + "{limit_type=\"degraded\", hw.type=\"cpu\"}";

	@Test
	void testContainsAllEntries() {
		final Map<String, String> firstMap = new HashMap<>();
		firstMap.put("key1", "value1");
		firstMap.put("key2", "value2");

		final Map<String, String> secondMap = new HashMap<>();
		secondMap.put("key1", "value1");

		assertTrue(AbstractMetricNormalizer.containsAllEntries(firstMap, secondMap));

		secondMap.put("key2", "differentValue");
		assertFalse(AbstractMetricNormalizer.containsAllEntries(firstMap, secondMap));
	}

	@Test
	void testNormalizeErrorsLimitMetric() {
		{
			// When the degraded and the critical metrics are both absent
			final Monitor monitorWithoutHwErrorsMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.cpu.speed.limit{limit_type=\"max\"}",
							NumberMetric.builder().value(1.0).name("hw.cpu.speed.limit{limit_type=\"max\"}").build()
						)
					)
				)
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithoutHwErrorsMetric);
			assertNull(
				monitorWithoutHwErrorsMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.errors{hw.type=\"cpu\"}")
				.attributes(Map.of("hw.type", "cpu"))
				.build();

			final Monitor monitorWithoutHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(new HashMap<>(Map.of("hw.errors{hw.type=\"cpu\"}", hwErrorsMetric)))
				.build();
			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithoutHwErrorsLimitMetric);
			assertNull(
				monitorWithoutHwErrorsLimitMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.errors{hw.type=\"cpu\"}")
				.attributes(Map.of("hw.type", "cpu"))
				.build();

			final Monitor diskMonitor = Monitor
				.builder()
				.id("monitorOne")
				.type("disk")
				.metrics(new HashMap<>(Map.of("hw.errors{hw.type=\"cpu\"}", hwErrorsMetric)))
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(diskMonitor);
			assertNull(diskMonitor.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class));
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			final Monitor monitorWithoutHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(new HashMap<>(Map.of("hw.errors", hwErrorsMetric)))
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithoutHwErrorsLimitMetric);
			assertNotNull(
				monitorWithoutHwErrorsLimitMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "critical", "hw.type", "cpu"))
				.build();
			final Monitor monitorWithHwErrorsMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(
					new HashMap<>(
						Map.of("hw.errors", hwErrorsMetric, HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, hwErrorsLimitMetric)
					)
				)
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithHwErrorsMetric);
			assertNotNull(
				monitorWithHwErrorsMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
			);
			assertNull(
				monitorWithHwErrorsMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU, NumberMetric.class)
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "cpu"))
				.build();
			hwErrorsLimitMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwErrorsMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(
					new HashMap<>(
						Map.of("hw.errors", hwErrorsMetric, HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU, hwErrorsLimitMetric)
					)
				)
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithHwErrorsMetric);
			assertNull(
				monitorWithHwErrorsMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
			);
			assertNotNull(
				monitorWithHwErrorsMetric.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU, NumberMetric.class)
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "critical", "hw.type", "cpu"))
				.build();
			hwErrorsLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "cpu"))
				.build();
			hwErrorsLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithHwErrorsLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU, NumberMetric.class)
					.getValue()
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "critical", "hw.type", "cpu"))
				.build();
			hwErrorsLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "cpu"))
				.build();
			hwErrorsLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("cpu")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new CpuMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalizeErrorsLimitMetric(monitorWithHwErrorsLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU, NumberMetric.class)
					.getValue()
			);
		}
	}

	// Test if replaceLimitType correctly replaces the old limit type with the new limit type in the metric name.
	@Test
	void testReplaceLimitType() {
		final String metricName = "hw.fan.speed.limit{limit_type=\"low.oldLimitType\", low=\"some_state\"}";
		final String newLimitType = "limit_type=\"high.newLimitType\"";
		final String expected = "hw.fan.speed.limit{limit_type=\"high.newLimitType\", low=\"some_state\"}";
		final String result = new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).replaceLimitType(metricName, newLimitType);
		assertEquals(expected, result);
	}
}
